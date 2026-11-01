package com.example.jylos.plugin.builtin.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * Pieces every tool class needs: argument reading, result shaping, JSON Schema fragments
 * and the protocol's tool annotations.
 *
 * <p>These started out private to {@code NoteTools}. They live here now that folder and
 * tag tools need the same ones, so the three tool classes stay comparable — a reader who
 * has understood one can read the next without re-learning its conventions.</p>
 */
final class McpSupport {

    private McpSupport() {
    }

    // ── Results ──────────────────────────────────────────────────────────────

    /**
     * A failed tool call. This is a <em>tool</em> error, not a protocol error: the call
     * itself succeeded, so the client gets a 200 with {@code isError} set and can show the
     * model what went wrong instead of treating the server as broken.
     */
    static CallToolResult errorResult(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    /** The metadata every tool returns for a note, so results are shaped alike. */
    static Map<String, Object> noteSummary(Note note) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", note.getId());
        summary.put("title", displayTitle(note));
        summary.put("tags", note.getTags().stream().map(Tag::getTitle).toList());
        summary.put("favorite", note.isFavorite());
        summary.put("pinned", note.isPinned());
        summary.put("created", note.getCreatedDate());
        summary.put("modified", note.getModifiedDate());
        return summary;
    }

    static String displayTitle(Note note) {
        String title = note.getTitle();
        return title != null && !title.isBlank() ? title : "Untitled";
    }

    static String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Refuses a private note. A note the user encrypted is off limits over MCP even while
     * the desktop session has it unlocked: an MCP client is a different trust boundary
     * from the app the user unlocked it in. Every tool that reads or changes note content
     * goes through here, so the rule cannot be forgotten in one tool and honoured in the
     * rest.
     *
     * @return the refusal to return to the client, or {@code null} when the note is fine
     */
    static CallToolResult refusePrivate(Note note, String action) {
        if (note != null && note.isPrivate()) {
            return errorResult("'" + displayTitle(note) + "' is private and cannot be " + action + " over MCP.");
        }
        return null;
    }

    /**
     * Refuses an id that is not a plain vault-relative path. A note or folder id is its
     * path inside the vault, and every id here comes from the MCP client — potentially a
     * prompt-injected agent. Storage already refuses ids that resolve outside the vault;
     * this is the second barrier, so a tool never even asks storage about one, and the
     * client learns why instead of a misleading "not found".
     *
     * @param argumentName the argument the id arrived in, for the error message
     * @return the refusal to return to the client, or {@code null} when the id is fine
     *         (including {@code null}, which each tool already handles on its own)
     */
    static CallToolResult refuseUnsafeId(String id, String argumentName) {
        if (id == null) {
            return null;
        }
        String path = id.replace('\\', '/');
        boolean absolute = path.startsWith("/") || path.matches("^[A-Za-z]:.*");
        boolean climbs = false;
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                climbs = true;
                break;
            }
        }
        if (absolute || climbs) {
            return errorResult("'" + argumentName + "' must be an id inside the vault, as returned by the "
                    + "list tools; '" + id + "' points outside the vault.");
        }
        return null;
    }

    // ── Arguments ────────────────────────────────────────────────────────────

    static String stringArgument(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }

    /** Reads a required non-blank argument, or {@code null} when it is missing/blank. */
    static String requiredArgument(Map<String, Object> arguments, String key) {
        String value = stringArgument(arguments, key);
        return value != null && !value.isBlank() ? value : null;
    }

    static int intArgument(Map<String, Object> arguments, String key, int fallback) {
        if (arguments == null) {
            return fallback;
        }
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    // ── Tool annotations ─────────────────────────────────────────────────────

    // Behavioural hints the protocol defines so a client can tell what a tool does before
    // calling it — a host can, for example, require confirmation for anything marked
    // destructive. They are hints, not enforcement: the real guarantees are in the
    // handlers. `openWorldHint` is false throughout because every tool here operates on
    // one local vault, never on an external system.

    /** A tool that only reads: no vault state changes. */
    static ToolAnnotations readOnly() {
        return ToolAnnotations.builder()
                .readOnlyHint(true)
                .openWorldHint(false)
                .build();
    }

    /**
     * A tool that changes the vault.
     *
     * @param destructive {@code true} when it removes or overwrites existing content
     * @param idempotent  {@code true} when repeating the same call leaves the same state
     */
    static ToolAnnotations writes(boolean destructive, boolean idempotent) {
        return ToolAnnotations.builder()
                .readOnlyHint(false)
                .destructiveHint(destructive)
                .idempotentHint(idempotent)
                .openWorldHint(false)
                .build();
    }

    // ── JSON Schema builders ─────────────────────────────────────────────────

    static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    static Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }
}
