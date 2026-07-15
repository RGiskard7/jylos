package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;

class FolderServiceMoveValidationTest {

    @TempDir
    Path vault;

    private FolderService folderService;

    @BeforeEach
    void setUp() {
        String root = vault.toString();
        folderService = new FolderService(new FolderDAOFileSystem(root), new NoteDAOFileSystem(root));
    }

    @Test
    void folderCannotMoveIntoItself() {
        Folder project = folderService.createFolder("Project");

        assertFalse(folderService.canMoveFolder(project, project));
    }

    @Test
    void folderCannotMoveIntoDescendant() {
        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);
        Folder deep = folderService.createSubfolder("Deep", docs);

        assertFalse(folderService.canMoveFolder(project, deep));
    }

    @Test
    void folderCanMoveIntoUnrelatedFolder() {
        Folder project = folderService.createFolder("Project");
        Folder archive = folderService.createFolder("Archive");

        assertTrue(folderService.canMoveFolder(project, archive));
    }

    @Test
    void invalidMoveInputsAreRejected() {
        Folder project = folderService.createFolder("Project");

        assertFalse(folderService.canMoveFolder(null, project));
        assertFalse(folderService.canMoveFolder(project, null));
        assertFalse(folderService.canMoveFolder(new Folder("No id"), project));
    }
}
