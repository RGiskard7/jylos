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

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

/**
 * A card's link opens the note it names by its path, as any link in the board's note does:
 * the card hands the whole link, folder included, to the editor's link handler, which resolves
 * it from the board's note. Before, a link with a folder opened nothing.
 */
class KanbanCardLinkTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aCardLinkIsOpenedWithItsPath(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Tablero.md"),
                "%% jylos-kanban %%\n## Por hacer\n- [[Personal/Reunión]]\n- [[Proyectos/Reunión|la otra]]\n",
                StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        List<String> opened = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            Parent root = loader.load();
            new Scene(new Pane(root), 900, 600);
            EditorController editor = loader.getController();
            List<String> links = new ArrayList<>();
            editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                    List::of, false, enabled -> { }, (link, newTab) -> links.add(link), null, null);
            editor.loadNote(notes.getNoteById("Tablero.md").orElseThrow());
            root.applyCss(); // the cards sit in a scroll pane, which holds them once it has its skin
            root.layout();
            for (Node card : root.lookupAll(".kanban-card")) {
                card.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                        false, false, false, false, false, false, false, false, false, true, null));
            }
            return links;
        });

        assertEquals(List.of("Personal/Reunión", "Proyectos/Reunión"), opened);
    }
}
