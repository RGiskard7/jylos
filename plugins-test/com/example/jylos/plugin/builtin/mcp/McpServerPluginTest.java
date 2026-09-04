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
 * <p>This plugin bundles its own copies of the MCP SDK, Jackson and Jetty, resolved via
 * Maven from its own {@code plugins-source/.../mcp/pom.xml} at build time (see
 * PLUGINS.md#third-party-dependencies). A classloading bug caught here already once:
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
        // A private note, to prove none of the tools hand its body out. The marker word is
        // unique in the vault, so any tool that leaks the body is caught by searching for it.
        Files.writeString(vault.resolve("Secret.md"),
                "---\nprivate: true\n---\n# Secret\nJENC1:sensitivemarkerword\n", StandardCharsets.UTF_8);

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
                    null, null, null, null, null, null, null, note -> { }, null);

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
                                "update_note", "rename_note", "move_note", "delete_note", "restore_note",
                                "list_trash", "list_folders", "create_folder", "delete_folder",
                                "list_tags", "add_tag", "remove_tag", "rename_tag"),
                        list.body());
                // Deleting is exposed, but only the recoverable kind. Anything that destroys
                // vault content outright stays out, so every destructive call an agent can
                // make is undoable from the desktop app.
                check("no irreversible tool is exposed",
                        !list.body().contains("permanently_delete") && !list.body().contains("empty_trash"),
                        list.body());
                check("the destructive tools say so in their annotations",
                        list.body().contains("\"destructiveHint\":true"), list.body());

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

                check("tools declare protocol annotations so a client knows what each one does",
                        list.body().contains("readOnlyHint"), list.body());

                System.out.println("\n-- DNS-rebinding protection (Origin validation) --");
                // The spec makes Origin validation a MUST precisely because binding to
                // loopback does not stop a browser: under DNS rebinding the page is treated
                // as same-origin with 127.0.0.1. Asking for a *write* here is deliberate —
                // the assertion that matters is not only the status code but that the vault
                // was left untouched, i.e. the request never reached the tool handler.
                HttpResponse<String> rebound = postWithOrigin(http, """
                        {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
                          "name":"create_note","arguments":{"title":"Injected","content":"pwned"}
                        }}""", sessionId, "http://evil.example");
                check("a request carrying a browser Origin is rejected",
                        rebound.statusCode() == 403, "status " + rebound.statusCode() + " " + rebound.body());
                check("the rejected request never reached the tool",
                        !Files.exists(vault.resolve("Injected.md")), vault.toString());

                // The other half of the guarantee: native clients (Claude Code, mcp-remote)
                // send no Origin header at all, and must keep working untouched.
                HttpResponse<String> nativeClient = post(http,
                        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{"
                                + "\"name\":\"list_notes\",\"arguments\":{}}}", sessionId);
                check("a client sending no Origin still works",
                        nativeClient.statusCode() == 200 && nativeClient.body().contains("From Test"),
                        "status " + nativeClient.statusCode() + " " + nativeClient.body());

                System.out.println("\n-- full note lifecycle --");
                // A note's id is its path in the vault, so renaming and moving both change it.
                // Threading the returned id from one call into the next is exactly what an
                // agent has to do, and is what would break if a tool forgot to report it.
                HttpResponse<String> renamed = call(http, sessionId, 10, "rename_note",
                        "\"id\":\"From Test.md\",\"title\":\"Renamed Note\"");
                check("rename_note renames the file on disk",
                        Files.exists(vault.resolve("Renamed Note.md"))
                                && !Files.exists(vault.resolve("From Test.md")),
                        renamed.body());
                check("rename_note reports the new id",
                        renamed.body().contains("Renamed Note.md"), renamed.body());

                HttpResponse<String> folder = call(http, sessionId, 11, "create_folder", "\"name\":\"Archive\"");
                check("create_folder creates the directory",
                        Files.isDirectory(vault.resolve("Archive")), folder.body());

                HttpResponse<String> moved = call(http, sessionId, 12, "move_note",
                        "\"id\":\"Renamed Note.md\",\"folderId\":\"Archive\"");
                check("move_note moves the file into the folder",
                        Files.exists(vault.resolve("Archive").resolve("Renamed Note.md")), moved.body());

                HttpResponse<String> tagged = call(http, sessionId, 13, "add_tag",
                        "\"id\":\"Archive/Renamed Note.md\",\"tag\":\"reviewed\"");
                check("add_tag succeeds", tagged.body().contains("\"isError\":false"), tagged.body());
                check("add_tag is reflected in the note's tags",
                        tagged.body().contains("reviewed"), tagged.body());

                HttpResponse<String> untagged = call(http, sessionId, 14, "remove_tag",
                        "\"id\":\"Archive/Renamed Note.md\",\"tag\":\"reviewed\"");
                check("remove_tag succeeds", untagged.body().contains("\"isError\":false"), untagged.body());

                HttpResponse<String> deleted = call(http, sessionId, 15, "delete_note",
                        "\"id\":\"Archive/Renamed Note.md\"");
                check("delete_note reports success", deleted.body().contains("\"isError\":false"), deleted.body());
                check("delete_note takes the file out of the vault",
                        !Files.exists(vault.resolve("Archive").resolve("Renamed Note.md")), deleted.body());

                HttpResponse<String> trash = call(http, sessionId, 16, "list_trash", "");
                check("the deleted note is in the trash, not gone",
                        trash.body().contains("Renamed Note"), trash.body());

                // A trashed note's id is its path under .trash, not the one it had before
                // being deleted — which is why restore_note documents list_trash as the
                // source of the id, and why passing a stale one must be an error.
                HttpResponse<String> staleRestore = call(http, sessionId, 17, "restore_note",
                        "\"id\":\"Archive/Renamed Note.md\"");
                check("restoring with a pre-deletion id is refused, not silently ignored",
                        staleRestore.body().contains("\"isError\":true"), staleRestore.body());

                // Taken from the listing rather than hardcoded: this is exactly the flow a
                // client follows, and it keeps the test honest about where the id comes from.
                String trashedId = firstTrashId(trash.body());
                check("list_trash reports an id under .trash to restore with",
                        trashedId != null && trashedId.startsWith(".trash/"), String.valueOf(trashedId));

                HttpResponse<String> restored = call(http, sessionId, 18, "restore_note",
                        "\"id\":\"" + trashedId + "\"");
                check("restore_note reports success", restored.body().contains("\"isError\":false"),
                        restored.body());
                check("the restored note is back on disk",
                        Files.exists(vault.resolve("Archive").resolve("Renamed Note.md")), restored.body());

                HttpResponse<String> renamedTag = call(http, sessionId, 19, "rename_tag",
                        "\"tag\":\"project\",\"newName\":\"work\"");
                check("rename_tag succeeds", renamedTag.body().contains("\"isError\":false"), renamedTag.body());

                HttpResponse<String> deletedFolder = call(http, sessionId, 20, "delete_folder",
                        "\"id\":\"Archive\"");
                check("delete_folder reports success",
                        deletedFolder.body().contains("\"isError\":false"), deletedFolder.body());

                System.out.println("\n-- private notes stay private --");
                // The vault is a different trust boundary from an MCP client: a note the
                // user encrypted must not come back through any tool, whatever the route.
                HttpResponse<String> readPrivate = call(http, sessionId, 23, "read_note",
                        "\"id\":\"Secret.md\"");
                check("read_note refuses a private note",
                        readPrivate.body().contains("\"isError\":true"), readPrivate.body());
                HttpResponse<String> searchPrivate = call(http, sessionId, 24, "search_notes",
                        "\"query\":\"sensitivemarkerword\"");
                check("search_notes never returns a private note's body",
                        !searchPrivate.body().contains("sensitivemarkerword\\n")
                                && !searchPrivate.body().contains("JENC1"),
                        searchPrivate.body());
                HttpResponse<String> editPrivate = call(http, sessionId, 25, "update_note",
                        "\"id\":\"Secret.md\",\"content\":\"overwritten\"");
                check("update_note refuses a private note",
                        editPrivate.body().contains("\"isError\":true"), editPrivate.body());
                HttpResponse<String> deletePrivate = call(http, sessionId, 26, "delete_note",
                        "\"id\":\"Secret.md\"");
                check("delete_note refuses a private note",
                        deletePrivate.body().contains("\"isError\":true"), deletePrivate.body());
                check("the private note is untouched on disk",
                        Files.readString(vault.resolve("Secret.md"), StandardCharsets.UTF_8)
                                .contains("sensitivemarkerword"), vault.toString());

                System.out.println("\n-- error paths --");
                HttpResponse<String> ghost = call(http, sessionId, 21, "delete_note", "\"id\":\"nope.md\"");
                check("deleting a note that does not exist is a tool error",
                        ghost.statusCode() == 200 && ghost.body().contains("\"isError\":true"), ghost.body());
                HttpResponse<String> badFolder = call(http, sessionId, 22, "move_note",
                        "\"id\":\"Existing.md\",\"folderId\":\"no-such-folder\"");
                check("moving into a folder that does not exist is a tool error",
                        badFolder.body().contains("\"isError\":true"), badFolder.body());
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
        return postWithOrigin(http, body, sessionId, null);
    }

    /**
     * Calls one tool. Keeps the lifecycle checks readable: only the tool name and its
     * arguments differ between them, so the JSON-RPC envelope is built here.
     *
     * @param arguments the tool's arguments as raw JSON object fields, {@code ""} for none
     */
    private static HttpResponse<String> call(HttpClient http, String sessionId, int id, String tool,
            String arguments) throws IOException, InterruptedException {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"" + tool + "\",\"arguments\":{" + arguments + "}}}";
        return post(http, body, sessionId);
    }

    /** Same as {@link #post}, plus an {@code Origin} header — what a browser would send. */
    private static HttpResponse<String> postWithOrigin(HttpClient http, String body, String sessionId, String origin)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        if (origin != null) {
            builder.header("Origin", origin);
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

    /**
     * Pulls the first {@code .trash/...} id out of a list_trash response. Deliberately a
     * plain string scan: this test has no JSON library on its classpath, and every other
     * assertion here reads the raw body the same way.
     */
    private static String firstTrashId(String body) {
        String marker = "\"id\":\".trash/";
        int start = body.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + "\"id\":\"".length();
        int end = body.indexOf('"', valueStart);
        return end > valueStart ? body.substring(valueStart, end) : null;
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
