package com.example.jylos.ui.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.NoteProperty;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.util.WikiLinkResolver;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The editor's properties panel: one row per property of the open note, editable in the
 * editing views and read-only (with clickable {@code [[links]]}) in the reading view, folded
 * or open, and the dialog that adds a property.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class PropertiesPanelSupport {

    /** Matches a single wiki-link as the entire property value (full or partial). */
    private static final Pattern WIKI_VALUE = Pattern.compile(
            "\\[\\[([^\\[\\]|#\\n]+?)(?:#[^\\[\\]|\\n]+?)?(?:\\|[^\\[\\]\\n]+?)?\\]\\]");

    /** The panel's nodes, from the editor's FXML. Any may be {@code null} outside the app. */
    record Nodes(VBox section, VBox body, VBox content, Label collapseIcon, Button addButton,
            Button addHeaderButton) {
    }

    /** What the panel reads from, and tells, the editor that owns it. */
    interface Host {
        Nodes nodes();

        Note currentNote();

        /** The reading view: properties are shown read-only. */
        boolean readOnlyView();

        /** An attachment (PDF, image, canvas) is shown instead of a note. */
        boolean viewingAttachment();

        /** A property was edited, added or removed. */
        void propertiesChanged();

        /** Follows a {@code [[link]]} clicked in a property. */
        void openLink(String link);

        String text(String key, String fallback);
    }

    private final Host host;
    /** Whether the body is open. Folded by default. */
    private boolean expanded = false;

    PropertiesPanelSupport(Host host) {
        this.host = host;
    }

    /** Opens or folds the panel's body (a click on its header). */
    void toggleExpanded() {
        expanded = !expanded;
        applyExpanded();
    }

    private void applyExpanded() {
        Nodes n = host.nodes();
        Node body = n.body() != null ? n.body() : n.content();
        if (body != null) {
            UiNodes.show(body, expanded);
        }
        if (n.collapseIcon() != null) {
            n.collapseIcon().setText(expanded ? "▼" : "▶");
        }
    }

    /** Asks for a new property's name and value, and adds it (editing views only). */
    void addProperty() {
        Note note = host.currentNote();
        if (note == null || host.readOnlyView()) {
            return;
        }
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle(host.text("dialog.add_property.title", "Add Property"));
        dialog.setHeaderText(host.text("dialog.add_property.header", "Enter property name and value:"));
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType(host.text("action.add", "Add"), ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField keyField = new TextField();
        keyField.setPromptText(host.text("dialog.add_property.key_prompt", "e.g. date, priority"));
        TextField valueField = new TextField();
        valueField.setPromptText(host.text("dialog.add_property.value_prompt", "e.g. 2026-05-31, 1"));

        grid.add(new Label(host.text("label.property_key", "Key:")), 0, 0);
        grid.add(keyField, 1, 0);
        grid.add(new Label(host.text("label.property_value", "Value:")), 0, 1);
        grid.add(valueField, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? new String[] {keyField.getText().trim(), valueField.getText().trim()}
                : null);

        Platform.runLater(keyField::requestFocus);
        UiDialogs.show(dialog).ifPresent(pair -> {
            String key = pair[0];
            String val = pair[1];
            if (!key.isBlank()) {
                // Persist into the model immediately, then rebuild so the new row is rendered
                // with the correct (editable) presentation.
                note.getCustomProperties().put(key, val);
                expanded = true;
                rebuild();
                host.propertiesChanged();
            }
        });
    }

    /**
     * Rebuilds the whole panel for the open note and view mode. Shown only when the note has
     * properties, in every view; values are editable except in the reading view, where
     * {@code [[links]]} are clickable. New properties are added from the title row's button
     * while the note has none, and from the panel's own "+ Add property" line once it shows.
     */
    void rebuild() {
        clear();
        Nodes n = host.nodes();
        Note note = host.currentNote();
        Map<String, String> props = note != null ? note.getCustomProperties() : null;
        boolean hasProps = props != null && !props.isEmpty();
        boolean sectionVisible = note != null && hasProps;
        boolean readOnly = host.readOnlyView();

        UiNodes.show(n.section(), sectionVisible);
        UiNodes.show(n.addHeaderButton(), note != null && !hasProps && !readOnly && !host.viewingAttachment());
        if (n.addButton() != null) {
            UiNodes.show(n.addButton(), !readOnly);
        }
        if (!sectionVisible) {
            return;
        }
        applyExpanded();
        props.forEach((key, value) -> {
            if (readOnly) {
                addReadOnlyRow(key, value);
            } else {
                addEditableRow(key, value);
            }
        });
    }

    /** Editable row: {@code [key] [TextField | CheckBox] [× remove]} — no type icon. */
    private void addEditableRow(String key, String value) {
        VBox content = host.nodes().content();
        if (content == null) {
            return;
        }
        NoteProperty prop = NoteProperty.of(key, value != null ? value : "");

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("property-row");
        row.setUserData(key);

        Control valueControl;
        if (prop.type() == NoteProperty.PropertyType.BOOLEAN) {
            CheckBox cb = new CheckBox();
            cb.setSelected("true".equalsIgnoreCase(value));
            cb.getStyleClass().add("property-value-check");
            cb.setOnAction(e -> host.propertiesChanged());
            valueControl = cb;
        } else {
            TextField tf = new TextField(value != null ? value : "");
            tf.getStyleClass().add("property-value");
            HBox.setHgrow(tf, Priority.ALWAYS);
            tf.textProperty().addListener((obs, o, n) -> host.propertiesChanged());
            valueControl = tf;
        }

        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("property-remove-btn");
        removeBtn.setTooltip(new Tooltip(host.text("tooltip.remove_property", "Remove property")));
        removeBtn.setOnAction(e -> {
            content.getChildren().remove(row);
            Note note = host.currentNote();
            if (note != null) {
                note.getCustomProperties().remove(key);
            }
            host.propertiesChanged();
        });

        row.getChildren().addAll(keyLabel(key), valueControl, removeBtn);
        content.getChildren().add(row);
    }

    /** Read-only row: {@code [key] [value]} where wiki-links are clickable. */
    private void addReadOnlyRow(String key, String value) {
        VBox content = host.nodes().content();
        if (content == null) {
            return;
        }
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("property-row");
        row.setUserData(key);
        Node valueNode = readOnlyValue(value != null ? value : "");
        HBox.setHgrow(valueNode, Priority.ALWAYS);
        row.getChildren().addAll(keyLabel(key), valueNode);
        content.getChildren().add(row);
    }

    private static Label keyLabel(String key) {
        Label keyLabel = new Label(key + ":");
        keyLabel.getStyleClass().add("property-key");
        keyLabel.setMinWidth(90);
        keyLabel.setMaxWidth(120);
        return keyLabel;
    }

    /**
     * A plain {@link Label} when the value has no internal links, or a {@link TextFlow}
     * mixing text and a clickable {@link Hyperlink} for each {@code [[wiki-link]]}.
     */
    private Node readOnlyValue(String value) {
        Matcher m = WIKI_VALUE.matcher(value);
        if (!m.find()) {
            Label label = new Label(value);
            label.getStyleClass().add("property-readonly-value");
            label.setWrapText(true);
            return label;
        }
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("property-readonly-value");
        int last = 0;
        m.reset();
        while (m.find()) {
            if (m.start() > last) {
                flow.getChildren().add(new Text(value.substring(last, m.start())));
            }
            String written = m.group(1).trim();
            String path = WikiLinkResolver.internalLinkPath(written);
            String title = WikiLinkResolver.extractTitle(written);
            String target = path != null ? path : title;
            Hyperlink link = new Hyperlink(title);
            link.getStyleClass().add("property-wikilink");
            link.setOnAction(e -> host.openLink(target));
            flow.getChildren().add(link);
            last = m.end();
        }
        if (last < value.length()) {
            flow.getChildren().add(new Text(value.substring(last)));
        }
        return flow;
    }

    /** The values the panel shows now, by key: what the user has typed, not yet saved. */
    Map<String, String> collect() {
        Map<String, String> result = new LinkedHashMap<>();
        VBox content = host.nodes().content();
        if (content == null) {
            return result;
        }
        for (Node rowNode : content.getChildren()) {
            if (!(rowNode instanceof HBox row)) {
                continue;
            }
            String key = (String) row.getUserData();
            if (key == null) {
                continue;
            }
            for (Node child : row.getChildren()) {
                if (child instanceof TextField tf) {
                    result.put(key, tf.getText());
                    break;
                }
                if (child instanceof CheckBox cb) {
                    result.put(key, String.valueOf(cb.isSelected()));
                    break;
                }
            }
        }
        return result;
    }

    /** Whether any property row is shown. */
    boolean hasRows() {
        VBox content = host.nodes().content();
        return content != null && !content.getChildren().isEmpty();
    }

    void clear() {
        VBox content = host.nodes().content();
        if (content != null) {
            content.getChildren().clear();
        }
    }
}
