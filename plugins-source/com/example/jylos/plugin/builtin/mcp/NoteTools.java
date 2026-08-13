package com.example.jylos.plugin.builtin.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * The MCP tools this server exposes: read-mostly access to the vault, plus two
 * deliberately narrow writes (create a note, replace a note's content).
 *
 * <h2>Scope, on purpose</h2>
 * <p>No delete, no trash, no permanent removal — an external MCP client is a genuinely
 * different trust boundary from the desktop UI's own confirmation dialogs, and a first
 * version handing an AI agent the ability to destroy vault content is not a risk worth
 * taking before there is any track record for what "professional AI layer" usage of this
 * server actually looks like. Widening the tool set later is a much smaller step than
 * walking back a destructive tool that shipped too early.</p>
 *
 * <h2>Why no Dataview query tool</h2>
 * <p>Plugins cannot call into one another — each only receives the {@code PluginContext}
 * the host gives it, with no registry of other plugins' capabilities. Exposing "run this
 * Dataview query" over MCP would need a host-level extension point that does not exist
 * today, so this first version sticks to what {@link NoteService}, {@link FolderService}
 * and {@link TagService} already provide directly.</p>
 */
final class NoteTools {

    private NoteTools() {
    }

    static List<SyncToolSpecification> build(NoteService noteService, FolderService folderService,
            TagService tagService) {
        List<SyncToolSpecification> tools = new ArrayList<>();
        tools.add(listNotes(noteService));
        tools.add(searchNotes(noteService));
        tools.add(readNote(noteService));
        tools.add(createNote(noteService));
        tools.add(updateNote(noteService));
        tools.add(listTags(tagService));
        return tools;
    }

    // ── list_notes ───────────────────────────────────────────────────────────

    private static SyncToolSpecification listNotes(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "limit", integerProperty("Maximum number of notes to return (default 50).")));
        Tool tool = Tool.builder("list_notes", schema)
                .description("Lists notes in the vault, most recently modified first. "
                        + "Returns id, title, tags and modified date for each note; use "
                        + "read_note to get a note's full content.")
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    int limit = intArgument(request.arguments(), "limit", 50);
                    List<Note> notes = noteService.getAllNotes();
                    List<Note> sorted = new ArrayList<>(notes);
                    sorted.sort((a, b) -> safe(b.getModifiedDate()).compareTo(safe(a.getModifiedDate())));
                    List<Note> page = sorted.subList(0, Math.min(limit, sorted.size()));

                    List<Object> summaries = new ArrayList<>(page.size());
                    StringBuilder text = new StringBuilder();
                    text.append(page.size()).append(" of ").append(sorted.size()).append(" note(s):\n");
                    for (Note note : page) {
                        summaries.add(noteSummary(note));
                        text.append("- ").append(displayTitle(note)).append(" (id: ").append(note.getId())
                                .append(")\n");
                    }
                    return CallToolResult.builder()
                            .addTextContent(text.toString())
                            .structuredContent(Map.of("notes", summaries, "total", sorted.size()))
                            .build();
                })
                .build();
    }

    // ── search_notes ─────────────────────────────────────────────────────────

    private static SyncToolSpecification searchNotes(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "query", stringProperty("Search text."),
                "limit", integerProperty("Maximum number of results to return (default 20).")));
        Tool tool = Tool.builder("search_notes", schema)
                .description("Full-text search over note titles and content.")
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String query = stringArgument(request.arguments(), "query");
                    if (query == null || query.isBlank()) {
                        return errorResult("The 'query' argument is required.");
                    }
                    int limit = intArgument(request.arguments(), "limit", 20);
                    List<Note> results = noteService.searchNotes(query);
                    List<Note> page = results.subList(0, Math.min(limit, results.size()));

                    List<Object> summaries = new ArrayList<>(page.size());
                    StringBuilder text = new StringBuilder();
                    text.append(page.size()).append(" of ").append(results.size())
                            .append(" match(es) for \"").append(query).append("\":\n");
                    for (Note note : page) {
                        summaries.add(noteSummary(note));
                        text.append("- ").append(displayTitle(note)).append(" (id: ").append(note.getId())
                                .append(")\n");
                    }
                    return CallToolResult.builder()
                            .addTextContent(text.toString())
                            .structuredContent(Map.of("notes", summaries, "total", results.size()))
                            .build();
                })
                .build();
    }

    // ── read_note ────────────────────────────────────────────────────────────

    private static SyncToolSpecification readNote(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes."),
                "title", stringProperty("Exact note title (used if id is not given).")));
        Tool tool = Tool.builder("read_note", schema)
                .description("Reads a note's full content and metadata by id or exact title. "
                        + "A private note's content is not returned even if it is currently unlocked "
                        + "in the desktop app.")
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = stringArgument(request.arguments(), "id");
                    String title = stringArgument(request.arguments(), "title");
                    Optional<Note> found = (id != null && !id.isBlank())
                            ? noteService.getNoteById(id)
                            : (title != null ? noteService.findNoteByTitle(title) : Optional.empty());
                    if (found.isEmpty()) {
                        return errorResult("No note found for "
                                + (id != null ? "id '" + id + "'" : "title '" + title + "'") + ".");
                    }
                    Note note = found.get();
                    if (note.isPrivate()) {
                        return errorResult("'" + displayTitle(note) + "' is private; its content is not exposed "
                                + "over MCP.");
                    }
                    Map<String, Object> full = new LinkedHashMap<>(noteSummary(note));
                    full.put("content", note.getContent() != null ? note.getContent() : "");
                    return CallToolResult.builder()
                            .addTextContent(note.getContent() != null ? note.getContent() : "")
                            .structuredContent(full)
                            .build();
                })
                .build();
    }

    // ── create_note ──────────────────────────────────────────────────────────

    private static SyncToolSpecification createNote(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "title", stringProperty("Note title."),
                "content", stringProperty("Initial Markdown content (optional).")),
                "title");
        Tool tool = Tool.builder("create_note", schema)
                .description("Creates a new note at the vault root.")
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String title = stringArgument(request.arguments(), "title");
                    if (title == null || title.isBlank()) {
                        return errorResult("The 'title' argument is required.");
                    }
                    String content = stringArgument(request.arguments(), "content");
                    Note created;
                    try {
                        created = noteService.createNote(title, content != null ? content : "");
                    } catch (RuntimeException e) {
                        return errorResult("Could not create the note: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Created '" + displayTitle(created) + "' (id: " + created.getId() + ")")
                            .structuredContent(noteSummary(created))
                            .build();
                })
                .build();
    }

    // ── update_note ──────────────────────────────────────────────────────────

    private static SyncToolSpecification updateNote(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes."),
                "content", stringProperty("New Markdown content, replacing the note in full.")),
                "id", "content");
        Tool tool = Tool.builder("update_note", schema)
                .description("Replaces a note's content. The title and metadata are left unchanged.")
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = stringArgument(request.arguments(), "id");
                    String content = stringArgument(request.arguments(), "content");
                    if (id == null || id.isBlank() || content == null) {
                        return errorResult("Both 'id' and 'content' are required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    if (note.isPrivate()) {
                        return errorResult("'" + displayTitle(note) + "' is private and cannot be edited over MCP.");
                    }
                    note.setContent(content);
                    try {
                        noteService.updateNote(note);
                    } catch (RuntimeException e) {
                        return errorResult("Could not update the note: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Updated '" + displayTitle(note) + "' (id: " + note.getId() + ")")
                            .structuredContent(noteSummary(note))
                            .build();
                })
                .build();
    }

    // ── list_tags ────────────────────────────────────────────────────────────

    private static SyncToolSpecification listTags(TagService tagService) {
        Tool tool = Tool.builder("list_tags", objectSchema(Map.of()))
                .description("Lists every tag in the vault with its note count, most used first.")
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    List<Tag> tags = tagService.getTagsByUsage();
                    List<Object> summaries = new ArrayList<>(tags.size());
                    StringBuilder text = new StringBuilder();
                    text.append(tags.size()).append(" tag(s):\n");
                    for (Tag tag : tags) {
                        int count = tagService.getNoteCountForTag(tag);
                        summaries.add(Map.of("title", tag.getTitle(), "noteCount", count));
                        text.append("- #").append(tag.getTitle()).append(" (").append(count).append(")\n");
                    }
                    return CallToolResult.builder()
                            .addTextContent(text.toString())
                            .structuredContent(Map.of("tags", summaries))
                            .build();
                })
                .build();
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static Map<String, Object> noteSummary(Note note) {
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

    private static String displayTitle(Note note) {
        String title = note.getTitle();
        return title != null && !title.isBlank() ? title : "Untitled";
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    private static String stringArgument(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }

    private static int intArgument(Map<String, Object> arguments, String key, int fallback) {
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

    // ── JSON Schema builders ─────────────────────────────────────────────────

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }
}
