package org.peergos.webdav;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.modeshape.common.logging.Logger;
import org.modeshape.webdav.WebdavServlet;

import java.util.Collections;

class WebDAVServer {

    private static final String VERSION= "0.1";
    private final Logger logger = Logger.getLogger(getClass());

    private Server server;
    private Args args;

    private WebDAVServer(Args args) {
        this.args = args;
    }

    public void start() throws Exception {
        int port = 8090;
        logger.info( "Starting WEBDAV server version: " + VERSION + " on port: " + port);
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.setConnectors(new Connector[] {connector});

        String webdavUser = "tobetset";
        String webdavPWD = "tobetset";
        String username = "";
        String password = "";

        //info from:
        //https://stackoverflow.com/questions/44263651/hashloginservice-and-jetty9
        //https://git.eclipse.org/c/jetty/org.eclipse.jetty.project.git/tree/examples/embedded/src/main/java/org/eclipse/jetty/embedded/SecuredHelloHandler.java
        HashLoginService loginService = new HashLoginService("MyRealm");
        UserStore userStore = new UserStore();
        userStore.addUser(webdavUser, new Password(webdavPWD), new String[] { "user"});
        loginService.setUserStore(userStore);
        server.addBean(loginService);

        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        server.setHandler(security);

        Constraint constraint = new Constraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[] { "user"});

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        security.setConstraintMappings(Collections.singletonList(mapping));
        security.setAuthenticator(new DigestAuthenticator());
        security.setLoginService(loginService);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        security.setHandler(context);

        ServletHolder holderDef = new ServletHolder("default", new WebdavServlet(username, password));
        holderDef.setInitParameter("rootpath","");
        context.addServlet(holderDef,"/*");

        server.start();
        server.join();
    }
    public static void main(String[] args) throws Exception {
        WebDAVServer webdavServer = new WebDAVServer(Args.parse(args));
        webdavServer.start();
    }
}
