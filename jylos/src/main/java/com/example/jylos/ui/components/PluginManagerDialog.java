package com.example.jylos.ui.components;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.plugin.PluginManager.PluginState;
import com.example.jylos.ui.UiDialogs;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Plugin manager window — lists installed plugins and lets the user enable or
 * disable each one.
 *
 * <p>Styling comes entirely from the active theme stylesheet (applied via
 * {@link UiDialogs}); there are no inline colours, so it follows light/dark like
 * the rest of the app. All visible text is internationalised.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class PluginManagerDialog {

    private final Stage parentStage;
    private final PluginManager pluginManager;
    private Stage dialogStage;
    private VBox pluginListContainer;
    private Label countLabel;
    private ResourceBundle bundle;

    /** Toggle switches per plugin id, so the list can refresh their state. */
    private final Map<String, ToggleButton> toggleButtons = new HashMap<>();

    public PluginManagerDialog(Stage parentStage, PluginManager pluginManager) {
        this.parentStage = parentStage;
        this.pluginManager = pluginManager;
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** Shows the Plugin Manager window. */
    public void show() {
        createDialog();
        refreshPluginList();
        dialogStage.showAndWait();
    }

    private String i18n(String key) {
        try {
            return bundle != null ? bundle.getString(key) : key;
        } catch (Exception e) {
            return key;
        }
    }

    private String i18n(String key, Object... args) {
        return MessageFormat.format(i18n(key), args);
    }

    private void createDialog() {
        dialogStage = new Stage();
        dialogStage.initOwner(parentStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.setTitle(i18n("dialog.plugin_manager.title"));
        dialogStage.setMinWidth(520);
        dialogStage.setMinHeight(420);

        VBox root = new VBox();
        root.getStyleClass().add("plugin-manager");

        // ── Header ──────────────────────────────────────────────────────────
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("plugin-manager-header");

        Label titleLabel = new Label(i18n("dialog.plugin_manager.header"));
        titleLabel.getStyleClass().add("plugin-manager-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countLabel = new Label();
        countLabel.getStyleClass().add("plugin-manager-count");

        header.getChildren().addAll(titleLabel, spacer, countLabel);

        // ── List ────────────────────────────────────────────────────────────
        pluginListContainer = new VBox();
        pluginListContainer.getStyleClass().add("plugin-manager-list");

        ScrollPane scrollPane = new ScrollPane(pluginListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("plugin-manager-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // ── Footer ──────────────────────────────────────────────────────────
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("plugin-manager-footer");

        Button closeButton = new Button(i18n("action.close"));
        closeButton.getStyleClass().add("plugin-manager-close-btn");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> dialogStage.close());
        footer.getChildren().add(closeButton);

        root.getChildren().addAll(header, scrollPane, footer);

        Scene scene = new Scene(root, 560, 520);
        UiDialogs.apply(scene);
        dialogStage.setScene(scene);
    }

    private void refreshPluginList() {
        pluginListContainer.getChildren().clear();
        toggleButtons.clear();

        List<Plugin> plugins = new ArrayList<>(pluginManager.getAllPlugins());
        countLabel.setText(i18n("dialog.plugin_manager.count", plugins.size()));

        if (plugins.isEmpty()) {
            Label emptyLabel = new Label(i18n("dialog.plugin_manager.empty"));
            emptyLabel.getStyleClass().add("plugin-manager-empty");
            pluginListContainer.getChildren().add(emptyLabel);
            return;
        }

        plugins.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (Plugin plugin : plugins) {
            pluginListContainer.getChildren().add(createPluginCard(plugin));
        }
    }

    private VBox createPluginCard(Plugin plugin) {
        PluginState state = pluginManager.getPluginState(plugin.getId());
        boolean isEnabled = state == PluginState.ENABLED || state == PluginState.INITIALIZED;

        VBox card = new VBox(8);
        card.getStyleClass().add("plugin-card");

        // Header row: name + version + toggle
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(plugin.getName());
        nameLabel.getStyleClass().add("plugin-card-title");

        Label versionLabel = new Label("v" + plugin.getVersion());
        versionLabel.getStyleClass().add("plugin-card-version");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton toggle = createToggleSwitch(isEnabled);
        toggle.setOnAction(e -> togglePlugin(plugin.getId(), toggle.isSelected()));
        toggleButtons.put(plugin.getId(), toggle);

        headerRow.getChildren().addAll(nameLabel, versionLabel, spacer, toggle);

        // Description
        Label descLabel = new Label(plugin.getDescription().isEmpty()
                ? i18n("dialog.plugin_manager.no_description")
                : plugin.getDescription());
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("plugin-card-desc");

        // Meta row: author + status
        HBox infoRow = new HBox(14);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        if (!plugin.getAuthor().isEmpty()) {
            Label authorLabel = new Label(i18n("dialog.plugin_manager.author", plugin.getAuthor()));
            authorLabel.getStyleClass().add("plugin-card-author");
            infoRow.getChildren().add(authorLabel);
        }

        Label statusLabel = new Label(isEnabled
                ? i18n("dialog.plugin_manager.enabled")
                : i18n("dialog.plugin_manager.disabled"));
        statusLabel.getStyleClass().addAll("plugin-card-status",
                isEnabled ? "plugin-card-status-on" : "plugin-card-status-off");
        infoRow.getChildren().add(statusLabel);

        card.getChildren().addAll(headerRow, descLabel, infoRow);
        return card;
    }

    /**
     * A pill toggle whose track colour and knob position are driven purely by CSS
     * ({@code .plugin-switch} / {@code .plugin-switch:selected}).
     */
    private ToggleButton createToggleSwitch(boolean isOn) {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("plugin-switch");
        toggle.setSelected(isOn);

        Region knob = new Region();
        knob.getStyleClass().add("plugin-switch-knob");
        toggle.setGraphic(knob);
        return toggle;
    }

    private void togglePlugin(String pluginId, boolean enable) {
        if (enable) {
            pluginManager.enablePlugin(pluginId);
        } else {
            pluginManager.disablePlugin(pluginId);
        }
        refreshPluginList();
    }
}
