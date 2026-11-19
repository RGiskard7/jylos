package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;

/**
 * The favorite and pin buttons over the note show whether the open note is a favorite and
 * pinned, say in their tooltip what a click does, and are off with no note open.
 */
class EditorFavoritePinButtonsTest {

    private static final ResourceBundle ES =
            ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theButtonsFollowTheOpenNote(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Nota.md"), "texto\n", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        List<String> seen = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"), ES);
            new Scene(new Pane((Parent) loader.load()), 900, 600);
            EditorController editor = loader.getController();
            editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                    List::of, false, enabled -> { }, null, null, null);
            Note note = notes.getNoteById("Nota.md").orElseThrow();
            editor.loadNote(note);
            String plain = state(loader);
            note.setFavorite(true);
            note.setPinned(true);
            editor.syncFavoritePinButtons(ES::getString);
            String marked = state(loader);
            editor.loadNote(null);
            return List.of(plain, marked, state(loader));
        });

        assertEquals(List.of(
                "fav on=false off=false [" + ES.getString("action.add_favorite") + "] pin on=false off=false ["
                        + ES.getString("tooltip.pin_note") + "]",
                "fav on=true off=false [" + ES.getString("action.remove_favorite") + "] pin on=true off=false ["
                        + ES.getString("action.unpin_note") + "]",
                "fav on=false off=true [" + ES.getString("tooltip.add_favorite") + "] pin on=false off=true ["
                        + ES.getString("tooltip.pin_note") + "]"), seen);
    }

    private static String state(FXMLLoader loader) {
        ToggleButton fav = (ToggleButton) loader.getNamespace().get("favoriteButton");
        ToggleButton pin = (ToggleButton) loader.getNamespace().get("pinButton");
        return "fav on=" + fav.isSelected() + " off=" + fav.isDisable() + " [" + fav.getTooltip().getText() + "] pin on="
                + pin.isSelected() + " off=" + pin.isDisable() + " [" + pin.getTooltip().getText() + "]";
    }
}
