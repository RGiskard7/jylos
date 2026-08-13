package com.example.jylos.plugin.builtin.mcp;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Loads the built {@code McpServerPlugin.jar} the same way {@code PluginLoader} does —
 * a dedicated {@link URLClassLoader}, not the flat classpath the rest of
 * {@code test-plugins.sh} compiles against — and drives it over real HTTP.
 *
 * <h2>Why through the JAR, not the sources</h2>
 * <p>This plugin bundles its own copies of the MCP SDK, Jackson and Jetty (see
 * {@code plugins-source/.../mcp/lib/}). A classloading bug caught here already once:
 * the SDK resolves its default JSON mapper via {@code ServiceLoader}, which by default
 * consults the calling thread's <em>context</em> classloader — not the classloader that
 * actually loaded the SDK's own classes. Compiling this plugin's sources flat alongside
 * the test (as every other plugin here is) would put the SDK's {@code META-INF/services}
 * entry on the same classpath as the test itself, masking exactly that bug. Loading the
 * real packaged JAR through its own {@link URLClassLoader}, exactly like a real install,
 * is what actually exercises the isolation a production run has.</p>
 */
public final class McpServerPluginTest {

    private static int passed;
    private static int failed;

    /** Distinct from the plugin's own default (8843), so a real running Jylos never clashes. */
    private static final String TEST_PORT = "18845";
    private static final String BASE_URL = "http://127.0.0.1:" + TEST_PORT + "/mcp";

    public static void main(String[] args) throws Exception {
        Path jar = locateBuiltJar();
        Path vault = Files.createTempDirectory("mcp-plugin-test-vault-");
        Files.writeString(vault.resolve("Existing.md"), "# Existing\nHas a #project tag already.\n",
                StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(vault.toString());
        TagDAOFileSystem tagDAO = new TagDAOFileSystem(noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        TagService tagService = new TagService(tagDAO, noteDAO);
        EventBus eventBus = EventBus.getInstance();

        System.setProperty("jylos.mcp.port", TEST_PORT);
        HttpClient http = HttpClient.newHttpClient();

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[] { jar.toUri().toURL() }, McpServerPluginTest.class.getClassLoader())) {

            Class<?> pluginClass = classLoader.loadClass("com.example.jylos.plugin.builtin.mcp.McpServerPlugin");
            Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

            check("plugin id is stable", "mcp-server".equals(plugin.getId()), plugin.getId());
            check("host API version matches the running build",
                    "1".equals(plugin.getHostApiVersion()), plugin.getHostApiVersion());

            PluginContext context = new PluginContext(
                    plugin.getId(), noteService, folderService, tagService, eventBus,
                    null, null, null, null, null, null, null, note -> { });

            plugin.initialize(context);

            System.out.println("\n-- MCP protocol over real HTTP --");
            String sessionId = null;
            try {
                HttpResponse<String> init = post(http, """
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-06-18","capabilities":{},
                          "clientInfo":{"name":"jylos-plugin-test","version":"1.0"}
                        }}""", null);
                check("initialize handshake succeeds", init.statusCode() == 200, "status " + init.statusCode());
                sessionId = init.headers().firstValue("Mcp-Session-Id").orElse(null);
                check("server issues a session id", sessionId != null, String.valueOf(sessionId));

                post(http, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId);

                HttpResponse<String> list = post(http,
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}", sessionId);
                check("tools/list lists every registered tool",
                        containsAll(list.body(), "list_notes", "search_notes", "read_note", "create_note",
                                "update_note", "list_tags"),
                        list.body());
                check("no destructive tool is exposed",
                        !list.body().contains("delete") && !list.body().contains("trash"), list.body());

                HttpResponse<String> created = post(http, """
                        {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                          "name":"create_note","arguments":{"title":"From Test","content":"hello"}
                        }}""", sessionId);
                check("create_note reports success", created.body().contains("\"isError\":false"), created.body());
                check("the note actually exists on disk",
                        Files.exists(vault.resolve("From Test.md")), vault.toString());

                HttpResponse<String> listed = post(http,
                        "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{"
                                + "\"name\":\"list_notes\",\"arguments\":{}}}", sessionId);
                check("list_notes sees both the pre-existing and the newly created note",
                        listed.body().contains("Existing") && listed.body().contains("From Test"), listed.body());
                check("an inline #tag from the vault is reflected in tool output",
                        listed.body().contains("project"), listed.body());

                HttpResponse<String> missing = post(http,
                        "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{"
                                + "\"name\":\"read_note\",\"arguments\":{\"id\":\"does-not-exist.md\"}}}",
                        sessionId);
                check("reading a missing note is a tool error, not a protocol failure",
                        missing.statusCode() == 200 && missing.body().contains("\"isError\":true"), missing.body());
            } finally {
                plugin.shutdown();
            }

            System.out.println("\n-- shutdown --");
            check("the port is released after shutdown", !portReachable(http), "");
        }

        System.out.println("\n========================================");
        System.out.println("passed: " + passed + "   failed: " + failed);
        System.out.println("========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Path locateBuiltJar() {
        Path jar = Path.of("plugins", "McpServerPlugin.jar");
        if (!Files.exists(jar)) {
            throw new IllegalStateException(
                    "plugins/McpServerPlugin.jar not found — run scripts/build-plugins.sh first "
                            + "(test-plugins.sh does this automatically; only a hand run of this class "
                            + "needs it done manually).");
        }
        return jar;
    }

    private static HttpResponse<String> post(HttpClient http, String body, String sessionId)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean portReachable(HttpClient http) {
        try {
            post(http, "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"tools/list\",\"params\":{}}", null);
            return true;
        } catch (ConnectException e) {
            return false;
        } catch (IOException | InterruptedException e) {
            // Any other failure (e.g. connection reset mid-handshake) also confirms the
            // server is gone; only a clean response would mean it is still running.
            return false;
        }
    }

    private static boolean containsAll(String haystack, String... needles) {
        for (String needle : needles) {
            if (!haystack.contains(needle)) {
                return false;
            }
        }
        return true;
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  -> " + detail);
        }
    }
}
