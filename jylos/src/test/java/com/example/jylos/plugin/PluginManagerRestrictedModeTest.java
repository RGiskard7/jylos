package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.AppDataDirectory;

/**
 * Turning restricted mode on stops and unloads the community plugins without forgetting which
 * ones the user had on; turning it off loads them again as they were.
 */
class PluginManagerRestrictedModeTest {

    private String previousDataDir;
    private Preferences node;

    @BeforeEach
    void create() {
        node = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
    }

    @AfterEach
    void restore() throws Exception {
        node.removeNode();
        if (previousDataDir == null) {
            System.clearProperty("jylos.data.dir");
        } else {
            System.setProperty("jylos.data.dir", previousDataDir);
        }
        AppDataDirectory.resetForTesting();
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void communityPluginsGoAndComeBackAsTheyWere(@TempDir Path data) throws Exception {
        previousDataDir = System.getProperty("jylos.data.dir");
        System.setProperty("jylos.data.dir", data.toString());
        AppDataDirectory.resetForTesting();
        Path plugins = data.resolve("plugins");
        TestPluginJars.build(plugins, "kanban-extra", "KanbanExtra.jar");
        TestPluginJars.build(plugins, "quiet-one", "QuietOne.jar");
        PluginManager manager = new PluginManager(null, null, null, null, null, null, null, null, null, null, null,
                note -> {
                }, null, null, null);
        manager.usePreferences(node);
        manager.setCommunityPluginsAllowed(true);
        manager.disablePlugin("quiet-one");

        manager.setCommunityPluginsAllowed(false);

        assertEquals(List.of(), ids(manager), "restricted: none loaded");
        assertFalse(manager.communityPluginsAllowed());

        manager.setCommunityPluginsAllowed(true);

        assertEquals(List.of("kanban-extra", "quiet-one"), ids(manager));
        assertEquals(PluginManager.PluginState.ENABLED, manager.getPluginState("kanban-extra"));
        assertEquals(PluginManager.PluginState.DISABLED, manager.getPluginState("quiet-one"), "it stays as the user left it");
    }

    @Test
    void inRestrictedModeAPluginCannotBeInstalledAndNothingIsCopied(@TempDir Path data) throws Exception {
        previousDataDir = System.getProperty("jylos.data.dir");
        System.setProperty("jylos.data.dir", data.toString());
        AppDataDirectory.resetForTesting();
        Path source = TestPluginJars.build(data.resolve("downloads"), "kanban-extra", "KanbanExtra.jar");
        PluginManager manager = new PluginManager(null, null, null, null, null, null, null, null, null, null, null,
                note -> {
                }, null, null, null);
        manager.usePreferences(node);
        manager.setCommunityPluginsAllowed(false);

        IOException refused = assertThrows(IOException.class, () -> manager.installPluginJar(source));

        assertTrue(refused.getMessage().contains("restricted"), refused.getMessage());
        assertFalse(Files.exists(data.resolve("plugins/KanbanExtra.jar")));
        assertEquals(List.of(), ids(manager));
    }

    private static List<String> ids(PluginManager manager) {
        return manager.getAllPlugins().stream().map(Plugin::getId).sorted().toList();
    }
}
