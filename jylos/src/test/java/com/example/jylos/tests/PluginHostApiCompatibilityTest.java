package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginLoader;

class PluginHostApiCompatibilityTest {

    private static final class StubPlugin implements Plugin {
        @Override
        public String getId() {
            return "stub";
        }

        @Override
        public String getName() {
            return "Stub";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public void initialize(com.example.jylos.plugin.PluginContext context) {
        }

        @Override
        public void shutdown() {
        }
    }

    @Test
    void pluginsThatPredateTheCheckDefaultToTheCurrentSupportedVersion() {
        assertTrue(PluginLoader.isHostApiCompatible(new StubPlugin().getHostApiVersion()),
                "A plugin using the default getHostApiVersion() must remain loadable.");
    }

    @Test
    void matchingHostApiVersionIsCompatible() {
        assertTrue(PluginLoader.isHostApiCompatible("1"));
    }

    @Test
    void mismatchedHostApiVersionIsRejected() {
        assertFalse(PluginLoader.isHostApiCompatible("2"));
        assertFalse(PluginLoader.isHostApiCompatible(""));
        assertFalse(PluginLoader.isHostApiCompatible(null));
    }
}
