package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.example.jylos.plugin.PluginOrigin;
import com.example.jylos.plugin.TestPluginJars;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * The plugin manager shows Jylos's own plugins and the community's in two sections: Jylos's can
 * only be turned on or off, the community's can also be removed, and Reload shows one the user
 * has just copied into their plugins folder.
 */
class PluginManagerDialogTest {

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
    void twoSectionsOnlyCommunityPluginsCanBeRemovedAndReloadShowsANewOne(@TempDir Path data) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        previousDataDir = System.getProperty("jylos.data.dir");
        System.setProperty("jylos.data.dir", data.toString());
        AppDataDirectory.resetForTesting();
        String core = "test-core-" + System.nanoTime();
        String community = "test-community-" + System.nanoTime();
        PluginManager manager = new PluginManager(null, null, null, null, null, null, null, null, null, null, null,
                note -> {
                }, null, null, null);
        node = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        manager.usePreferences(node);
        manager.setCommunityPluginsAllowed(true);
        manager.registerPlugin(PluginLoader.loadPluginJar(TestPluginJars.build(data.resolve("install"), core, "Core.jar")),
                PluginOrigin.CORE);
        manager.registerPlugin(PluginLoader.loadPluginJar(
                TestPluginJars.build(data.resolve("plugins"), community, "Community.jar")), PluginOrigin.COMMUNITY);
        ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        PluginManagerDialog dialog = new PluginManagerDialog(null, manager);
        dialog.setBundle(es);

        List<String> before = onFx(() -> {
            dialog.build();
            return describe(dialog.root(), es);
        });
        String fresh = "test-fresh-" + System.nanoTime();
        TestPluginJars.build(data.resolve("plugins"), fresh, "Fresh.jar");
        List<String> after = onFx(() -> {
            button(dialog.root(), es.getString("dialog.plugin_manager.reload")).fire();
            return describe(dialog.root(), es);
        });

        assertEquals(List.of(es.getString("dialog.plugin_manager.section_core"), "Test " + core + " (no remove)",
                es.getString("dialog.plugin_manager.section_community"), "Test " + community + " (remove)"), before);
        assertEquals(List.of(es.getString("dialog.plugin_manager.section_core"), "Test " + core + " (no remove)",
                es.getString("dialog.plugin_manager.section_community"), "Test " + community + " (remove)",
                "Test " + fresh + " (remove)"), after);
    }

    /** The list in order: each section's title, then each card's name and whether it can be removed. */
    private static List<String> describe(Parent root, ResourceBundle es) {
        root.applyCss(); // the scroll pane's content is in its skin
        List<String> out = new ArrayList<>();
        Node list = root.lookup(".plugin-manager-list");
        for (Node node : ((Parent) list).getChildrenUnmodifiable()) {
            if (node.getStyleClass().contains("plugin-manager-section")) {
                out.add(((Label) node.lookup(".plugin-manager-section-title")).getText());
            } else if (node.getStyleClass().contains("plugin-card")) {
                String name = ((Label) node.lookup(".plugin-card-title")).getText();
                Button remove = (Button) node.lookup(".plugin-manager-remove-btn");
                out.add(name + (remove == null ? " (no remove)" : remove.isDisabled() ? " (remove, disabled)" : " (remove)"));
            }
        }
        return out;
    }

    private static Button button(Parent root, String tooltip) {
        root.applyCss();
        for (Node node : root.lookupAll(".button")) {
            Tooltip tip = node instanceof Button b ? b.getTooltip() : null;
            if (tip != null && tooltip.equals(tip.getText())) {
                return (Button) node;
            }
        }
        throw new AssertionError("no button " + tooltip);
    }
}
