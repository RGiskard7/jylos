package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * A private note shows a lock next to its title: open while the private notes are unlocked,
 * closed while they are locked. A normal note shows none.
 */
class EditorPrivateLockTest {

    @AfterEach
    void lock() {
        EncryptionService.getInstance().lock();
        EventBus.getInstance().clear();
    }

    @Test
    void aPrivateNoteShowsItsLock(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Secreta.md"), "texto\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Normal.md"), "texto\n", StandardCharsets.UTF_8);
        EncryptionService encryption = EncryptionService.getInstance();
        encryption.configure("clave-candado".toCharArray());
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        Note secret = notes.getNoteById("Secreta.md").orElseThrow();
        secret.setPrivate(true);
        notes.updateNote(secret);

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            out.add(lockOf(notes, "Secreta.md"));
            out.add(lockOf(notes, "Normal.md"));
            encryption.lock();
            out.add(lockOf(notes, "Secreta.md"));
            return out;
        });

        assertEquals(List.of("lock fth-unlock", "no lock", "lock fth-lock"), seen);
    }

    private String lockOf(NoteService notes, String id) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        Parent root = loader.load();
        new Scene(new Pane(root), 900, 600);
        EditorController editor = loader.getController();
        editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                List::of, false, enabled -> { }, null, null, null);
        editor.loadNote(notes.getNoteById(id).orElseThrow());
        Node lock = (Node) loader.getNamespace().get("privateIndicator");
        FontIcon icon = (FontIcon) loader.getNamespace().get("privateIndicatorIcon");
        return lock.isVisible() ? "lock " + icon.getIconLiteral() : "no lock";
    }
}
