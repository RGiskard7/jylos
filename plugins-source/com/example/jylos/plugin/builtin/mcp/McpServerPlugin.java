package com.example.jylos.plugin.builtin.mcp;

import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;

/**
 * Exposes the vault to MCP clients (Claude Desktop, Claude Code, and other
 * Streamable-HTTP-capable clients) over a local HTTP server while Jylos is running.
 *
 * <p>Ships as a plugin rather than a core feature for the same reason Dataview and
 * Mermaid do: everything it needs — {@link com.example.jylos.service.NoteService},
 * {@link com.example.jylos.service.FolderService}, {@link com.example.jylos.service.TagService}
 * — is already reachable through {@link PluginContext}, with nothing here that requires a
 * new host extension point. See {@code docs/MCP.md} for the exposed tools, the transport
 * choice, and what is deliberately left out of this first version.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
public class McpServerPlugin implements Plugin {

    private PluginContext context;
    private JylosMcpServer server;

    private static java.util.ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(McpServerPlugin.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

    @Override
    public String getId() {
        return "mcp-server";
    }

    @Override
    public String getName() {
        return "MCP Server";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return tr("plugin.description", "Exposes the vault to MCP clients (Claude Desktop, Claude Code, …) over a "
                + "local HTTP server.");
    }

    @Override
    public String getAuthor() {
        return "Edu Díaz";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        this.server = new JylosMcpServer(context.getNoteService(), context.getFolderService(),
                context.getTagService());

        boolean started = server.start();
        if (started) {
            context.log("MCP server listening at " + server.endpointUrl());
        } else {
            context.log("MCP server could not start — see the log for the cause "
                    + "(a busy port is the usual reason; override it with -Djylos.mcp.port=<port>).");
        }

        context.registerMenuItem("MCP Server", tr("menu.connectionInfo", "Connection info"), this::showConnectionInfo);

        context.registerCommand("MCP Server: Connection info",
                tr("command.connectionInfo.description", "Shows the local URL MCP clients should connect to"),
                this::showConnectionInfo);

        context.registerCommand("MCP Server: Restart",
                tr("command.restart.description", "Stops and restarts the embedded MCP server"),
                () -> {
                    server.stop();
                    boolean restarted = server.start();
                    context.showInfo(tr("title", "MCP Server"),
                            restarted ? tr("restart.ok.header", "Restarted") : tr("restart.fail.header", "Failed to restart"),
                            restarted ? tr("restart.ok.body", "Listening at %s").formatted(server.endpointUrl())
                                    : tr("restart.fail.body", "Could not bind the server. Check the log for details."));
                });

        context.log("MCP Server plugin initialized");
    }

    @Override
    public void shutdown() {
        if (server != null) {
            server.stop();
        }
        if (context != null) {
            context.unregisterAllCommands();
        }
    }

    private void showConnectionInfo() {
        if (server != null && server.isRunning()) {
            context.showInfo(tr("title", "MCP Server"), tr("running.header", "Running"),
                    tr("running.body", "URL: %s\n\n"
                            + "Add this as a Streamable HTTP / remote MCP server in your client "
                            + "(Claude Desktop, Claude Code, …). The vault is exposed read-mostly: "
                            + "notes, search and tags, plus creating notes and replacing a note's "
                            + "content. Deleting or trashing notes is not exposed.")
                            .formatted(server.endpointUrl()));
        } else {
            context.showInfo(tr("title", "MCP Server"), tr("notRunning.header", "Not running"),
                    tr("notRunning.body", "The server failed to start. Check the log — a port already in use is the "
                            + "usual cause. Override it with -Djylos.mcp.port=<port> and restart Jylos, "
                            + "or use \"MCP Server: Restart\" from the command palette after freeing "
                            + "the port."));
        }
    }
}
