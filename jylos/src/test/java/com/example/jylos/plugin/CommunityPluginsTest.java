package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Community plugins: one that needs a later Jylos is not loaded, and none is loaded until the
 * user allows community plugins (restricted mode, as in Obsidian), unless they already had some
 * when they updated.
 */
class CommunityPluginsTest {

    private Preferences node;

    @BeforeEach
    void create() {
        node = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
    }

    @AfterEach
    void close() throws Exception {
        node.removeNode();
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void aPluginThatNeedsALaterJylosIsNotLoadedAndSaysWhichOne(@TempDir Path dir) throws Exception {
        Path user = dir.resolve("plugins");
        TestPluginJars.build(user, "too-new", "TooNew.jar", Map.of("Plugin-Min-Jylos", "2.7.0"));
        TestPluginJars.build(user, "just-right", "JustRight.jar", Map.of("Plugin-Min-Jylos", "2.6.0"));
        TestPluginJars.build(user, "no-card", "NoCard.jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(), user, true, "2.6.0");

        assertEquals(List.of("just-right", "no-card"), report.getPlugins().stream().map(Plugin::getId).sorted().toList());
        assertEquals(Map.of("TooNew.jar", "2.7.0"), PluginLoader.incompatibleCommunityJars());
    }

    @Test
    void inRestrictedModeNoCommunityPluginIsLoadedButJylossAre(@TempDir Path dir) throws Exception {
        Path install = dir.resolve("install");
        Path user = dir.resolve("plugins");
        TestPluginJars.build(install, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(user, "kanban-extra", "KanbanExtra.jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(install), user, false, "2.6.0");

        assertEquals(List.of("word-count"), report.getPlugins().stream().map(Plugin::getId).toList());
        assertEquals(List.of(user.resolve("KanbanExtra.jar").toAbsolutePath().normalize()),
                PluginLoader.unloadedCommunityJars(user));
    }

    @Test
    void aNewInstallStartsInRestrictedMode(@TempDir Path dir) throws Exception {
        Path user = Files.createDirectories(dir.resolve("plugins"));

        assertFalse(new PluginStates(node).communityAllowed(user));
        Files.writeString(user.resolve("KanbanExtra.jar"), "jar");
        assertFalse(new PluginStates(node).communityAllowed(user), "decided once: a plugin added later waits for the user");
    }

    @Test
    void whoeverUpdatesWithTheirOwnPluginsKeepsThem(@TempDir Path dir) throws Exception {
        Path user = Files.createDirectories(dir.resolve("plugins"));
        Files.writeString(user.resolve("KanbanExtra.jar"), "jar");

        assertTrue(new PluginStates(node).communityAllowed(user));
    }

    @Test
    void copiesOfJylossOwnPluginsDoNotCountAsTheUsersOwn(@TempDir Path dir) throws Exception {
        Path user = Files.createDirectories(dir.resolve("plugins"));
        Files.writeString(user.resolve("WordCountPlugin.jar"), "jar");
        Files.writeString(user.resolve("AIPlugin.jar"), "jar");

        assertFalse(new PluginStates(node).communityAllowed(user));
    }

    @Test
    void theUsersChoiceIsKept(@TempDir Path dir) throws Exception {
        Path user = Files.createDirectories(dir.resolve("plugins"));
        PluginStates states = new PluginStates(node);

        states.setCommunityAllowed(true);

        assertTrue(new PluginStates(node).communityAllowed(user));
    }
}
