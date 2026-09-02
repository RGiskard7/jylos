package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.ui.controller.EditorController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Pane;

class UiPresentationFxmlGuardTest {

    private static final int FX_OPERATION_TIMEOUT_SECONDS = 20;

    private static boolean fxRuntimeAvailable = false;

    @BeforeAll
    static void initFxRuntime() {
        fxRuntimeAvailable = FxTestSupport.isFxRuntimeAvailable();
    }

    @Test
    void sidebarTabsShouldExposeIdsForRuntimePresentationSwitch() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        Map<String, Object> nodes = loadNamespace("/com/example/jylos/ui/view/SidebarView.fxml");

        assertTrue(nodes.containsKey("foldersTab"), "Sidebar folders tab should have fx:id.");
        assertTrue(nodes.containsKey("tagsTab"), "Sidebar tags tab should have fx:id.");
        assertTrue(nodes.containsKey("recentTab"), "Sidebar recent tab should have fx:id.");
        assertTrue(nodes.containsKey("favoritesTab"), "Sidebar favorites tab should have fx:id.");
        assertTrue(nodes.containsKey("trashTab"), "Sidebar trash tab should have fx:id.");
    }

    @Test
    void editorViewShouldStartWithCollapsedTagsAndReadingControlAvailable() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        Map<String, Object> nodes = loadNamespace("/com/example/jylos/ui/view/EditorView.fxml");
        Node toggleTagsBtn = node(nodes, "toggleTagsBtn");
        Node tagsContainer = node(nodes, "tagsContainer");
        Node readingModeButton = node(nodes, "readingModeButton");

        assertTrue(!((javafx.scene.control.ToggleButton) toggleTagsBtn).isSelected(),
                "Tags toggle should start collapsed by default.");
        assertTrue(!tagsContainer.isVisible() && !tagsContainer.isManaged(),
                "Tags container should be hidden and unmanaged on startup.");
        assertTrue(readingModeButton instanceof javafx.scene.control.Button,
                "Editing/reading must use an action button rather than a persistent toggle.");
        assertTrue(((javafx.scene.control.ButtonBase) readingModeButton).getGraphic() != null,
                "Editing/reading button should support icon rendering.");
    }

    @Test
    void deleteButtonInTheBottomFormatToolbarOnlyCarriesTheIconButtonClass() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        Map<String, Object> nodes = loadNamespace("/com/example/jylos/ui/view/EditorView.fxml");
        Node deleteBtn = node(nodes, "editorDeleteBtn");

        // editorDeleteBtn is icon-only (a trash-can FontIcon graphic, no text) — it must
        // match every other icon-only button in the format toolbar (undo, bold, link, …),
        // not the text-glyph buttons' classes (format-btn-text, and format-quote's much
        // larger font-size meant for the "❝" quote glyph), which it had picked up by
        // copy-paste even though its size was pinned elsewhere and never visibly changed.
        assertTrue(deleteBtn.getStyleClass().contains("format-btn"),
                "delete button must carry the base format-btn class");
        assertTrue(!deleteBtn.getStyleClass().contains("format-btn-text"),
                "delete button is icon-only and must not carry the text-glyph-button class");
        assertTrue(!deleteBtn.getStyleClass().contains("format-quote"),
                "delete button must not carry the quote button's oversized-font class");
    }

    @Test
    void editorUsesCodeMirrorHostForMarkdownEditing() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        Map<String, Object> nodes = loadNamespace("/com/example/jylos/ui/view/EditorView.fxml");

        assertTrue(nodes.get("noteContentArea") instanceof MarkdownEditorView,
                "Note content editor should use the CodeMirror host component.");
    }

    @Test
    void textCommandsDoNotRegisterGlobalAccelerators() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        CountDownLatch latch = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.ENGLISH);
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/jylos/ui/view/ToolbarView.fxml"), bundle);
                loader.load();
                MenuBar menuBar = (MenuBar) loader.getNamespace().get("menuBar");
                Menu editMenu = menuBar.getMenus().get(1);

                assertTrue(editMenu.getItems().get(3).getAccelerator() == null,
                        "Cut must use the focused text control's native shortcut.");
                assertTrue(editMenu.getItems().get(4).getAccelerator() == null,
                        "Copy must use the focused text control's native shortcut.");
                assertTrue(editMenu.getItems().get(5).getAccelerator() == null,
                        "Paste must use the focused text control's native shortcut.");
            } catch (AssertionError e) {
                failure[0] = e;
            } catch (Exception e) {
                failure[0] = new AssertionError(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(FX_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Accelerator check timed out.");
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    @Test
    void editorExposesTabBarAndSaveIndicator() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        Map<String, Object> nodes = loadNamespace("/com/example/jylos/ui/view/EditorView.fxml");

        assertTrue(nodes.containsKey("editorTabBar"),
                "EditorView should host the open-note tab strip.");
        assertTrue(nodes.containsKey("dirtySaveIndicator"),
                "EditorView should host the inline save indicator dot.");
    }

    @Test
    void editorFormatToolbarShouldRemainVisibleAfterLoadingNote() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        CountDownLatch latch = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.ENGLISH);
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                        bundle);
                Parent root = loader.load();
                EditorController controller = loader.getController();
                controller.loadNote(new Note("note.md", "Note", "# Heading\n\nBody"));

                Pane host = new Pane(root);
                host.resize(1000, 700);
                Scene scene = new Scene(host, 1000, 700);
                scene.getStylesheets().add(getClass()
                        .getResource("/com/example/jylos/ui/css/dark-theme.css")
                        .toExternalForm());
                host.applyCss();
                host.layout();

                Node toolbar = findByStyleClass(root, "format-toolbar-container");
                assertTrue(toolbar != null, "Format toolbar container should exist. Tree: " + describeTree(root, 0));
                assertTrue(toolbar.isVisible() && toolbar.isManaged(),
                        "Format toolbar should be visible and managed when a note is open.");
                assertTrue(toolbar.getLayoutBounds().getHeight() >= 40,
                        "Format toolbar should keep a usable height after layout.");
                assertTrue(findVisibleVerticalScrollBar(toolbar) == null,
                        "Format toolbar should not show a vertical scrollbar.");
            } catch (AssertionError e) {
                failure[0] = e;
            } catch (Exception e) {
                failure[0] = new AssertionError(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(FX_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS), "JavaFX layout check timed out.");
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static Node findByStyleClass(Node node, String styleClass) {
        if (node.getStyleClass().contains(styleClass)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findByStyleClass(child, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Map<String, Object> loadNamespace(String resource) throws Exception {
        final Map<String, Object>[] namespace = new Map[1];
        CountDownLatch latch = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.ENGLISH);
                FXMLLoader loader = new FXMLLoader(getClass().getResource(resource), bundle);
                loader.load();
                namespace[0] = loader.getNamespace();
            } catch (Exception e) {
                failure[0] = new AssertionError(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(FX_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS), "FXML load timed out: " + resource);
        if (failure[0] != null) {
            throw failure[0];
        }
        return namespace[0];
    }

    private static Node node(Map<String, Object> nodes, String id) {
        Object node = nodes.get(id);
        assertTrue(node instanceof Node, "Expected JavaFX node with fx:id=" + id);
        return (Node) node;
    }

    private static String describeTree(Node node, int depth) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n").append("  ".repeat(depth))
                .append(node.getClass().getSimpleName())
                .append(" ")
                .append(node.getStyleClass());
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                builder.append(describeTree(child, depth + 1));
            }
        }
        return builder.toString();
    }

    private static ScrollBar findVisibleVerticalScrollBar(Node node) {
        if (node instanceof ScrollBar scrollBar
                && scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                && scrollBar.isVisible()) {
            return scrollBar;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollBar found = findVisibleVerticalScrollBar(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
