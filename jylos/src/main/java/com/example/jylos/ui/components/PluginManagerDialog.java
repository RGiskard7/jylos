package com.example.jylos.ui.components;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginLoader;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.plugin.PluginManager.PluginState;
import com.example.jylos.plugin.PluginOrigin;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.util.FileManager;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Plugin manager window — lists installed plugins and lets the user enable,
 * disable, install or remove them.
 *
 * <p>Styling comes entirely from the active theme stylesheet (applied via
 * {@link UiDialogs}); there are no inline colours, so it follows light/dark like
 * the rest of the app. All visible text is internationalised.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class PluginManagerDialog {

    private static final Logger logger = LoggerConfig.getLogger(PluginManagerDialog.class);

    private final Stage parentStage;
    private final PluginManager pluginManager;
    private Stage dialogStage;
    private VBox pluginListContainer;
    private Label countLabel;
    private Button installButton;
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
        build();
        dialogStage.showAndWait();
    }

    /** Builds the window with the current plugins, without showing it. */
    void build() {
        createDialog();
        refreshPluginList();
    }

    /** The window's content. */
    Parent root() {
        return dialogStage.getScene().getRoot();
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

        // ── Header ───────────────────────────────────────────────────────────
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

        // ── List ─────────────────────────────────────────────────────────────
        pluginListContainer = new VBox();
        pluginListContainer.getStyleClass().add("plugin-manager-list");

        ScrollPane scrollPane = new ScrollPane(pluginListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("plugin-manager-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // ── Footer ───────────────────────────────────────────────────────────
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("plugin-manager-footer");

        installButton = new Button(i18n("dialog.plugin_manager.install"));
        installButton.getStyleClass().addAll("plugin-manager-action-btn", "plugin-manager-install-btn");
        installButton.setOnAction(e -> installPluginJar());

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        Button closeButton = new Button(i18n("action.close"));
        closeButton.getStyleClass().addAll("plugin-manager-action-btn", "plugin-manager-close-btn");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> dialogStage.close());
        footer.getChildren().addAll(installButton, footerSpacer, closeButton);

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
        plugins.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<Plugin> core = plugins.stream()
                .filter(p -> pluginManager.originOf(p.getId()) == PluginOrigin.CORE).toList();
        List<Plugin> community = plugins.stream()
                .filter(p -> pluginManager.originOf(p.getId()) != PluginOrigin.CORE).toList();

        // Jylos's own: they come with the app and are updated with it; only on or off.
        if (!core.isEmpty()) {
            pluginListContainer.getChildren().add(section(i18n("dialog.plugin_manager.section_core"),
                    i18n("dialog.plugin_manager.section_core_hint")));
            core.forEach(plugin -> pluginListContainer.getChildren().add(createPluginCard(plugin, false)));
        }

        // The community's: in the user's plugins folder, which can be opened and read again; none
        // is loaded until the user allows them (restricted mode).
        boolean allowed = pluginManager.communityPluginsAllowed();
        Button reload = iconButton("fth-refresh-cw", i18n("dialog.plugin_manager.reload"));
        reload.setDisable(!allowed);
        reload.setOnAction(e -> {
            pluginManager.reloadCommunityPlugins();
            refreshPluginList();
        });
        Button openFolder = iconButton("fth-folder", i18n("dialog.plugin_manager.open_folder"));
        openFolder.setOnAction(e -> openPluginsFolder());
        ToggleButton communitySwitch = createToggleSwitch(allowed);
        communitySwitch.getStyleClass().add("plugin-manager-community-switch");
        communitySwitch.setTooltip(new Tooltip(i18n("dialog.plugin_manager.community_toggle")));
        communitySwitch.setAccessibleText(i18n("dialog.plugin_manager.community_toggle"));
        communitySwitch.setOnAction(e -> {
            if (communitySwitch.isSelected() && !confirmCommunityPlugins()) {
                communitySwitch.setSelected(false);
                return;
            }
            allowCommunityPlugins(communitySwitch.isSelected());
        });
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        pluginListContainer.getChildren().add(section(i18n("dialog.plugin_manager.section_community"),
                i18n(allowed ? "dialog.plugin_manager.section_community_hint"
                        : "dialog.plugin_manager.section_community_restricted"),
                reload, openFolder, gap, communitySwitch));
        if (installButton != null) {
            installButton.setDisable(!allowed);
        }
        if (!allowed) {
            for (Path jar : PluginLoader.unloadedCommunityJars()) {
                Label blocked = new Label(jar.getFileName().toString());
                blocked.getStyleClass().add("plugin-manager-blocked");
                pluginListContainer.getChildren().add(blocked);
            }
            return;
        }
        PluginLoader.incompatibleCommunityJars().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Label incompatible = new Label(i18n("dialog.plugin_manager.needs_jylos", entry.getKey(), entry.getValue()));
                    incompatible.setWrapText(true);
                    incompatible.getStyleClass().add("plugin-manager-incompatible");
                    pluginListContainer.getChildren().add(incompatible);
                });
        if (community.isEmpty() && PluginLoader.incompatibleCommunityJars().isEmpty()) {
            Label emptyLabel = new Label(i18n("dialog.plugin_manager.community_empty"));
            emptyLabel.getStyleClass().add("plugin-manager-empty");
            pluginListContainer.getChildren().add(emptyLabel);
        }
        community.forEach(plugin -> pluginListContainer.getChildren().add(createPluginCard(plugin, true)));
    }

    /** Turns restricted mode off ({@code allowed}) or on, and shows the result. */
    void allowCommunityPlugins(boolean allowed) {
        pluginManager.setCommunityPluginsAllowed(allowed);
        refreshPluginList();
    }

    /** Community plugins are other people's code with full access: the user confirms they want them. */
    private boolean confirmCommunityPlugins() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(dialogStage);
        confirm.setTitle(i18n("dialog.plugin_manager.community_confirm_title"));
        confirm.setHeaderText(i18n("dialog.plugin_manager.community_confirm_header"));
        confirm.setContentText(i18n("dialog.plugin_manager.community_confirm_content"));
        return UiDialogs.show(confirm).orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** A section's title, with small actions beside it, and a line saying what it holds. */
    private VBox section(String title, String hint, Node... actions) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("plugin-manager-section-title");
        HBox titleRow = new HBox(4, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getChildren().addAll(actions);
        Label hintLabel = new Label(hint);
        hintLabel.setWrapText(true);
        hintLabel.getStyleClass().add("plugin-manager-section-hint");
        VBox section = new VBox(titleRow, hintLabel);
        section.getStyleClass().add("plugin-manager-section");
        return section;
    }

    private static Button iconButton(String icon, String text) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.setTooltip(new Tooltip(text));
        button.setAccessibleText(text);
        button.getStyleClass().add("plugin-manager-icon-btn");
        return button;
    }

    private void openPluginsFolder() {
        try {
            Path folder = PluginLoader.getPluginsDirectoryFile().toPath();
            Files.createDirectories(folder);
            FileManager.open(folder);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not open the plugins folder", e);
        }
    }

    /** A plugin's card; {@code removable} for the community's, which can be removed. */
    private VBox createPluginCard(Plugin plugin, boolean removable) {
        PluginState state = pluginManager.getPluginState(plugin.getId());
        boolean isEnabled = state == PluginState.ENABLED || state == PluginState.INITIALIZED;

        VBox card = new VBox(8);
        card.getStyleClass().add("plugin-card");

        // Header row: name + version + remove + toggle
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

        headerRow.getChildren().addAll(nameLabel, versionLabel, spacer);
        if (removable) {
            Button removeButton = new Button(i18n("dialog.plugin_manager.remove"));
            removeButton.getStyleClass().addAll("plugin-manager-card-action-btn", "plugin-manager-remove-btn");
            removeButton.setDisable(!pluginManager.isPluginRemovable(plugin.getId()));
            removeButton.setOnAction(e -> removePlugin(plugin));
            headerRow.getChildren().add(removeButton);
        }
        headerRow.getChildren().add(toggle);

        // Description
        Label descLabel = new Label(plugin.getDescription().isEmpty()
                ? i18n("dialog.plugin_manager.no_description")
                : plugin.getDescription());
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("plugin-card-desc");

        // Meta row: author + status
        HBox infoRow = new HBox(14);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        // Jylos's own plugins are Jylos's: who made them is said only for the community's.
        if (removable && !plugin.getAuthor().isEmpty()) {
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

    private void installPluginJar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("dialog.plugin_manager.install_title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(i18n("dialog.plugin_manager.jar_filter"), "*.jar"));
        var selected = chooser.showOpenDialog(dialogStage);
        if (selected == null) {
            return;
        }
        try {
            Plugin plugin = pluginManager.installPluginJar(selected.toPath());
            refreshPluginList();
            showAlert(
                    Alert.AlertType.INFORMATION,
                    i18n("dialog.plugin_manager.install_success_title"),
                    i18n("dialog.plugin_manager.install_success_header", plugin.getName()));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Plugin installation failed", ex);
            showAlert(
                    Alert.AlertType.ERROR,
                    i18n("dialog.plugin_manager.install_error_title"),
                    ex.getMessage());
        }
    }

    private void removePlugin(Plugin plugin) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(dialogStage);
        confirm.setTitle(i18n("dialog.plugin_manager.remove_confirm_title"));
        confirm.setHeaderText(i18n("dialog.plugin_manager.remove_confirm_header", plugin.getName()));
        confirm.setContentText(i18n("dialog.plugin_manager.remove_confirm_content"));

        if (UiDialogs.show(confirm).orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            Plugin removedPlugin = pluginManager.uninstallPlugin(plugin.getId());
            refreshPluginList();
            showAlert(
                    Alert.AlertType.INFORMATION,
                    i18n("dialog.plugin_manager.remove_success_title"),
                    i18n("dialog.plugin_manager.remove_success_header", removedPlugin.getName()));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Plugin removal failed", ex);
            showAlert(
                    Alert.AlertType.ERROR,
                    i18n("dialog.plugin_manager.remove_error_title"),
                    ex.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(dialogStage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        UiDialogs.show(alert);
    }
}
