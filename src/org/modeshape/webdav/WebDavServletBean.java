/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modeshape.webdav;

import org.modeshape.common.logging.Logger;
import org.modeshape.webdav.exceptions.*;
import org.modeshape.webdav.fromcatalina.RequestUtil;
import org.modeshape.webdav.locking.ResourceLocks;
import org.modeshape.webdav.methods.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class WebDavServletBean extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static Logger LOG = Logger.getLogger(WebDavServletBean.class);

    /**
     * MD5 message digest provider.
     */
    protected static MessageDigest MD5_HELPER;

    private static final boolean READ_ONLY = false;
    protected final ResourceLocks resLocks;
    protected IWebdavStore store;
    private Map<String, IMethodExecutor> methodMap = new HashMap<String, IMethodExecutor>();

    public WebDavServletBean() {
        resLocks = new ResourceLocks();

        try {
            MD5_HELPER = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings( "unused" )
    public void init( IWebdavStore store,
                      String dftIndexFile,
                      String insteadOf404,
                      int nocontentLenghHeaders,
                      boolean lazyFolderCreationOnPut ) throws ServletException {

        this.store = store;

        IMimeTyper mimeTyper = new IMimeTyper() {
            @Override
            public String getMimeType( ITransaction transaction,
                                       String path ) {
                String retVal = WebDavServletBean.this.store.getStoredObject(transaction, path).getMimeType();
                if (retVal == null) {
                    retVal = getServletContext().getMimeType(path);
                }
                return retVal;
            }
        };

        register("GET", new DoGet(store, dftIndexFile, insteadOf404, resLocks, mimeTyper, nocontentLenghHeaders));
        register("HEAD", new DoHead(store, dftIndexFile, insteadOf404, resLocks, mimeTyper, nocontentLenghHeaders));
        DoDelete doDelete = (DoDelete)register("DELETE", new DoDelete(store, resLocks, READ_ONLY));
        DoCopy doCopy = (DoCopy)register("COPY", new DoCopy(store, resLocks, doDelete, READ_ONLY));
        register("LOCK", new DoLock(store, resLocks, READ_ONLY));
        register("UNLOCK", new DoUnlock(store, resLocks, READ_ONLY));
        register("MOVE", new DoMove(resLocks, doDelete, doCopy, READ_ONLY));
        register("MKCOL", new DoMkcol(store, resLocks, READ_ONLY));
        register("OPTIONS", new DoOptions(store, resLocks));
        register("PUT", new DoPut(store, resLocks, READ_ONLY, lazyFolderCreationOnPut));
        register("PROPFIND", new DoPropfind(store, resLocks, mimeTyper));
        register("PROPPATCH", new DoProppatch(store, resLocks, READ_ONLY));
        register("*NO*IMPL*", new DoNotImplemented(READ_ONLY));
    }

    @Override
    public void destroy() {
        if (store != null) {
            store.destroy();
        }
        super.destroy();
    }

    protected IMethodExecutor register( String methodName,
                                        IMethodExecutor method ) {
        methodMap.put(methodName, method);
        return method;
    }

    /**
     * Handles the special WebDAV methods.
     */
    @Override
    protected void service( HttpServletRequest req,
                            HttpServletResponse resp ) throws ServletException, IOException {

        String methodName = req.getMethod();
        ITransaction transaction = null;
        boolean needRollback = false;

        if (LOG.isTraceEnabled()) {
            debugRequest(methodName, req);
        }

        try {
            Principal userPrincipal = req.getUserPrincipal();
            transaction = store.begin(userPrincipal);
            needRollback = true;
            store.checkAuthentication(transaction);
            resp.setStatus(WebdavStatus.SC_OK);

            try {
                IMethodExecutor methodExecutor = methodMap.get(methodName);
                if (methodExecutor == null) {
                    methodExecutor = methodMap.get("*NO*IMPL*");
                }

                methodExecutor.execute(transaction, req, resp);

                store.commit(transaction);
                /**
                 * Clear not consumed data Clear input stream if available otherwise later access include current input. These
                 * cases occure if the client sends a request with body to an not existing resource.
                 */
                if (RequestUtil.streamNotConsumed(req)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Clear not consumed data!");
                    }
                    try {
                        while (req.getInputStream().available() > 0) {
                            req.getInputStream().read();
                        }
                    } catch (IOException e) {
                        //ignore
                    }
                }
                needRollback = false;
            } catch (IOException e) {
                LOG.error(e, "IOException");
                resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                store.rollback(transaction);
                throw new ServletException(e);
            }

        } catch (UnauthenticatedException e) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
        } catch (AccessDeniedException e) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
        } catch (LockFailedException e) {
            resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
        } catch (ObjectAlreadyExistsException e) {
            resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
        } catch (ObjectNotFoundException e) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
        } catch (WebdavException e) {
            throw new ServletException(e);
        } catch (Throwable t) {
            t = translate(t);
            if (t instanceof UnauthenticatedException) {
                resp.sendError(WebdavStatus.SC_FORBIDDEN);
            } else if (t instanceof AccessDeniedException) {
                resp.sendError(WebdavStatus.SC_FORBIDDEN);
            } else if (t instanceof LockFailedException) {
                resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            } else if (t instanceof ObjectAlreadyExistsException) {
                resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
            } else if (t instanceof ObjectNotFoundException) {
                resp.sendError(WebdavStatus.SC_NOT_FOUND);
            } else if (t instanceof WebdavException) {
                throw new ServletException(t);
            } else {
                throw new ServletException(t);
            }
        } finally {
            if (needRollback) {
                store.rollback(transaction);
            }
        }
    }

    protected Throwable translate( Throwable t ) {
        return t;
    }

    private void debugRequest( String methodName,
                               HttpServletRequest req ) {
        LOG.trace("-----------");
        LOG.trace("WebdavServlet\n request: methodName = " + methodName);
        LOG.trace("time: " + System.currentTimeMillis());
        LOG.trace("path: " + req.getRequestURI());
        LOG.trace("-----------");
        Enumeration<?> e = req.getHeaderNames();
        while (e.hasMoreElements()) {
            String s = (String)e.nextElement();
            LOG.trace("header: " + s + " " + req.getHeader(s));
        }
        e = req.getAttributeNames();
        while (e.hasMoreElements()) {
            String s = (String)e.nextElement();
            LOG.trace("attribute: " + s + " " + req.getAttribute(s));
        }
        e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String s = (String)e.nextElement();
            LOG.trace("parameter: " + s + " " + req.getParameter(s));
        }
    }

}
