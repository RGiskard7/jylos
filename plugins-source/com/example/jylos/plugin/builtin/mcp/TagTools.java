package com.example.jylos.plugin.builtin.mcp;

import static com.example.jylos.plugin.builtin.mcp.McpSupport.displayTitle;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.errorResult;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.noteSummary;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.objectSchema;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.readOnly;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.refusePrivate;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.requiredArgument;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.stringProperty;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.writes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * The tag tools: list the vault's tags, and add, remove or rename them.
 *
 * <p>Deleting a tag outright is not exposed. {@code remove_tag} takes one tag off one
 * note, which is the operation an agent actually needs; wiping a tag from every note at
 * once is a vault-wide edit better left to the desktop UI, where it is visible and
 * undoable.</p>
 */
final class TagTools {

    private TagTools() {
    }

    static List<SyncToolSpecification> build(NoteService noteService, TagService tagService) {
        List<SyncToolSpecification> tools = new ArrayList<>();
        tools.add(listTags(tagService));
        tools.add(addTag(noteService, tagService));
        tools.add(removeTag(noteService, tagService));
        tools.add(renameTag(tagService));
        return tools;
    }

    // ── list_tags ────────────────────────────────────────────────────────────

    private static SyncToolSpecification listTags(TagService tagService) {
        Tool tool = Tool.builder("list_tags", objectSchema(Map.of()))
                .description("Lists every tag in the vault with its note count, most used first.")
                .annotations(readOnly())
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

    // ── add_tag ──────────────────────────────────────────────────────────────

    private static SyncToolSpecification addTag(NoteService noteService, TagService tagService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes."),
                "tag", stringProperty("Tag name, without the leading '#'.")),
                "id", "tag");
        Tool tool = Tool.builder("add_tag", schema)
                .description("Adds a tag to a note, creating the tag if the vault does not have it yet.")
                .annotations(writes(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    String tagName = normalizeTagName(requiredArgument(request.arguments(), "tag"));
                    if (id == null || tagName == null) {
                        return errorResult("Both 'id' and 'tag' are required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    CallToolResult refusal = refusePrivate(note, "tagged");
                    if (refusal != null) {
                        return refusal;
                    }
                    if (!tagService.isValidTagTitle(tagName)) {
                        return errorResult("'" + tagName + "' is not a valid tag name.");
                    }
                    try {
                        tagService.addTagToNote(note, tagService.getOrCreateTag(tagName));
                    } catch (RuntimeException e) {
                        return errorResult("Could not add the tag: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Added #" + tagName + " to '" + displayTitle(note) + "'")
                            .structuredContent(noteSummary(note))
                            .build();
                })
                .build();
    }

    // ── remove_tag ───────────────────────────────────────────────────────────

    private static SyncToolSpecification removeTag(NoteService noteService, TagService tagService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Note id, as returned by list_notes or search_notes."),
                "tag", stringProperty("Tag name, without the leading '#'.")),
                "id", "tag");
        Tool tool = Tool.builder("remove_tag", schema)
                .description("Removes a tag from a note. The tag itself stays in the vault as long as "
                        + "another note still uses it.")
                .annotations(writes(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    String tagName = normalizeTagName(requiredArgument(request.arguments(), "tag"));
                    if (id == null || tagName == null) {
                        return errorResult("Both 'id' and 'tag' are required.");
                    }
                    Optional<Note> found = noteService.getNoteById(id);
                    if (found.isEmpty()) {
                        return errorResult("No note found for id '" + id + "'.");
                    }
                    Note note = found.get();
                    CallToolResult refusal = refusePrivate(note, "untagged");
                    if (refusal != null) {
                        return refusal;
                    }
                    // Match on the note's own tags rather than looking the tag up globally: a
                    // tag typed inline in the body has no persistent id in a filesystem vault,
                    // so its title is the only thing identifying it. Case-insensitive for the
                    // same reason — #Project and #project are one tag.
                    Optional<Tag> attached = note.getTags().stream()
                            .filter(t -> t.getTitle() != null && t.getTitle().equalsIgnoreCase(tagName))
                            .findFirst();
                    if (attached.isEmpty()) {
                        return errorResult("'" + displayTitle(note) + "' is not tagged #" + tagName + ".");
                    }
                    try {
                        tagService.removeTagFromNote(note, attached.get());
                    } catch (RuntimeException e) {
                        return errorResult("Could not remove the tag: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Removed #" + tagName + " from '" + displayTitle(note) + "'")
                            .structuredContent(noteSummary(note))
                            .build();
                })
                .build();
    }

    // ── rename_tag ───────────────────────────────────────────────────────────

    private static SyncToolSpecification renameTag(TagService tagService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "tag", stringProperty("Current tag name, without the leading '#'."),
                "newName", stringProperty("New tag name, without the leading '#'.")),
                "tag", "newName");
        Tool tool = Tool.builder("rename_tag", schema)
                .description("Renames a tag everywhere it is used in the vault.")
                .annotations(writes(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String tagName = normalizeTagName(requiredArgument(request.arguments(), "tag"));
                    String newName = normalizeTagName(requiredArgument(request.arguments(), "newName"));
                    if (tagName == null || newName == null) {
                        return errorResult("Both 'tag' and 'newName' are required.");
                    }
                    Optional<Tag> existing = tagService.getTagByTitle(tagName);
                    if (existing.isEmpty()) {
                        return errorResult("No tag named #" + tagName + " in this vault.");
                    }
                    try {
                        tagService.renameTag(existing.get(), newName);
                    } catch (IllegalArgumentException e) {
                        // The service raises this when the target name is already taken.
                        return errorResult("Could not rename the tag: " + e.getMessage());
                    } catch (RuntimeException e) {
                        return errorResult("Could not rename the tag: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Renamed #" + tagName + " to #" + newName)
                            .structuredContent(Map.of("previous", tagName, "title", newName))
                            .build();
                })
                .build();
    }

    /** Accepts "#tag" as well as "tag", since a model will often include the marker. */
    private static String normalizeTagName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        while (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }
}
