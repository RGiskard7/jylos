package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.AppDataDirectory;

/**
 * The plugin manager knows whether each plugin is Jylos's own or the community's, and only a
 * community plugin can be removed: Jylos's own comes with the installation, even when the
 * Windows portable build keeps it in the same folder as the user's.
 */
class PluginManagerOriginTest {

    private String previousDataDir;

    @AfterEach
    void restore() {
        if (previousDataDir == null) {
            System.clearProperty("jylos.data.dir");
        } else {
            System.setProperty("jylos.data.dir", previousDataDir);
        }
        AppDataDirectory.resetForTesting();
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void aPluginIsCommunityUnlessRegisteredAsCore(@TempDir Path dir) throws Exception {
        PluginManager manager = manager();
        Plugin core = PluginLoader.loadPluginJar(TestPluginJars.build(dir, "word-count", "A.jar"));
        Plugin community = PluginLoader.loadPluginJar(TestPluginJars.build(dir, "kanban-extra", "B.jar"));

        manager.registerPlugin(core, PluginOrigin.CORE);
        manager.registerPlugin(community);

        assertEquals(PluginOrigin.CORE, manager.originOf("word-count"));
        assertEquals(PluginOrigin.COMMUNITY, manager.originOf("kanban-extra"));
    }

    @Test
    void onlyACommunityPluginCanBeRemovedEvenInTheSameFolder(@TempDir Path data) throws Exception {
        previousDataDir = System.getProperty("jylos.data.dir");
        System.setProperty("jylos.data.dir", data.toString());
        AppDataDirectory.resetForTesting();
        Path plugins = data.resolve("plugins");
        PluginManager manager = manager();
        manager.registerPlugin(PluginLoader.loadPluginJar(TestPluginJars.build(plugins, "word-count", "WordCountPlugin.jar")),
                PluginOrigin.CORE);
        manager.registerPlugin(PluginLoader.loadPluginJar(TestPluginJars.build(plugins, "kanban-extra", "KanbanExtra.jar")),
                PluginOrigin.COMMUNITY);

        assertFalse(manager.isPluginRemovable("word-count"), "Jylos's own plugin");
        assertTrue(manager.isPluginRemovable("kanban-extra"));
    }

    private static PluginManager manager() {
        return new PluginManager(null, null, null, null, null, null, null, null, null, null, null, note -> {
        }, null, null, null);
    }
}
