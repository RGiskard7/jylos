package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A settings window's layout: the sections listed on the left, and the chosen section's page
 * on the right, one row per setting. A row has the setting's name, with an optional short
 * description under it, and its control on the right. The name and description wrap onto more
 * lines rather than being cut, and the control keeps its own width.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class SettingsPane extends BorderPane {

    private final ListView<String> nav = new ListView<>();
    private final ScrollPane pageScroll = new ScrollPane();
    private final List<Section> sections = new ArrayList<>();

    public SettingsPane() {
        getStyleClass().add("settings-pane");
        nav.getStyleClass().add("settings-nav");
        nav.setMinWidth(Region.USE_PREF_SIZE);
        nav.getSelectionModel().selectedIndexProperty().addListener((obs, old, index) -> {
            int i = index.intValue();
            pageScroll.setContent(i >= 0 && i < sections.size() ? sections.get(i).page : null);
            pageScroll.setVvalue(0);
        });

        pageScroll.getStyleClass().add("settings-page-scroll");
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        setLeft(nav);
        setCenter(pageScroll);
    }

    /** Adds a section, listed on the left in the order added, and returns it to fill. */
    public Section addSection(String title) {
        Section section = new Section(title);
        sections.add(section);
        nav.getItems().add(title);
        if (nav.getSelectionModel().isEmpty()) {
            nav.getSelectionModel().select(0);
        }
        return section;
    }

    /** Shows the section at {@code index} (in the order added). */
    public void showSection(int index) {
        nav.getSelectionModel().select(index);
    }

    /** The sections' titles, in the order listed. */
    public List<String> sectionTitles() {
        return List.copyOf(nav.getItems());
    }

    /** One section's page. */
    public final class Section {
        final VBox page = new VBox();

        Section(String title) {
            page.getStyleClass().add("settings-page");
            Label heading = new Label(title);
            heading.getStyleClass().add("settings-section-title");
            heading.setWrapText(true);
            page.getChildren().add(heading);
        }

        /**
         * Adds a setting: its {@code name}, an optional {@code description} under it (or
         * {@code null}), and its {@code control} on the right.
         */
        public Section row(String name, String description, Node control) {
            Label nameLabel = wrapping(name, "settings-row-name");
            VBox text = new VBox(nameLabel);
            text.getStyleClass().add("settings-row-text");
            if (description != null && !description.isBlank()) {
                text.getChildren().add(wrapping(description, "settings-row-description"));
            }
            HBox.setHgrow(text, Priority.ALWAYS);
            // A control keeps its width. The theme lets a drop-down give way down to a minimum
            // (.settings-row > .combo-box-base), so a long choice does not crush the name,
            // which keeps a minimum width too (.settings-row-text).
            if (control instanceof Region region) {
                region.setMinWidth(Region.USE_PREF_SIZE);
            }

            HBox row = new HBox(text);
            row.getStyleClass().add("settings-row");
            row.setAlignment(Pos.CENTER_LEFT);
            if (control != null) {
                row.getChildren().add(control);
            }
            page.getChildren().add(row);
            return this;
        }

        /** Adds a node across the page's width, such as a list or a hint. */
        public Section block(Node node) {
            page.getChildren().add(node);
            return this;
        }
    }

    private static Label wrapping(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
