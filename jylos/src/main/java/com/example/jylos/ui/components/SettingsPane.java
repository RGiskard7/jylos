package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
        private final Label heading;

        Section(String title) {
            page.getStyleClass().add("settings-page");
            heading = new Label(title);
            heading.getStyleClass().add("settings-section-title");
            heading.setWrapText(true);
            page.getChildren().add(heading);
        }

        /**
         * Puts a button labelled {@code text} beside the section's title that runs
         * {@code reset}, meant to put the section's controls back to their defaults.
         */
        public Section resettable(String text, Runnable reset) {
            Button button = new Button(text, new FontIcon("fth-rotate-ccw"));
            button.getStyleClass().add("settings-reset-btn");
            button.setOnAction(e -> reset.run());
            HBox.setHgrow(heading, Priority.ALWAYS);
            heading.setMaxWidth(Double.MAX_VALUE);
            HBox titleRow = new HBox();
            titleRow.getStyleClass().add("settings-section-heading");
            titleRow.setAlignment(Pos.CENTER_LEFT);
            page.getChildren().set(0, titleRow);
            titleRow.getChildren().addAll(heading, button);
            return this;
        }

        /**
         * Adds a setting: its {@code name}, an optional {@code description} under it (or
         * {@code null}), and its {@code control} on the right.
         */
        public Section row(String name, String description, Node control) {
            page.getChildren().add(rowNode(name, description, control));
            return this;
        }

        /** Starts a part of the section: its {@code title}, with its {@code actions} on the right. */
        public Section subheading(String title, Node... actions) {
            Label label = wrapping(title, "settings-subheading-title");
            HBox.setHgrow(label, Priority.ALWAYS);
            HBox heading = new HBox(label);
            heading.getChildren().addAll(actions);
            heading.getStyleClass().add("settings-subheading");
            heading.setAlignment(Pos.CENTER_LEFT);
            page.getChildren().add(heading);
            return this;
        }

        /** Adds a node across the page's width, such as a list or a hint. */
        public Section block(Node node) {
            page.getChildren().add(node);
            return this;
        }
    }

    /**
     * A setting's row, made on its own: for a list filled later (such as files found in a
     * folder) whose rows should read like the others. {@link Section#row} adds one directly.
     */
    public static HBox rowNode(String name, String description, Node control) {
        Label nameLabel = wrapping(name, "settings-row-name");
        VBox text = new VBox(nameLabel);
        text.getStyleClass().add("settings-row-text");
        text.setAlignment(Pos.CENTER_LEFT);
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
        return row;
    }

    /** Several controls side by side, for one row or as a row of buttons under a block. */
    public static HBox controls(Node... nodes) {
        HBox box = new HBox(nodes);
        box.getStyleClass().add("settings-controls");
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
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
