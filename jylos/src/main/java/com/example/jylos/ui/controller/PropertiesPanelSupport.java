package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.NoteProperty;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.util.WikiLinkResolver;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The editor's properties panel, opened and closed from its button in the title row, like
 * the tags bar: one row per property of the open note, editable in the editing views (where
 * new ones are added in place) and read-only, with clickable {@code [[links]]}, in the
 * reading view. The button marks a note that has properties. The panel stays open or closed
 * from one note to the next.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class PropertiesPanelSupport {

    /** Matches a single wiki-link as the entire property value (full or partial). */
    private static final Pattern WIKI_VALUE = Pattern.compile(
            "\\[\\[([^\\[\\]|#\\n]+?)(?:#[^\\[\\]|\\n]+?)?(?:\\|[^\\[\\]\\n]+?)?\\]\\]");

    /** The panel's nodes, from the editor's FXML. Any may be {@code null} outside the app. */
    record Nodes(ToggleButton toggle, Node dot, VBox section, VBox content, Label emptyLabel, Node addRow) {
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
    /** Whether the panel is open. Closed at first; kept from one note to the next. */
    private boolean open = false;

    PropertiesPanelSupport(Host host) {
        this.host = host;
    }

    /** Opens or closes the panel (its button, in the title row). */
    void setOpen(boolean open) {
        this.open = open;
        applyOpen();
    }

    private void applyOpen() {
        Nodes n = host.nodes();
        if (n.toggle() != null) {
            n.toggle().setSelected(open);
        }
        UiNodes.show(n.section(), open && host.currentNote() != null && !host.viewingAttachment());
    }

    /** Adds an empty row whose name and value are typed in place (editing views only). */
    void addProperty() {
        VBox content = host.nodes().content();
        if (host.currentNote() == null || host.readOnlyView() || content == null) {
            return;
        }
        setOpen(true);
        TextField keyField = new TextField();
        keyField.getStyleClass().add("property-key-field");
        keyField.setPromptText(host.text("property.name_prompt", "Name"));
        keyField.setPrefWidth(120);
        keyField.setMaxWidth(120);
        TextField valueField = new TextField();
        valueField.getStyleClass().add("property-value");
        valueField.setPromptText(host.text("property.value_prompt", "Value"));
        HBox.setHgrow(valueField, Priority.ALWAYS);
        keyField.textProperty().addListener((obs, o, n) -> host.propertiesChanged());
        valueField.textProperty().addListener((obs, o, n) -> host.propertiesChanged());
        keyField.setOnAction(e -> valueField.requestFocus());

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("property-row");
        // A new row is named by what is typed in its name field, read when collected.
        row.setUserData(keyField);
        row.getChildren().addAll(keyField, valueField, removeButton(content, row, null));
        content.getChildren().add(row);
        Platform.runLater(keyField::requestFocus);
    }

    /**
     * Rebuilds the whole panel for the open note and view mode: editable rows and "+ Add
     * property" in the editing views; read-only rows, or "No properties", in the reading view.
     */
    void rebuild() {
        clear();
        Nodes n = host.nodes();
        Note note = host.currentNote();
        Map<String, String> props = note != null ? note.getCustomProperties() : null;
        int count = props != null ? props.size() : 0;
        boolean readOnly = host.readOnlyView();

        UiNodes.show(n.addRow(), !readOnly);
        UiNodes.show(n.emptyLabel(), readOnly && count == 0);
        if (n.dot() != null) {
            n.dot().setVisible(count > 0);
        }
        if (n.toggle() != null) {
            String tip = count > 0
                    ? MessageFormat.format(host.text("tooltip.toggle_properties_count", "Properties ({0})"), count)
                    : host.text("tooltip.toggle_properties", "Properties");
            if (n.toggle().getTooltip() != null) {
                n.toggle().getTooltip().setText(tip);
            } else {
                n.toggle().setTooltip(new Tooltip(tip));
            }
        }
        applyOpen();
        if (count == 0) {
            return;
        }
        props.forEach((key, value) -> {
            if (readOnly) {
                addReadOnlyRow(key, value);
            } else {
                addEditableRow(key, value);
            }
        });
    }

    private Button removeButton(VBox content, HBox row, String key) {
        Button removeBtn = UiNodes.keepWhole(new Button("×"));
        removeBtn.getStyleClass().add("property-remove-btn");
        removeBtn.setTooltip(new Tooltip(host.text("tooltip.remove_property", "Remove property")));
        removeBtn.setOnAction(e -> {
            content.getChildren().remove(row);
            Note note = host.currentNote();
            if (note != null && key != null) {
                note.getCustomProperties().remove(key);
            }
            host.propertiesChanged();
        });
        return removeBtn;
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

        row.getChildren().addAll(keyLabel(key), valueControl, removeButton(content, row, key));
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
            String key = row.getUserData() instanceof TextField keyField ? keyField.getText().trim()
                    : (String) row.getUserData();
            if (key == null || key.isEmpty()) {
                continue;
            }
            for (Node child : row.getChildren()) {
                if (child instanceof TextField tf && tf.getStyleClass().contains("property-value")) {
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
        return content != null && content.getChildren().stream().anyMatch(HBox.class::isInstance);
    }

    void clear() {
        VBox content = host.nodes().content();
        if (content != null) {
            content.getChildren().clear();
        }
    }
}
