package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The plugins in the installation are Jylos's own (core); those in the user's plugins folder are
 * the community's. A core plugin is never replaced by a copy in the user's folder, and the copies
 * older versions of Jylos made there go.
 */
class PluginOriginTest {

    @AfterEach
    void close() {
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void theInstallationsPluginsAreCoreAndTheUsersAreCommunity(@TempDir Path dir) throws Exception {
        Path install = dir.resolve("install/plugins");
        Path user = dir.resolve("user/plugins");
        TestPluginJars.build(install, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(user, "kanban-extra", "KanbanExtra.jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(install), user);

        assertEquals(Map.of("word-count", PluginOrigin.CORE, "kanban-extra", PluginOrigin.COMMUNITY), origins(report));
    }

    @Test
    void aCopyOfACorePluginInTheUsersFolderIsNotLoadedAndItsJarIsReleased(@TempDir Path dir) throws Exception {
        Path install = dir.resolve("install/plugins");
        Path user = dir.resolve("user/plugins");
        Path core = TestPluginJars.build(install, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(user, "word-count", "MyWordCount.jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(install), user);

        assertEquals(Map.of("word-count", PluginOrigin.CORE), origins(report));
        assertEquals(1, report.getPlugins().size());
        assertEquals(core.toAbsolutePath().normalize(), PluginLoader.jarOf("word-count"));
        assertEquals(1, PluginLoader.openClassLoaderCount(), "the copy's classloader was closed");
    }

    @Test
    void theCopiesOlderVersionsOfJylosMadeAreRemovedAndTheUsersOwnPluginsStay(@TempDir Path dir) throws Exception {
        Path install = dir.resolve("install/plugins");
        Path user = dir.resolve("user/plugins");
        TestPluginJars.build(install, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(user, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(user, "kanban-extra", "KanbanExtra.jar");
        Files.writeString(user.resolve(".bundled-plugins-copied"), "Bundled plugins copied by Jylos.\n");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(install), user);

        assertFalse(Files.exists(user.resolve("WordCountPlugin.jar")), "the old copy of a core plugin");
        assertTrue(Files.exists(user.resolve("KanbanExtra.jar")));
        assertEquals(Map.of("word-count", PluginOrigin.CORE, "kanban-extra", PluginOrigin.COMMUNITY), origins(report));
    }

    @Test
    void withoutJylossMarkACopyWithTheSameNameStays(@TempDir Path dir) throws Exception {
        Path install = dir.resolve("install/plugins");
        Path user = dir.resolve("user/plugins");
        TestPluginJars.build(install, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(user, "word-count", "WordCountPlugin.jar");

        PluginLoader.load(List.of(install), user);

        assertTrue(Files.exists(user.resolve("WordCountPlugin.jar")), "a file Jylos did not put there is not deleted");
    }

    @Test
    void aCommunityPluginNamedLikeTheAppStillLoads(@TempDir Path dir) throws Exception {
        Path install = dir.resolve("install/plugins");
        Path user = dir.resolve("user/plugins");
        Files.createDirectories(install);
        TestPluginJars.build(user, "kanban-extra", "jylos-kanban.jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(install), user);

        assertEquals(Map.of("kanban-extra", PluginOrigin.COMMUNITY), origins(report));
    }

    /** The Windows portable build keeps the user's plugins in the installation's own folder. */
    @Test
    void inAFolderBothShareOnlyJylossOwnPluginsAreCore(@TempDir Path dir) throws Exception {
        Path shared = dir.resolve("portable/plugins");
        TestPluginJars.build(shared, "word-count", "WordCountPlugin.jar");
        TestPluginJars.build(shared, "kanban-extra", "KanbanExtra.jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(shared), shared);

        assertEquals(Map.of("word-count", PluginOrigin.CORE, "kanban-extra", PluginOrigin.COMMUNITY), origins(report));
    }

    private static Map<String, PluginOrigin> origins(PluginLoader.PluginLoadReport report) {
        return report.getPlugins().stream().collect(Collectors.toMap(Plugin::getId, report::originOf));
    }
}
