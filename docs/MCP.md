# MCP server plugin

Español: [es/MCP.md](es/MCP.md)

Exposes the vault to [MCP](https://modelcontextprotocol.io) clients — Claude Desktop,
Claude Code, or any other Streamable-HTTP-capable client — over a local HTTP server,
while Jylos is running.

The plugin ships in `plugins-source/com/example/jylos/plugin/builtin/mcp/` and is built
by `scripts/build-plugins.sh` into `jylos/plugins/McpServerPlugin.jar`.

## Connecting a client

The server listens at `http://127.0.0.1:8843/mcp` by default (**Tools → Plugins → MCP
Server → Connection info** shows the live URL and current status). Jylos does not need
to be told about the client; any Streamable-HTTP-conformant client can connect.

Override the port with `-Djylos.mcp.port=<port>` if 8843 is taken, then use **MCP
Server: Restart** from the command palette (or restart Jylos).

### Claude Code

Claude Code speaks Streamable HTTP natively — point it straight at the URL:

```bash
claude mcp add --transport http jylos http://127.0.0.1:8843/mcp
```

### Claude Desktop

Claude Desktop's `claude_desktop_config.json` only spawns **stdio** servers directly; it
has no native "url" entry for an already-running HTTP server. Bridge it with
[`mcp-remote`](https://github.com/geelen/mcp-remote), passing `--allow-http` since this
server is plain loopback HTTP, not HTTPS:

```json
{
  "mcpServers": {
    "jylos": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://127.0.0.1:8843/mcp",
        "--allow-http"
      ]
    }
  }
}
```

macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`. Windows:
`%APPDATA%\Claude\claude_desktop_config.json`. Restart Claude Desktop after editing.

### opencode

`opencode.json`, `"remote"` type, straight to the URL — no bridge:

```json
{
  "mcp": {
    "jylos": {
      "type": "remote",
      "url": "http://127.0.0.1:8843/mcp",
      "enabled": true
    }
  }
}
```

### Codex CLI

`config.toml`, `url` field under `[mcp_servers.jylos]` — no bridge:

```toml
[mcp_servers.jylos]
url = "http://127.0.0.1:8843/mcp"
```

### Cursor

`.cursor/mcp.json`, `url` field — no bridge:

```json
{
  "mcpServers": {
    "jylos": {
      "url": "http://127.0.0.1:8843/mcp"
    }
  }
}
```

### VS Code

`.vscode/mcp.json`, top-level key is `servers` (not `mcpServers`), `"type": "http"` — no
bridge:

```json
{
  "servers": {
    "jylos": {
      "type": "http",
      "url": "http://127.0.0.1:8843/mcp"
    }
  }
}
```

### Other Streamable-HTTP clients

Any client with native Streamable HTTP support (not stdio-only) can be pointed directly
at the URL, the same way the clients above are — check that client's own docs for its
exact config field name. There is no standard client-side config format across MCP
clients; only the wire protocol is standardized, so each vendor invented its own file and
field names. A stdio-only client needs the `mcp-remote` bridge shown for Claude Desktop.

## Why a plugin, and why HTTP

Everything the server needs — `NoteService`, `FolderService`, `TagService` — is already
reachable through `PluginContext`, exactly like Dataview and Mermaid. No core change was
needed to build this.

The MCP Java SDK's server transports are STDIO or Servlet; STDIO assumes the *client*
spawns the server as a subprocess, which does not fit a plugin already running inside a
live desktop app. This hosts the SDK's own conformance-tested
`HttpServletStreamableServerTransportProvider` — a real `HttpServlet` — on a minimal
embedded Jetty (one server, one servlet context; not a full distribution), rather than
writing HTTP/SSE session handling by hand. See `plugins-source/.../mcp/pom.xml` and
[PLUGINS.md](PLUGINS.md#third-party-dependencies) for how a plugin bundles a real SDK
instead of reimplementing an external, evolving protocol from scratch.

## Binding and request validation

Loopback only (`127.0.0.1`), never the wildcard address. This is a local integration
point for tools running on the same machine, not a service meant to be reachable from
the network.

Loopback binding alone is **not** enough, and the spec says so — it makes `Origin`
validation a MUST. A web page the user is merely visiting can reach a local server
through DNS rebinding: the browser then treats `127.0.0.1` as same-origin with the
attacker's site, so there is no preflight and the response is readable. The server
therefore also:

- **Rejects any request carrying an `Origin` header** with `403`. A browser always sends
  one on a cross-origin request; native clients (Claude Desktop, Claude Code,
  `mcp-remote`) send none, so they are unaffected.
- **Pins the `Host` header** to the loopback names it is actually reachable at, so a
  rebound `Host: attacker.example` gets `421`.

Both are handled by the SDK's own `DefaultServerTransportSecurityValidator` rather than a
hand-written check. One consequence: `-Djylos.mcp.port=0` ("any free port") is refused and
falls back to the default, because the allowed-`Host` list has to be built before the
connector binds.

## Tools

Every tool carries the protocol's `readOnlyHint` / `destructiveHint` / `idempotentHint`
annotations, so a client can tell what a tool does — and ask for confirmation — before
calling it.

**Notes**

| Tool | Does |
|------|------|
| `list_notes` | Lists notes, most recently modified first (id, title, tags, favorite/pinned, dates) |
| `search_notes` | Full-text search over titles and content |
| `read_note` | Full content and metadata by id or exact title |
| `create_note` | Creates a note at the vault root |
| `update_note` | Replaces a note's content (title and metadata untouched) |
| `rename_note` | Renames a note — **returns a new id** |
| `move_note` | Moves a note into a folder, or to the root — **returns a new id** |
| `delete_note` | Moves a note to the trash (recoverable) |
| `restore_note` | Restores a note from the trash, by its `list_trash` id |
| `list_trash` | Lists trashed notes, each restorable |

**Folders**

| Tool | Does |
|------|------|
| `list_folders` | Every folder with its id, name and note count |
| `create_folder` | Creates a folder, at the root or inside another |
| `delete_folder` | Deletes a folder; its notes move to the root rather than being deleted |

**Tags**

| Tool | Does |
|------|------|
| `list_tags` | Every tag with its note count, most used first |
| `add_tag` | Adds a tag to a note, creating the tag if new |
| `remove_tag` | Removes a tag from one note |
| `rename_tag` | Renames a tag across the whole vault |

A note's id is its path inside the vault, so `rename_note` and `move_note` change it.
Both return the new id — use it for any further call, or the next one will not resolve.

A private note's content is never returned or editable over MCP, even if it happens to
be unlocked in the desktop app's current session. That covers every route: reading,
searching, editing, renaming, moving, tagging and deleting all refuse it.

### Deliberately not exposed

**Nothing irreversible.** Deleting is available, but only the recoverable kind: a deleted
note goes to the trash and comes back with `restore_note`, and a deleted folder keeps its
notes by moving them to the root. `permanently_delete_note`, `empty_trash` and
`permanently_delete_folder` have no tools. An MCP client is a different trust boundary
from the desktop UI's confirmation dialogs, so every destructive call an agent can make
here is one the user can undo from the app.

**No folder rename or move, no tag delete.** Each reshapes the vault around notes the
agent was not asked about. A note can already be relocated one at a time with
`move_note`, and a tag taken off one note with `remove_tag`.

**No Dataview query tool.** Plugins cannot call into one another — each only receives the
`PluginContext` the host gives it, with no registry of other plugins' capabilities.
Exposing "run this Dataview query" over MCP would need a host-level extension point that
does not exist today.

## Dependencies

`mcp` (SDK core + Jackson 3), `jetty-server` + `jetty-ee11-servlet` (embedded HTTP),
`jakarta.servlet-api`. Resolved with Maven against the exact versions the SDK declares —
not hand-picked — and packed into the plugin JAR (see
[PLUGINS.md](PLUGINS.md#third-party-dependencies) for what that packing step handles:
signature-file stripping, `META-INF/services` merging, `module-info.class` removal).
`slf4j-api` is deliberately **not** bundled — the core app already ships a compatible
version, bound to `java.util.logging`, so the SDK's own logging flows through Jylos's
existing log configuration for free.

## Tests

```bash
./scripts/test-plugins.sh
```

This plugin's test
(`plugins-test/com/example/jylos/plugin/builtin/mcp/McpServerPluginTest.java`) is loaded
through the **built JAR** via its own `URLClassLoader`, not compiled flat alongside the
other plugin sources the way Dataview's is. That distinction caught a real bug during
development: the SDK resolves its default JSON mapper via `ServiceLoader`, which by
default consults the calling thread's *context* classloader — not the classloader that
actually loaded the SDK's own classes. On a flat classpath (sources compiled together
with the test) that lookup succeeds by accident, because everything already shares one
classloader; it only fails the way a real plugin install would fail, which is exactly
what loading the actual JAR in isolation reproduces. Fixed in `JylosMcpServer.start()` by
setting the thread's context classloader to the plugin's own before building the server.

The test drives the running server over real HTTP: the `initialize` handshake, session
id issuance, `tools/list`, and `tools/call` for each tool against a real temporary vault
(including confirming a written note lands on disk, and that an existing note's inline
`#tag` shows up in results) — then confirms the port is actually released after
`shutdown()`.
