package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.ExplorerTree;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;

/**
 * The Explorer tree reading through the real services, on both storage backends: the vault
 * root (id "ROOT") lists the top-level folders and the notes outside any folder, and
 * expanding a folder lists its own subfolders and notes.
 */
class ExplorerTreeServicesTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteVault() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("explorer.db"));
        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
            FolderService folders = new FolderService(folderDAO, noteDAO);
            NoteService notes = new NoteService(noteDAO, folderDAO);
            Folder trabajo = folders.createFolder("Trabajo");
            folders.createFolder("Personal");
            folders.createSubfolder("Reuniones", trabajo);
            folders.addNoteToFolder(trabajo, notes.createNote("Acta", "x"));
            notes.createNote("Inicio", "x");

            assertTree(ExplorerTree.Source.of(folders, notes));
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    @Test
    void folderVault() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Path vault = tempDir.resolve("vault");
        Files.createDirectories(vault.resolve("Trabajo/Reuniones"));
        Files.createDirectories(vault.resolve("Personal"));
        Files.writeString(vault.resolve("Trabajo/Acta.md"), "x");
        Files.writeString(vault.resolve("Inicio.md"), "x");
        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(vault.toString());

        assertTree(ExplorerTree.Source.of(new FolderService(folderDAO, noteDAO), new NoteService(noteDAO, folderDAO)));
    }

    private static void assertTree(ExplorerTree.Source source) throws Exception {
        AtomicReference<ExplorerTree> tree = new AtomicReference<>();
        onFx(() -> {
            tree.set(new ExplorerTree(source, Runnable::run, key -> key));
            tree.get().setRoot(new Folder("ROOT", "Vault"));
            return null;
        });
        assertEquals(List.of("Personal", "Trabajo", "Inicio"), settle(() -> titles(tree.get().getView().getRoot())));

        onFx(() -> {
            tree.get().getView().getRoot().getChildren().get(1).setExpanded(true);
            return null;
        });
        assertEquals(List.of("Reuniones", "Acta"),
                settle(() -> titles(tree.get().getView().getRoot().getChildren().get(1))));
    }

    private static List<String> titles(TreeItem<Component> parent) {
        List<String> out = new ArrayList<>();
        for (TreeItem<Component> child : parent.getChildren()) {
            out.add(child.getValue() != null ? child.getValue().getTitle() : "(loading)");
        }
        return out;
    }

    private static <T> T settle(Callable<T> read) throws Exception {
        onFx(() -> null);
        onFx(() -> null);
        return onFx(read);
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
