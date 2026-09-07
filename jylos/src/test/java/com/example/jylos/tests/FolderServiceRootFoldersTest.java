package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;

/**
 * {@link FolderService#getRootFolders()} on a filesystem-backed vault.
 *
 * <p>It used to answer "is this a root folder?" with {@code getParentFolder(id) == null},
 * which only holds on the SQLite backend. The filesystem backend resolves a parent by
 * walking the path up, so a top-level folder's parent comes back as the synthetic
 * {@code ROOT} folder for the vault directory — never null — and the filter matched
 * nothing at all. Measured against a real 922-folder vault it returned 0 root folders,
 * so everything seeded from it (the publish dialog's folder tree, and the folder
 * structure mirrored into an exported static site) silently flattened to a bare list of
 * every note.</p>
 */
class FolderServiceRootFoldersTest {

    @Test
    void topLevelFoldersOfAFilesystemVaultAreReportedAsRootFolders(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Projects/Alpha"));
        Files.createDirectories(vault.resolve("Projects/Beta"));
        Files.createDirectories(vault.resolve("Journal"));
        Files.writeString(vault.resolve("Journal/entry.md"), "# Entry\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("loose.md"), "# Loose\n", StandardCharsets.UTF_8);

        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        FolderService folderService = new FolderService(folderDao, new NoteDAOFileSystem(vault.toString()));

        List<Folder> roots = folderService.getRootFolders();
        Set<String> rootTitles = roots.stream().map(Folder::getTitle).collect(Collectors.toSet());

        // Both bounds matter: the bug returned an empty list, so "contains Projects" alone
        // would not have caught it going the other way (every folder reported as root).
        assertEquals(Set.of("Projects", "Journal"), rootTitles,
                "exactly the two top-level directories are root folders");

        Folder projects = roots.stream().filter(f -> "Projects".equals(f.getTitle())).findFirst().orElseThrow();
        Set<String> subTitles = folderService.getSubfolders(projects).stream()
                .map(Folder::getTitle)
                .collect(Collectors.toSet());
        assertEquals(Set.of("Alpha", "Beta"), subTitles,
                "and the nested ones hang off their parent instead of being reported as roots too");
        assertTrue(rootTitles.stream().noneMatch(t -> t.equals("Alpha") || t.equals("Beta")),
                "a nested folder must never also appear at the root");
    }
}
