package com.example.jylos.ui.theme;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The size every icon is drawn at, in the real views and as the app's cells build them, with
 * each built-in theme; and how icons and icon buttons grow with the interface font.
 */
class IconSizesTest {

    private static final String[] VIEWS = {"ToolbarView", "SidebarView", "EditorView", "NotesListView"};

    /** The sizes before icons followed the interface font, at its default 13 px. */
    private static final String AT_13 = "fth-share-2=17, fth-file-plus=17, fth-folder-plus=17, fth-hash=17, "
            + "fth-search=17, fth-sidebar=17, fth-list=17, fth-layout=17, fth-sidebar=17, fth-more-horizontal=17, "
            + "fth-folder=16, fth-tag=16, fth-clock=16, fth-star=16, fth-trash-2=16, fth-filter=16, fth-trash-2=16, "
            + "fth-filter=16, fth-filter=16, fth-filter=16, fth-filter=16, fth-crosshair=16, fth-chevrons-down=16, "
            + "fth-chevron-left=16, fth-chevron-right=16, privateIndicatorIcon=16, readingModeIcon=16, fth-save=16, "
            + "fth-sliders=16, fth-hash=16, boardViewIcon=16, bi-pin-angle=16, fth-star=16, fth-chevron-up=12, "
            + "fth-rotate-ccw=16, fth-rotate-cw=16, fth-bold=16, fth-italic=16, fth-underline=16, fth-link=16, "
            + "fth-bookmark=16, fth-image=16, fth-check-square=16, fth-list=16, fth-code=16, fth-trash-2=16, "
            + "fth-chevron-down=16, fth-file-text=48, fth-file-plus=16, fth-layout=20, fth-search=20, "
            + "fth-share-2=20, fth-columns=20, fth-x=14, viewModeToggleIcon=16, fth-layers=16, fth-refresh-cw=16, "
            + "fth-x=16, explorer-note=16, explorer-folder=16, explorer-mark=16, list-pin=12, list-fav=12, "
            + "list-lock=12, list-note=14, trash-item=16, tab-icon=16, tree-arrow=16, kanban=16, canvas=16, "
            + "plugin=16";

    @ParameterizedTest
    @ValueSource(strings = {"dark-theme.css", "modern-theme.css"})
    void atTheDefaultFontEveryIconKeepsItsSize(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        assertEquals(AT_13, String.join(", ", sizes(theme, 13).icons()));
        assertEquals(28.0, sizes(theme, 13).navButtonWidth());
    }

    /** At 22 px every icon is its 13 px size × 22/13, rounded, and so is an icon button. */
    @ParameterizedTest
    @ValueSource(strings = {"dark-theme.css", "modern-theme.css"})
    void atALargerFontIconsAndIconButtonsGrowInProportion(String theme) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> expected = new ArrayList<>();
        for (String entry : AT_13.split(", ")) {
            int at = entry.lastIndexOf('=');
            long size = Math.round(Integer.parseInt(entry.substring(at + 1)) * 22.0 / 13);
            expected.add(entry.substring(0, at + 1) + size);
        }
        Measured at22 = sizes(theme, 22);
        assertEquals(String.join(", ", expected), String.join(", ", at22.icons()));
        assertEquals(47.0, at22.navButtonWidth(), "28 px × 22/13");
    }

    record Measured(List<String> icons, double navButtonWidth) {
    }

    /** "id-or-literal=size" for every icon, in order, and the editor's Back button width. */
    static Measured sizes(String theme, double fontPx) throws Exception {
        return onFx(() -> {
            VBox root = new VBox();
            root.getStyleClass().addAll("root-container");
            root.setStyle("-fx-font-size: " + fontPx + "px;");
            ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
            for (String view : VIEWS) {
                URL fxml = IconSizesTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/view/" + view + ".fxml");
                root.getChildren().add((Parent) new FXMLLoader(fxml, es).load());
            }
            root.getChildren().add(javaBuiltIcons());
            Scene scene = new Scene(root, 1600, 1200);
            scene.getStylesheets().add(IconSizesTest.class
                    .getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
            UiSizeScale.apply(scene, fontPx, 1.0);
            root.applyCss();
            root.layout();
            root.applyCss();
            List<String> out = new ArrayList<>();
            collect(root, out);
            Region back = (Region) root.lookup("#navBackBtn");
            return new Measured(out, back.getWidth());
        });
    }

    /** Icons built as the cells, tabs and boards build them in code (classes and sizes as there). */
    private static Node javaBuiltIcons() {
        HBox box = new HBox();
        box.getChildren().addAll(
                icon("explorer-note", 13, "feather-icon", "explorer-note-icon"),
                icon("explorer-folder", 13, "feather-icon", "explorer-folder-icon"),
                icon("explorer-mark", 12, "feather-icon", "explorer-mark"),
                icon("list-pin", -1, "feather-pin-active", "icon-12"),
                icon("list-fav", -1, "feather-favorite-active", "icon-12"),
                icon("list-lock", -1, "note-cell-lock", "icon-12"),
                icon("list-note", -1, "note-cell-icon", "icon-14"),
                icon("trash-item", 13, "feather-icon"),
                icon("tab-icon", -1, "feather-icon", "editor-tab-icon"),
                icon("tree-arrow", 12, "feather-icon"),
                icon("kanban", 16, "feather-icon"),
                icon("canvas", 16, "feather-icon"),
                icon("plugin", 16, "feather-icon"));
        return box;
    }

    private static FontIcon icon(String id, int size, String... classes) {
        FontIcon icon = new FontIcon("fth-file");
        icon.setId(id);
        icon.getStyleClass().addAll(classes);
        if (size > 0) {
            icon.setIconSize(size);
        }
        return icon;
    }

    private static void collect(Node node, List<String> out) {
        if (node instanceof FontIcon icon) {
            String name = icon.getId() != null ? icon.getId() : icon.getIconLiteral();
            out.add(name + "=" + icon.getIconSize());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, out);
            }
        }
    }
}
