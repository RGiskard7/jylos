package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.plugin.PluginLoader;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.plugin.TestPluginJars;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;

/**
 * In restricted mode the plugin manager says which community plugins are not loaded and does not
 * offer to install one; allowing community plugins loads them. A plugin that needs a later Jylos
 * says which one.
 */
class PluginManagerRestrictedDialogTest {

    private String previousDataDir;
    private Preferences node;

    @AfterEach
    void restore() throws Exception {
        if (node != null) {
            node.removeNode();
        }
        if (previousDataDir == null) {
            System.clearProperty("jylos.data.dir");
        } else {
            System.setProperty("jylos.data.dir", previousDataDir);
        }
        AppDataDirectory.resetForTesting();
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void restrictedThenAllowed(@TempDir Path data) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        previousDataDir = System.getProperty("jylos.data.dir");
        System.setProperty("jylos.data.dir", data.toString());
        AppDataDirectory.resetForTesting();
        Path plugins = data.resolve("plugins");
        String id = "test-kanban-" + System.nanoTime();
        TestPluginJars.build(plugins, id, "KanbanExtra.jar");
        TestPluginJars.build(plugins, "test-future-" + System.nanoTime(), "Future.jar", Map.of("Plugin-Min-Jylos", "99.0.0"));
        node = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        PluginManager manager = new PluginManager(null, null, null, null, null, null, null, null, null, null, null,
                note -> {
                }, null, null, null);
        manager.usePreferences(node);
        manager.setCommunityPluginsAllowed(false);
        ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        PluginManagerDialog dialog = new PluginManagerDialog(null, manager);
        dialog.setBundle(es);

        List<String> restricted = onFx(() -> {
            dialog.build();
            return describe(dialog.root(), es);
        });
        List<String> allowed = onFx(() -> {
            dialog.allowCommunityPlugins(true);
            return describe(dialog.root(), es);
        });

        assertEquals(List.of("switch off", "install disabled", "blocked: Future.jar", "blocked: KanbanExtra.jar"), restricted);
        assertEquals(List.of("switch on", "install enabled",
                "incompatible: " + MessageFormat.format(es.getString("dialog.plugin_manager.needs_jylos"), "Future.jar", "99.0.0"),
                "card: Test " + id), allowed);
    }

    private static List<String> describe(Parent root, ResourceBundle es) {
        root.applyCss();
        List<String> out = new ArrayList<>();
        ToggleButton community = (ToggleButton) root.lookup(".plugin-manager-community-switch");
        out.add(community.isSelected() ? "switch on" : "switch off");
        Button install = (Button) root.lookup(".plugin-manager-install-btn");
        out.add(install.isDisabled() ? "install disabled" : "install enabled");
        for (Node node : ((Parent) root.lookup(".plugin-manager-list")).getChildrenUnmodifiable()) {
            if (node.getStyleClass().contains("plugin-manager-blocked")) {
                out.add("blocked: " + ((Label) node).getText());
            } else if (node.getStyleClass().contains("plugin-manager-incompatible")) {
                out.add("incompatible: " + ((Label) node).getText());
            } else if (node.getStyleClass().contains("plugin-card")) {
                out.add("card: " + ((Label) node.lookup(".plugin-card-title")).getText());
            }
        }
        return out;
    }
}
