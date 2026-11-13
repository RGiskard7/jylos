package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBase;

/**
 * In the Explorer layout the notes panel only shows the results of a tag or a search. It
 * then has a close button, and no grid view or "notes of subfolders" toggle (those belong to
 * browsing a folder, which the Explorer tree does).
 */
class NotesListResultsPanelModeTest {

    @Test
    void resultsModeSwapsTheFolderControlsForACloseButton() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> closes = new ArrayList<>();

        List<Boolean> visibility = onFx(() -> {
            URL fxml = getClass().getClassLoader().getResource("com/example/jylos/ui/view/NotesListView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            loader.load();
            NotesListController controller = loader.getController();
            controller.setCloseResultsAction(() -> closes.add("close"));

            List<Boolean> out = new ArrayList<>();
            out.addAll(shown(controller));
            controller.setResultsPanelMode(true);
            out.addAll(shown(controller));
            button(controller, "closeResultsBtn").fire();
            controller.setResultsPanelMode(false);
            out.addAll(shown(controller));
            return out;
        });

        // order per state: grid toggle, subfolders toggle, close button
        assertEquals(List.of(true, true, false, false, false, true, true, true, false), visibility);
        assertEquals(List.of("close"), closes);
    }

    /**
     * The delete command (editor's trash button, menu, shortcut) deletes the note selected
     * in the list; in the Explorer layout nothing is selected there, so it deletes the note
     * open in the editor instead of doing nothing.
     */
    @Test
    void deleteTargetsTheListSelectionOrElseTheOpenNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> targets = onFx(() -> {
            URL fxml = getClass().getClassLoader().getResource("com/example/jylos/ui/view/NotesListView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            loader.load();
            NotesListController controller = loader.getController();
            com.example.jylos.data.models.Note open = new com.example.jylos.data.models.Note("abierta", "x");
            open.setId("abierta");
            controller.setCurrentNoteSupplier(() -> open);
            List<String> out = new ArrayList<>();
            out.add(String.valueOf(controller.deleteTarget() != null ? controller.deleteTarget().getId() : null));

            Field field = NotesListController.class.getDeclaredField("notesListView");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            javafx.scene.control.ListView<com.example.jylos.data.models.Note> list =
                    (javafx.scene.control.ListView<com.example.jylos.data.models.Note>) field.get(controller);
            com.example.jylos.data.models.Note selected = new com.example.jylos.data.models.Note("lista", "x");
            selected.setId("lista");
            list.getItems().setAll(selected);
            list.getSelectionModel().select(0);
            out.add(controller.deleteTarget().getId());
            return out;
        });

        assertEquals(List.of("abierta", "lista"), targets);
    }

    private static List<Boolean> shown(NotesListController controller) throws Exception {
        return List.of(visible(button(controller, "viewModeToggleBtn")),
                visible(button(controller, "peekRecursiveBtn")),
                visible(button(controller, "closeResultsBtn")));
    }

    private static boolean visible(ButtonBase button) {
        return button.isVisible() && button.isManaged();
    }

    private static ButtonBase button(NotesListController controller, String name) throws Exception {
        Field field = NotesListController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ButtonBase) field.get(controller);
    }
}
