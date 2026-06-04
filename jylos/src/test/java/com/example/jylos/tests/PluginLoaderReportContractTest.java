package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.PluginLoader;
import com.example.jylos.plugin.PluginLoader.PluginLoadReport;

class PluginLoaderReportContractTest {

    @Test
    void loadExternalPluginsWithReportShouldReturnNonNullCollections() {
        PluginLoadReport report = PluginLoader.loadExternalPluginsWithReport();
        assertNotNull(report, "Plugin load report must not be null.");
        assertNotNull(report.getPlugins(), "Loaded plugins list must not be null.");
        assertNotNull(report.getFailures(), "Plugin load failures list must not be null.");
    }
}
