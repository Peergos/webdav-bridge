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
package org.modeshape.webdav.methods;

import org.modeshape.common.logging.Logger;
import org.modeshape.webdav.IMethodExecutor;
import org.modeshape.webdav.ITransaction;
import org.modeshape.webdav.WebdavStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DoNotImplemented implements IMethodExecutor {

    private static Logger LOG = Logger.getLogger(DoNotImplemented.class);

    private final boolean readOnly;

    public DoNotImplemented( boolean readOnly ) {
        this.readOnly = readOnly;
    }

    @Override
    public void execute( ITransaction transaction,
                         HttpServletRequest req,
                         HttpServletResponse resp ) throws IOException {
        LOG.trace("-- " + req.getMethod());
        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }
}
