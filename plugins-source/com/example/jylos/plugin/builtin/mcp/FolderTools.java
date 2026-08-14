package com.example.jylos.plugin.builtin.mcp;

import static com.example.jylos.plugin.builtin.mcp.McpSupport.errorResult;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.objectSchema;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.readOnly;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.requiredArgument;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.stringProperty;
import static com.example.jylos.plugin.builtin.mcp.McpSupport.writes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * The folder tools: list the vault's folders, create one, and delete one.
 *
 * <p>Permanent folder deletion is not exposed, matching the notes: {@code delete_folder}
 * is the recoverable one. Moving and renaming folders are left out too — both reshape the
 * vault around notes the agent was not asked about, and a note can already be relocated
 * one at a time with {@code move_note}.</p>
 */
final class FolderTools {

    private FolderTools() {
    }

    static List<SyncToolSpecification> build(FolderService folderService) {
        List<SyncToolSpecification> tools = new ArrayList<>();
        tools.add(listFolders(folderService));
        tools.add(createFolder(folderService));
        tools.add(deleteFolder(folderService));
        return tools;
    }

    // ── list_folders ─────────────────────────────────────────────────────────

    private static SyncToolSpecification listFolders(FolderService folderService) {
        Tool tool = Tool.builder("list_folders", objectSchema(Map.of()))
                .description("Lists every folder in the vault with its id, name and note count. "
                        + "Use an id from here as move_note's folderId.")
                .annotations(readOnly())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    List<Folder> folders = folderService.getAllFolders();
                    List<Object> summaries = new ArrayList<>(folders.size());
                    StringBuilder text = new StringBuilder();
                    text.append(folders.size()).append(" folder(s):\n");
                    for (Folder folder : folders) {
                        summaries.add(folderSummary(folder, folderService.getNoteCount(folder)));
                        text.append("- ").append(folderService.getFolderPath(folder))
                                .append(" (id: ").append(folder.getId()).append(")\n");
                    }
                    return CallToolResult.builder()
                            .addTextContent(text.toString())
                            .structuredContent(Map.of("folders", summaries, "total", folders.size()))
                            .build();
                })
                .build();
    }

    // ── create_folder ────────────────────────────────────────────────────────

    private static SyncToolSpecification createFolder(FolderService folderService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "name", stringProperty("Folder name."),
                "parentId", stringProperty("Parent folder id from list_folders. "
                        + "Omit it to create the folder at the vault root.")),
                "name");
        Tool tool = Tool.builder("create_folder", schema)
                .description("Creates a folder, at the vault root or inside another folder.")
                .annotations(writes(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String name = requiredArgument(request.arguments(), "name");
                    if (name == null) {
                        return errorResult("The 'name' argument is required.");
                    }
                    String parentId = requiredArgument(request.arguments(), "parentId");
                    Folder parent = null;
                    if (parentId != null) {
                        Optional<Folder> found = folderService.getFolderById(parentId);
                        if (found.isEmpty()) {
                            return errorResult("No folder found for id '" + parentId + "'.");
                        }
                        parent = found.get();
                    }
                    Folder created;
                    try {
                        created = parent != null
                                ? folderService.createSubfolder(name, parent)
                                : folderService.createFolder(name);
                    } catch (RuntimeException e) {
                        return errorResult("Could not create the folder: " + e.getMessage());
                    }
                    if (created == null) {
                        return errorResult("Could not create the folder '" + name
                                + "' — a folder with that name may already exist there.");
                    }
                    return CallToolResult.builder()
                            .addTextContent("Created folder '" + created.getTitle() + "' (id: "
                                    + created.getId() + ")")
                            .structuredContent(folderSummary(created, 0))
                            .build();
                })
                .build();
    }

    // ── delete_folder ────────────────────────────────────────────────────────

    private static SyncToolSpecification deleteFolder(FolderService folderService) {
        Map<String, Object> schema = objectSchema(Map.of(
                "id", stringProperty("Folder id, as returned by list_folders.")),
                "id");
        Tool tool = Tool.builder("delete_folder", schema)
                .description("Deletes a folder. Its notes are not deleted — they are moved to the "
                        + "vault root. Permanent deletion is not available over MCP.")
                .annotations(writes(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String id = requiredArgument(request.arguments(), "id");
                    if (id == null) {
                        return errorResult("The 'id' argument is required.");
                    }
                    Optional<Folder> found = folderService.getFolderById(id);
                    if (found.isEmpty()) {
                        return errorResult("No folder found for id '" + id + "'.");
                    }
                    String name = found.get().getTitle();
                    try {
                        folderService.deleteFolder(id);
                    } catch (RuntimeException e) {
                        return errorResult("Could not delete the folder: " + e.getMessage());
                    }
                    return CallToolResult.builder()
                            .addTextContent("Deleted folder '" + name + "'. Any notes it held are now at "
                                    + "the vault root.")
                            .structuredContent(Map.of("id", id, "title", name, "deleted", true))
                            .build();
                })
                .build();
    }

    private static Map<String, Object> folderSummary(Folder folder, int noteCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", folder.getId());
        summary.put("title", folder.getTitle());
        summary.put("noteCount", noteCount);
        return summary;
    }
}
