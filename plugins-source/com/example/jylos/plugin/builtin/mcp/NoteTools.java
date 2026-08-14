package com.example.jylos.plugin.builtin.mcp;

import static com.example.jylos.plugin.builtin.mcp.McpSupport.displayTitle;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.errorResult;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.intArgument;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.noteSummary;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.objectSchema;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.readOnly;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.refusePrivate;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.requiredArgument;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.safe;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.stringArgument;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.stringProperty;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.writes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * The note tools: read, search, create, edit, rename, move, delete and restore.
 *
 * <h2>Deleting means the trash</h2>
 * <p>{@code delete_note} moves a note to the trash, the same thing Obsidian's own delete
 * does by default, and {@code restore_note} brings it back. Permanent deletion and
 * emptying the trash are deliberately <em>not</em> exposed: an MCP client is a different
 * trust boundary from the desktop UI's confirmation dialogs, and every destructive thing
 * reachable from here has to be undoable from the app.</p>
 *
 * <h2>Ids move</h2>
 * <p>A note's id is its path inside the vault, so renaming or moving a note changes it.
 * Both tools return the new id and say so in their description, otherwise an agent's next
 * call would use an id that no longer resolves.</p>
 *
 * <h2>Why no Dataview query tool</h2>
 * <p>Plugins cannot call into one another — each only receives the {@code PluginContext}
 * the host gives it, with no registry of other plugins' capabilities. Exposing "run this
 * Dataview query" over MCP would need a host-level extension point that does not exist
 * today.</p>
 */
final class NoteTools {

    private NoteTools() {
    }

    static List<SyncToolSpecification> build(NoteService noteService, FolderService folderService) {
        List<SyncToolSpecification> tools = new ArrayList<>();
        tools.add(listNotes(noteService));
        tools.add(searchNotes(noteService));
        tools.add(readNote(noteService));
        tools.add(createNote(noteService));
        tools.add(updateNote(noteService));
        tools.add(renameNote(noteService));
        tools.add(moveNote(noteService, folderService));
        tools.add(deleteNote(noteService));
        tools.add(restoreNote(noteService));
        tools.add(listTrash(noteService));
        return tools;
    }

    // ── list_notes ───────────────────────────────────────────────────────────

