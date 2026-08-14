package com.example.jylos.plugin.builtin.mcp;

import java.util.ArrayList;
import java.util.List;
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
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
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
                    .securityValidator(localhostSecurityValidator(port))
                    .build();

            mcpServer = McpServer.sync(transportProvider)
                    .serverInfo("jylos", pluginVersion())
                    .instructions("Local Jylos vault: notes, folders, tags and full-text search. "
                            + "Deleting a note or a folder moves it to the trash and is undoable "
                            + "(list_trash, restore_note); nothing here removes content permanently. "
                            + "Private (encrypted) notes are never readable or editable through these tools.")
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                    .tools(allTools())
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

    /** Every tool this server exposes, grouped by the part of the vault it works on. */
    private List<SyncToolSpecification> allTools() {
        List<SyncToolSpecification> tools = new ArrayList<>();
        tools.addAll(NoteTools.build(noteService, folderService));
        tools.addAll(FolderTools.build(folderService));
        tools.addAll(TagTools.build(noteService, tagService));
        return tools;
    }

    /**
     * Rejects requests that a browser could originate, which loopback binding alone does
     * not stop. The MCP Streamable HTTP spec makes this a MUST: without it, a page the
     * user is merely visiting can reach this server through DNS rebinding — the browser
     * then treats {@code 127.0.0.1} as same-origin with the attacker's site, so there is
     * no preflight and the response is readable.
     *
     * <p>The SDK's own validator is used rather than a hand-written check. Its semantics
     * are exactly what a local server wants:</p>
     * <ul>
     *   <li><b>Origin</b>: a request without the header passes untouched (native clients
     *       like Claude Code and {@code mcp-remote} send none), while any request that
     *       <em>does</em> carry one is rejected with 403, because the allowed-origins list
     *       is left empty. A browser always sends {@code Origin} cross-origin, so this is
     *       precisely the boundary we want — leaving the list empty is the configuration,
     *       not an omission.</li>
     *   <li><b>Host</b>: pinned to the loopback names this server is actually reachable
     *       at, so a rebound {@code Host: attacker.example} is rejected with 421.</li>
     * </ul>
     */
    private static DefaultServerTransportSecurityValidator localhostSecurityValidator(int port) {
        return DefaultServerTransportSecurityValidator.builder()
                .allowedHost("127.0.0.1:" + port)
                .allowedHost("localhost:" + port)
                .allowedHost("[::1]:" + port)
                .build();
    }

    /**
     * Resolves the listening port. Port 0 ("pick any free port") is deliberately refused:
     * the Host allow-list above has to be built before the connector binds, so an
     * unpredictable port would produce a server that rejects every request to itself.
     */
    private static int resolvePort() {
        String configured = System.getProperty(PORT_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            try {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed <= 0) {
                    logger.warning("-D" + PORT_PROPERTY + "=" + parsed
                            + " is not supported (the port must be known before binding so the Host"
                            + " allow-list can be built); using default " + DEFAULT_PORT);
                    return DEFAULT_PORT;
                }
                return parsed;
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
