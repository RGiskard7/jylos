package com.example.jylos.plugin.builtin.mcp;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;

/**
 * Embeds a local MCP server exposing the vault to MCP clients (Claude Desktop, Claude
 * Code, and any other Streamable-HTTP-capable client) while Jylos is running.
 *
 * <h2>Transport</h2>
 * <p>The MCP Java SDK's server-side transports are STDIO or Servlet-based; STDIO assumes
 * the client spawns the server as a subprocess, which does not fit a plugin already
 * running inside a live desktop app. This hosts the SDK's own conformance-tested
 * {@link HttpServletStreamableServerTransportProvider} — a real {@code HttpServlet} — on
 * a minimal embedded Jetty (server + one servlet context, not a full distribution),
 * rather than writing HTTP/SSE session handling by hand.</p>
 *
 * <h2>Binding</h2>
 * <p>Loopback only. This is a local integration point for tools running on the same
 * machine, not a service meant to be reachable from the network — {@link ServerConnector}
 * is bound to the loopback address explicitly, never the wildcard address.</p>
 */
final class JylosMcpServer {

    private static final Logger logger = Logger.getLogger(JylosMcpServer.class.getName());

    /** Overridable the same way other host paths are, e.g. {@code jylos.data.dir}. */
    private static final String PORT_PROPERTY = "jylos.mcp.port";
    private static final int DEFAULT_PORT = 8843;
    private static final String MCP_ENDPOINT = "/mcp";

    private final NoteService noteService;
    private final FolderService folderService;
    private final TagService tagService;

    private Server jettyServer;
    private McpSyncServer mcpServer;
    private int boundPort = -1;

    JylosMcpServer(NoteService noteService, FolderService folderService, TagService tagService) {
        this.noteService = noteService;
        this.folderService = folderService;
        this.tagService = tagService;
    }

    /**
     * Starts the embedded server. Never throws: a busy port or any other startup failure
     * is logged and leaves the server not running, exactly like a failed plugin must not
     * be allowed to stop the rest of the app from loading.
     *
     * @return {@code true} if the server is now listening
     */
    boolean start() {
        if (jettyServer != null) {
            return true;
        }
        int port = resolvePort();
        // The SDK looks up its default McpJsonMapper via ServiceLoader, which by default
        // resolves against the calling thread's context classloader — not the classloader
        // that actually loaded the SDK's classes. The thread running plugin initialization
        // never has this plugin's own URLClassLoader as its context classloader, so without
        // this the lookup finds nothing and throws ServiceConfigurationError, even though
        // the META-INF/services entry is right there in this same JAR. Restored in
        // `finally` — this must not leak into the thread's classloader for anything after.
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(JylosMcpServer.class.getClassLoader());
        try {
            McpStreamableServerTransportProvider transportProvider = HttpServletStreamableServerTransportProvider
                    .builder()
                    .mcpEndpoint(MCP_ENDPOINT)
                    .build();

            mcpServer = McpServer.sync(transportProvider)
                    .serverInfo("jylos", pluginVersion())
                    .instructions("Local Jylos vault: notes, full-text search and tags. "
                            + "Destructive operations (delete, trash) are intentionally not exposed.")
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                    .tools(NoteTools.build(noteService, folderService, tagService))
                    .build();

            Server server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setHost(java.net.InetAddress.getLoopbackAddress().getHostAddress());
            connector.setPort(port);
            server.addConnector(connector);

            ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            ServletHolder holder = new ServletHolder((jakarta.servlet.http.HttpServlet) transportProvider);
            // The Streamable HTTP transport holds a request open for server-to-client
            // notifications; without async support Jetty rejects that with an
            // IllegalStateException the first time a client actually connects.
            holder.setAsyncSupported(true);
            context.addServlet(holder, "/*");
            server.setHandler(context);

            // Assigned before start(), not after: a bind failure must still leave stop()
            // able to find and release whatever the connector partially opened, instead
            // of leaking it because the field was never set.
            jettyServer = server;
            server.start();
            boundPort = connector.getLocalPort();
            logger.info("MCP server listening on http://127.0.0.1:" + boundPort + MCP_ENDPOINT);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not start the MCP server on port " + port
                    + " (set -D" + PORT_PROPERTY + "=<port> to use a different one)", e);
            stop();
            return false;
        }
    }

    /** Stops both the MCP server and the embedded Jetty instance. Safe to call twice. */
    void stop() {
        if (mcpServer != null) {
            try {
                mcpServer.close();
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "Error closing the MCP server", e);
            }
            mcpServer = null;
        }
        if (jettyServer != null) {
            try {
                jettyServer.stop();
            } catch (Exception e) {
                logger.log(Level.FINE, "Error stopping the embedded MCP HTTP server", e);
            }
            jettyServer = null;
        }
        boundPort = -1;
    }

    boolean isRunning() {
        return jettyServer != null;
    }

    /** The URL clients should be configured with, or {@code null} if not running. */
    String endpointUrl() {
        return isRunning() ? "http://127.0.0.1:" + boundPort + MCP_ENDPOINT : null;
    }

    private static int resolvePort() {
        String configured = System.getProperty(PORT_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            try {
                return Integer.parseInt(configured.trim());
            } catch (NumberFormatException ignored) {
                logger.warning("Invalid -D" + PORT_PROPERTY + "='" + configured + "', using default " + DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }

    private static String pluginVersion() {
        return "1.0.0";
    }
}