    private static SyncToolSpecification listNotes(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "limit", McpSupport.integerProperty("Maximum number of notes to return (default 50).")));
        Tool tool = Tool.builder("list_notes", schema)
                .description("Lists notes in the vault, most recently modified first. "
                        + "Returns id, title, tags and modified date for each note; use "
                        + "read_note to get a note's full content.")
                .annotations(readOnly())
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
                "limit", McpSupport.integerProperty("Maximum number of results to return (default 20).")),
                "query");
        Tool tool = Tool.builder("search_notes", schema)
                .description("Full-text search over note titles and content.")
                .annotations(readOnly())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String query = requiredArgument(request.arguments(), "query");
                    if (query == null) {
                        return errorResult("The 'query' argument is required.");
                    }
                    int limit = intArgument(request.arguments(), "limit", 20);
                    List<Note> matches = noteService.searchNotes(query);
                    List<Note> page = matches.subList(0, Math.min(limit, matches.size()));

                    List<Object> summaries = new ArrayList<>(page.size());
                    StringBuilder text = new StringBuilder();
                    text.append(page.size()).append(" of ").append(matches.size())
                            .append(" match(es) for '").append(query).append("':\n");
                    for (Note note : page) {
                        summaries.add(noteSummary(note));
                        text.append("- ").append(displayTitle(note)).append(" (id: ").append(note.getId())
                                .append(")\n");
                    }
                    return CallToolResult.builder()
                            .addTextContent(text.toString())
                            .structuredContent(Map.of("notes", summaries, "total", matches.size()))
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
                .annotations(readOnly())
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
                    CallToolResult refusal = refusePrivate(note, "read");
                    if (refusal != null) {
                        return refusal;
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
                .annotations(writes(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String title = requiredArgument(request.arguments(), "title");
                    if (title == null) {
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
                .annotations(writes(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    String content = stringArgument(request.arguments(), "content");
                    if (id == null || content == null) {
                        return errorResult("Both 'id' and 'content' are required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    CallToolResult refusal = refusePrivate(note, "edited");
                    if (refusal != null) {
                        return refusal;
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

    // ── rename_note ──────────────────────────────────────────────────────────

    private static SyncToolSpecification renameNote(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes."),
                "title", stringProperty("New title.")),
                "id", "title");
        Tool tool = Tool.builder("rename_note", schema)
                .description("Renames a note. The id changes with the title, because a note's id is "
                        + "its path in the vault — use the id returned here for any further calls.")
                .annotations(writes(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    String title = requiredArgument(request.arguments(), "title");
                    if (id == null || title == null) {
                        return errorResult("Both 'id' and 'title' are required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    CallToolResult refusal = refusePrivate(note, "renamed");
                    if (refusal != null) {
                        return refusal;
                    }
                    // Same path the desktop UI takes: set the title and save. The DAO renames
                    // the file and the note comes back carrying its new id.
                    note.setTitle(title);
                    try {
                        noteService.updateNote(note);
                    } catch (RuntimeException e) {
                        return errorResult("Could not rename the note: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Renamed to '" + displayTitle(note) + "' (new id: " + note.getId() + ")")
                            .structuredContent(noteSummary(note))
                            .build();
                })
                .build();
    }

    // ── move_note ────────────────────────────────────────────────────────────

    private static SyncToolSpecification moveNote(NoteService noteService, FolderService folderService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes."),
                "folderId", stringProperty("Destination folder id from list_folders. "
                        + "Omit it, or pass an empty string, to move the note to the vault root.")),
                "id");
        Tool tool = Tool.builder("move_note", schema)
                .description("Moves a note into a folder, or to the vault root. The id changes with the "
                        + "location, because a note's id is its path in the vault — use the id returned "
                        + "here for any further calls.")
                .annotations(writes(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    if (id == null) {
                        return errorResult("The 'id' argument is required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    CallToolResult refusal = refusePrivate(note, "moved");
                    if (refusal != null) {
                        return refusal;
                    }

                    // An absent or blank folderId means the vault root, which the service
                    // expresses as a null destination.
                    String folderId = requiredArgument(request.arguments(), "folderId");
                    Folder destination = null;
                    if (folderId != null) {
                        Optional<Folder> target = folderService.getFolderById(folderId);
                        if (target.isEmpty()) {
                            return errorResult("No folder found for id '" + folderId + "'.");
                        }
                        destination = target.get();
                    }
                    try {
                        folderService.moveNoteToFolder(note, destination);
                    } catch (RuntimeException e) {
                        return errorResult("Could not move the note: " + e.getMessage());
                    }
                    String where = destination != null ? "'" + destination.getTitle() + "'" : "the vault root";
                    return CallToolResult.builder()
                            .addTextContent("Moved '" + displayTitle(note) + "' to " + where
                                    + " (new id: " + note.getId() + ")")
                            .structuredContent(noteSummary(note))
                            .build();
                })
                .build();
    }

    // ── delete_note ──────────────────────────────────────────────────────────

    private static SyncToolSpecification deleteNote(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes.")),
                "id");
        Tool tool = Tool.builder("delete_note", schema)
                .description("Moves a note to the trash. It is recoverable: list_trash shows it and "
                        + "restore_note brings it back. Permanent deletion is not available over MCP.")
                .annotations(writes(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    if (id == null) {
                        return errorResult("The 'id' argument is required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    CallToolResult refusal = refusePrivate(note, "deleted");
                    if (refusal != null) {
                        return refusal;
                    }
                    try {
                        noteService.moveToTrash(id);
                    } catch (RuntimeException e) {
                        return errorResult("Could not delete the note: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Moved '" + displayTitle(note) + "' to the trash. "
                                    + "Use restore_note with id '" + id + "' to undo this.")
                            .structuredContent(Map.of("id", id, "title", displayTitle(note), "trashed", true))
                            .build();
                })
                .build();
    }

    // ── restore_note ─────────────────────────────────────────────────────────

    private static SyncToolSpecification restoreNote(NoteService noteService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_trash.")),
                "id");
        Tool tool = Tool.builder("restore_note", schema)
                .description("Restores a note from the trash back into the vault.")
                .annotations(writes(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    if (id == null) {
                        return errorResult("The 'id' argument is required.");
                    }
                    // The service restores by id and simply does nothing when it does not
                    // match anything in the trash, so without this check the tool would
                    // cheerfully report a restore that never happened. A trashed note's id
                    // is its path under .trash, not the path it had before deletion.
                    Optional<Note> trashed = noteService.getTrashNotes().stream()
                            .filter(note -> id.equals(note.getId()))
                            .findFirst();
                    if (trashed.isEmpty()) {
                        return errorResult("No note with id '" + id + "' is in the trash. "
                                + "Use list_trash to get the id to restore.");
                    }
                    try {
                        noteService.restoreNote(id);
                    } catch (RuntimeException e) {
                        return errorResult("Could not restore the note: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Restored '" + displayTitle(trashed.get()) + "' from the trash.")
                            .structuredContent(Map.of("id", id, "restored", true))
                            .build();
                })
                .build();
    }

    // ── list_trash ───────────────────────────────────────────────────────────

    private static SyncToolSpecification listTrash(NoteService noteService) {
        Tool tool = Tool.builder("list_trash", objectSchema(Map.of()))
                .description("Lists the notes currently in the trash, each restorable with restore_note.")
                .annotations(readOnly())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    List<Note> trashed = noteService.getTrashNotes();
                    List<Object> summaries = new ArrayList<>(trashed.size());
                    StringBuilder text = new StringBuilder();
                    text.append(trashed.size()).append(" note(s) in the trash:\n");
                    for (Note note : trashed) {
                        summaries.add(noteSummary(note));
                        text.append("- ").append(displayTitle(note)).append(" (id: ").append(note.getId())
                                .append(")\n");
                    }
                    return CallToolResult.builder()
                            .addTextContent(text.toString())
                            .structuredContent(Map.of("notes", summaries, "total", trashed.size()))
                            .build();
                })
                .build();
    }
}
