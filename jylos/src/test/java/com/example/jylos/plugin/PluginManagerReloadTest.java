package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.AppDataDirectory;

/** Reload picks up the plugins the user has just copied into their folder, without restarting. */
class PluginManagerReloadTest {

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
    void aPluginCopiedIntoTheFolderIsLoadedOnReloadAndOnlyOnce(@TempDir Path data) throws Exception {
        previousDataDir = System.getProperty("jylos.data.dir");
        System.setProperty("jylos.data.dir", data.toString());
        AppDataDirectory.resetForTesting();
        PluginManager manager = new PluginManager(null, null, null, null, null, null, null, null, null, null, null,
                note -> {
                }, null, null, null);
        manager.usePreferences(node);
        manager.registerPlugin(PluginLoader.loadPluginJar(
                TestPluginJars.build(data.resolve("plugins"), "already-here", "Already.jar")));
        manager.initializeAll();

        TestPluginJars.build(data.resolve("plugins"), "kanban-extra", "KanbanExtra.jar");
        TestPluginJars.build(data.resolve("plugins"), "already-here", "AlreadyCopy.jar");
        List<Plugin> added = manager.reloadCommunityPlugins();

        assertEquals(List.of("kanban-extra"), added.stream().map(Plugin::getId).toList());
        assertEquals(PluginOrigin.COMMUNITY, manager.originOf("kanban-extra"));
        assertEquals(PluginManager.PluginState.ENABLED, manager.getPluginState("kanban-extra"));
        assertEquals(List.of(), manager.reloadCommunityPlugins(), "nothing new the second time");
        assertEquals(2, manager.getAllPlugins().size());
    }
}
