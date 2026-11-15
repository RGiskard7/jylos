package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.example.jylos.ui.components.KanbanBoard;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.Pane;

/**
 * A board note opens as its board in its own tab, as Obsidian's Kanban plugin does, and can
 * be viewed as Markdown and back. A change on the board is saved as any edit is: the note
 * on disk changes and nothing is left unsaved.
 */
class EditorKanbanTest {

    private static final String BOARD = "%% jylos-kanban %%\n## Por hacer\n- Escribir\n## Hecho\n";

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aBoardNoteOpensAsItsBoardAndAsMarkdownOnRequest(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = vault(vault);

        List<String> seen = onFx(() -> {
            Opened o = open(notes, "Tablero.md");
            ButtonBase toggle = (ButtonBase) o.loader().getNamespace().get("boardViewBtn");
            List<String> out = new ArrayList<>();
            out.add(o.state());
            toggle.fire();
            out.add(o.state());
            toggle.fire();
            out.add(o.state());
            return out;
        });

        assertEquals(List.of("board true source false button true", "board false source true button true",
                "board true source false button true"), seen);
    }

    @Test
    void aNoteThatIsNoBoardShowsItsTextAndNoBoardButton(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = vault(vault);

        String seen = onFx(() -> open(notes, "Nota.md").state());

        assertEquals("board false source true button false", seen);
    }

    @Test
    void aChangeOnTheBoardIsSavedAndNothingIsLeftUnsaved(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        NoteService notes = vault(vault);

        boolean modified = onFx(() -> {
            Opened o = open(notes, "Tablero.md");
            ((KanbanBoard) o.root().lookup(".kanban-board")).addCard(1, "Revisar");
            return o.editor().isModified();
        });

        String saved = Files.readString(vault.resolve("Tablero.md"));
        assertTrue(saved.contains("## Hecho\n- Revisar"), saved);
        assertTrue(saved.contains("- Escribir"), saved);
        assertEquals(false, modified);
    }

    private record Opened(FXMLLoader loader, Parent root, EditorController editor) {
        String state() {
            Node board = root.lookup(".kanban-board");
            Node source = (Node) loader.getNamespace().get("editorPreviewSplitPane");
            Node button = (Node) loader.getNamespace().get("boardViewBtn");
            return "board " + shown(board) + " source " + shown(source) + " button " + shown(button);
        }

        private boolean shown(Node node) {
            for (Node current = node; current != null; current = current.getParent()) {
                if (!current.isVisible()) {
                    return false;
                }
                if (current == root) {
                    return true;
                }
            }
            return false;
        }
    }

    private static NoteService vault(Path vault) throws Exception {
        Files.writeString(vault.resolve("Tablero.md"), BOARD, StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Nota.md"), "texto\n", StandardCharsets.UTF_8);
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private Opened open(NoteService notes, String id) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        Parent root = loader.load();
        new Scene(new Pane(root), 900, 600);
        EditorController editor = loader.getController();
        editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status -> { },
                List::of, false, enabled -> { }, null, null, null);
        editor.loadNote(notes.getNoteById(id).orElseThrow());
        return new Opened(loader, root, editor);
    }
}
