package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A plugin Jylos no longer has (the AI assistant) is never loaded again, and what it left on a
 * user's machine goes: its settings (an API key among them) and the copy Jylos itself put in the
 * user's plugins folder. Anything else there stays.
 */
class RetiredPluginsTest {

    private Preferences plugins;

    @BeforeEach
    void node() {
        plugins = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
    }

    @AfterEach
    void removeNode() throws Exception {
        plugins.removeNode();
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void theAiAssistantIsRetired() {
        assertTrue(RetiredPlugins.isRetired("ai-assistant"));
        assertFalse(RetiredPlugins.isRetired("dataview"));
        assertFalse(RetiredPlugins.isRetired(null));
    }

    @Test
    void aJarWithARetiredPluginIsNotLoaded(@TempDir Path dir) throws Exception {
        Path retired = TestPluginJars.build(dir, "ai-assistant", "Renamed.jar");
        Path other = TestPluginJars.build(dir, "still-here-" + System.nanoTime(), "Other.jar");

        assertNull(PluginLoader.loadPluginJar(retired), "not loaded, whatever its file is called");
        assertTrue(PluginLoader.loadPluginJar(other) != null, "another plugin still loads");
    }

    @Test
    void itsSettingsAndTheCopyJylosMadeAreRemovedAndNothingElse(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(".bundled-plugins-copied"), "Bundled plugins copied by Jylos.\n");
        Files.writeString(folder.resolve("AIPlugin.jar"), "jar");
        Files.writeString(folder.resolve("DataviewPlugin.jar"), "jar");
        Files.writeString(folder.resolve("MyOwnPlugin.jar"), "jar");
        plugins.node("ai-assistant").node("ai-plugin-config").put("apiKey", "sk-secret");
        plugins.putBoolean("plugin.disabled.ai-assistant", true);
        plugins.putBoolean("plugin.disabled.dataview", true);
        plugins.node("dataview").put("setting", "kept");

        List<String> done = RetiredPlugins.cleanUp(folder, plugins);

        assertFalse(Files.exists(folder.resolve("AIPlugin.jar")));
        assertTrue(Files.exists(folder.resolve("DataviewPlugin.jar")));
        assertTrue(Files.exists(folder.resolve("MyOwnPlugin.jar")));
        assertFalse(plugins.nodeExists("ai-assistant"), "its settings and the API key are gone");
        assertNull(plugins.get("plugin.disabled.ai-assistant", null));
        assertTrue(plugins.getBoolean("plugin.disabled.dataview", false), "other plugins' state stays");
        assertEquals("kept", plugins.node("dataview").get("setting", null));
        assertEquals(2, done.size(), "what was removed is reported for the log: " + done);
        assertTrue(RetiredPlugins.cleanUp(folder, plugins).isEmpty(), "a second run has nothing to do");
    }

    @Test
    void aJarJylosDidNotPutThereIsLeftAlone(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("AIPlugin.jar"), "jar");

        RetiredPlugins.cleanUp(folder, plugins);

        assertTrue(Files.exists(folder.resolve("AIPlugin.jar")), "without Jylos's mark, the user's own file stays");
    }
}
