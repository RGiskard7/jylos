package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A load report always has its lists, also when there is nothing to load and when a JAR is not a
 * plugin. It reads folders of its own, not the user's plugins or preferences.
 */
class PluginLoaderReportContractTest {

    @AfterEach
    void close() {
        PluginLoader.closeAllClassLoaders();
    }

    @Test
    void theReportHasItsListsWithNothingToLoad(@TempDir Path dir) {
        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(dir.resolve("install")), dir.resolve("user"),
                true, "2.6.0");

        assertNotNull(report.getPlugins());
        assertNotNull(report.getFailures());
        assertEquals(0, report.getPlugins().size());
    }

    @Test
    void aJarThatIsNotAPluginIsAFailureNotACrash(@TempDir Path dir) throws Exception {
        Path user = Files.createDirectories(dir.resolve("user"));
        Files.writeString(user.resolve("Broken.jar"), "not a jar");

        PluginLoader.PluginLoadReport report = PluginLoader.load(List.of(), user, true, "2.6.0");

        assertEquals(List.of(), report.getPlugins());
        assertEquals(1, report.getFailures().size(), String.valueOf(report.getFailures()));
    }
}
