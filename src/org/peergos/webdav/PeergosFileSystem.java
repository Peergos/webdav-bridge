/*
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
 *
 */
package org.peergos.webdav;

import org.modeshape.common.logging.Logger;
import org.modeshape.webdav.ITransaction;
import org.modeshape.webdav.IWebdavStore;
import org.modeshape.webdav.StoredObject;
import org.modeshape.webdav.exceptions.WebdavException;
import peergos.server.Builder;
import peergos.server.Main;
import peergos.shared.Crypto;
import peergos.shared.NetworkAccess;
import peergos.shared.user.UserContext;
import peergos.shared.user.fs.AsyncReader;
import peergos.shared.user.fs.FileWrapper;
import peergos.shared.util.Futures;
import peergos.shared.util.Serialize;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.security.Principal;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Integration with Peergos File system
 * This class was based on org.modeshape.webdav.LocalFileSystemStore
 * which in turn was based on reference Implementation of WebdavStore
 * Originally by:
 * @author joa
 * @author re
 * @author hchiorea@redhat.com
 */
public class PeergosFileSystem implements IWebdavStore {

    private static final Logger LOG = Logger.getLogger(PeergosFileSystem.class);

    private final UserContext context;

    public PeergosFileSystem(String username, String password) {
        Crypto crypto = Main.initCrypto();
        try {
            int port = 8000;
            NetworkAccess network = Builder.buildJavaNetworkAccess(new URL("http://localhost:" + port), false).join();
            context = UserContext.signIn(username, password, m -> Futures.errored(new IllegalStateException("MFA login not implemented!")),
                    network, crypto).join();
        } catch (Exception ex) {
            LOG.error(ex, "Unable to connect to Peergos account");
            throw new IllegalStateException("Unable to connect to Peergos account: ", ex);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public ITransaction begin(Principal principal ) throws WebdavException {
        LOG.trace("PeergosFileSystem.begin()");
        return null;
    }

    @Override
    public void checkAuthentication( ITransaction transaction ) throws SecurityException {
        LOG.trace("PeergosFileSystem.checkAuthentication()");
    }

    @Override
    public void commit( ITransaction transaction ) throws WebdavException {
        LOG.trace("PeergosFileSystem.commit()");
    }

    @Override
    public void rollback( ITransaction transaction ) throws WebdavException {
        LOG.trace("PeergosFileSystem.rollback()");
    }

    private Optional<FileWrapper> getByPath(Path path) {
        return context.getByPath(path.toString().replace('\\', '/')).join();
    }

    @Override
    public void createFolder( ITransaction transaction,
                              String uri ) throws WebdavException {
        LOG.trace("PeergosFileSystem.createFolder(" + uri + ")");
        Path path = new File(uri).toPath();
        Optional<FileWrapper> parentFolder = getByPath(path.getParent());
        if (parentFolder.isEmpty() || parentFolder.get().getFileProperties().isHidden) {
            throw new WebdavException("cannot find parent of folder: " + uri);
        }
        try {
            parentFolder.get().mkdir(path.getFileName().toString(), context.network, false, context.mirrorBatId(), context.crypto).join();
        } catch (Exception ex) {
            LOG.error(ex, "cannot create folder");
            throw new WebdavException("cannot create folder: " + uri);
        }
    }

    @Override
    public void createResource( ITransaction transaction,
                                String uri ) throws WebdavException {
        LOG.trace("PeergosFileSystem.createResource(" + uri + ")");
        Path path = new File(uri).toPath();
        if (path.getFileName().toString().startsWith("._") || path.getFileName().toString().equals(".DS_Store")) { // MacOS rubbish!
            return;
        }
        Optional<FileWrapper> parentFolder = getByPath(path.getParent());
        if (parentFolder.isEmpty() || parentFolder.get().getFileProperties().isHidden) {
            throw new WebdavException("cannot find parent of file: " + uri);
        }
        byte[] contents = new byte[0];
        try {
            parentFolder.get().uploadOrReplaceFile(path.getFileName().toString(), new AsyncReader.ArrayBacked(contents), contents.length, context.network, context.crypto, l -> {}).join();
        } catch (Exception e) {
            LOG.error("PeergosFileSystem.createResource(" + uri + ") failed");
            throw new WebdavException(e);
        }
    }

    @Override
    public long setResourceContent( ITransaction transaction,
                                    String uri,
                                    InputStream is,
                                    String contentType,
                                    String characterEncoding ) throws WebdavException {

        LOG.trace("PeergosFileSystem.setResourceContent(" + uri + ")");
        Path path = new File(uri).toPath();
        if (path.getFileName().toString().startsWith("._") || path.getFileName().toString().equals(".DS_Store")) { // MacOS rubbish!
            return 0;
        }
        Optional<FileWrapper> parentFolder = getByPath(path.getParent());
        if (parentFolder.isEmpty() || parentFolder.get().getFileProperties().isHidden) {
            throw new WebdavException("cannot find parent of file: " + uri);
        }
        try {
            byte[] contents = Serialize.readFully(is);
            parentFolder.get().uploadOrReplaceFile(path.getFileName().toString(), new AsyncReader.ArrayBacked(contents), contents.length, context.network, context.crypto, l -> {}).join();
            return contents.length;
        } catch (Exception e) {
            LOG.error("PeergosFileSystem.setResourceContent(" + uri + ") failed");
            throw new WebdavException(e);
        }
    }

    @Override
    public String[] getChildrenNames( ITransaction transaction,
                                      String uri ) throws WebdavException {
        LOG.trace("PeergosFileSystem.getChildrenNames(" + uri + ")");
        Path path = new File(uri).toPath();
        Optional<FileWrapper> folder = getByPath(path);
        if (folder.isEmpty() || !folder.get().isDirectory() || folder.get().getFileProperties().isHidden) {
            return new String[0];
        }
        Set<FileWrapper> children = folder.get().getChildren(context.crypto.hasher, context.network).join();
        List<String> filenames = children.stream()
                .filter(f -> !f.getFileProperties().isHidden)
                .map(f -> f.getName()).collect(Collectors.toList());
        String[] childrenNames = new String[filenames.size()];
        return filenames.toArray(childrenNames);
    }

    @Override
    public void removeObject( ITransaction transaction,
                              String uri ) throws WebdavException {
        Path path = new File(uri).toPath();
        Optional<FileWrapper> fw = getByPath(path);
        if (fw.isEmpty() || fw.get().getFileProperties().isHidden) {
            throw new WebdavException("cannot find: " + uri);
        }
        Optional<FileWrapper> parentFolder = getByPath(path.getParent());
        if (parentFolder.isEmpty()) {
            throw new WebdavException("cannot find parent folder of: " + uri);
        }
        try {
            fw.get().remove(parentFolder.get(), path, context).join();
        } catch (Exception ex) {
            throw new WebdavException("cannot delete object: " + uri);
        }
    }

    @Override
    public InputStream getResourceContent( ITransaction transaction,
                                           String uri ) throws WebdavException {
        LOG.trace("PeergosFileSystem.getResourceContent(" + uri + ")");
        Path path = new File(uri).toPath();
        Optional<FileWrapper> fw = context.getByPath(path.toString()).join();
        if (fw.isEmpty() || fw.get().isDirectory() || fw.get().getFileProperties().isHidden) {
            throw new WebdavException("cannot find file: " + uri);
        }
        try {
            byte[] data = Serialize.readFully(fw.get().getInputStream(context.network, context.crypto,
                    fw.get().getSize(), l-> {}).join(), fw.get().getSize()).join();
            return new ByteArrayInputStream(data);
        } catch (Exception e) {
            LOG.error("PeergosFileSystem.getResourceContent(" + uri + ") failed");
            throw new WebdavException(e);
        }
    }

    @Override
    public long getResourceLength( ITransaction transaction,
                                   String uri) throws WebdavException {
        Path path = new File(uri).toPath();
        Optional<FileWrapper> fw = context.getByPath(path.toString()).join();
        if (fw.isEmpty() || fw.get().isDirectory() || fw.get().getFileProperties().isHidden) {
            throw new WebdavException("cannot find file: " + uri);
        }
        return fw.get().getFileProperties().size;
    }

    @Override
    public StoredObject getStoredObject(ITransaction transaction,
                                        String uri ) {
        StoredObject so = null;
        Path path = new File(uri).toPath();
        Optional<FileWrapper> fwOpt = context.getByPath(path.toString()).join();
        if (fwOpt.isPresent() && !fwOpt.get().getFileProperties().isHidden) {
            so = new StoredObject();
            FileWrapper fw = fwOpt.get();
            so.setFolder(fw.isDirectory());
            so.setLastModified(new Date(fw.getFileProperties().modified.toEpochSecond(ZoneOffset.UTC) * 1000));
            so.setCreationDate(new Date(fw.getFileProperties().created.toEpochSecond(ZoneOffset.UTC) * 1000));
            so.setResourceLength(fw.getFileProperties().size);
        }
        return so;
    }

    @Override
    public Map<String, String> setCustomProperties( ITransaction transaction,
                                                    String resourceUri,
                                                    Map<String, Object> propertiesToSet,
                                                    List<String> propertiesToRemove ) {
        LOG.trace("PeergosFileSystem.setCustomProperties(" + resourceUri + ")");
        return null;
    }

    @Override
    public Map<String, Object> getCustomProperties( ITransaction transaction,
                                                    String resourceUri ) {
        LOG.trace("PeergosFileSystem.getCustomProperties(" + resourceUri + ")");
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getCustomNamespaces( ITransaction transaction,
                                                    String resourceUri ) {
        return Collections.emptyMap();
    }
}
