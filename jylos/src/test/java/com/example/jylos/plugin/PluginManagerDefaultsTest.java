package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The plugin manager starts each plugin as {@link PluginStates} says and stores what the user turns on or off. */
class PluginManagerDefaultsTest {

    private Preferences node;
    private final List<String> initialized = new ArrayList<>();

    @BeforeEach
    void create() {
        node = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        PluginStates.initialize(node, false);
    }

    @AfterEach
    void remove() throws Exception {
        node.removeNode();
    }

    @Test
    void aCorePluginThatStartsOffIsNotStartedAndOneThatStartsOnIs() {
        PluginManager manager = manager();
        manager.registerPlugin(plugin("mcp-server"), PluginOrigin.CORE);
        manager.registerPlugin(plugin("word-count"), PluginOrigin.CORE);
        manager.registerPlugin(plugin("kanban-extra"), PluginOrigin.COMMUNITY);

        manager.initializeAll();

        assertEquals(PluginManager.PluginState.DISABLED, manager.getPluginState("mcp-server"));
        assertEquals(PluginManager.PluginState.ENABLED, manager.getPluginState("word-count"));
        assertEquals(PluginManager.PluginState.ENABLED, manager.getPluginState("kanban-extra"));
        assertEquals(List.of("kanban-extra", "word-count"), initialized.stream().sorted().toList(),
                "the MCP server was never started");
    }

    @Test
    void turningOnACorePluginIsRememberedForTheNextStart() {
        PluginManager first = manager();
        first.registerPlugin(plugin("mcp-server"), PluginOrigin.CORE);
        first.initializeAll();

        first.enablePlugin("mcp-server");

        PluginManager next = manager();
        next.registerPlugin(plugin("mcp-server"), PluginOrigin.CORE);
        next.initializeAll();
        assertEquals(PluginManager.PluginState.ENABLED, next.getPluginState("mcp-server"));
        assertTrue(node.getBoolean("plugin.enabled.mcp-server", false));
    }

    private PluginManager manager() {
        PluginManager manager = new PluginManager(null, null, null, null, null, null, null, null, null, null, null,
                note -> {
                }, null, null, null);
        manager.usePreferences(node);
        return manager;
    }

    private Plugin plugin(String id) {
        return new Plugin() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getName() {
                return id;
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public void initialize(PluginContext context) {
                initialized.add(id);
            }

            @Override
            public void shutdown() {
            }
        };
    }
}
