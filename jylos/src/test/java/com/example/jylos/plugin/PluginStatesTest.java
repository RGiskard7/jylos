package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Whether each plugin is on. In a new install Jylos's own plugins start as decided for each one
 * (those that open a port or write files outside the vault, off); whoever updates keeps what they
 * had, which was every plugin on unless they turned it off. What the user chooses stays.
 */
class PluginStatesTest {

    private Preferences node;

    @BeforeEach
    void create() {
        node = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
    }

    @AfterEach
    void remove() throws Exception {
        node.removeNode();
    }

    @Test
    void aNewInstallStartsWithEachCorePluginsDefault() {
        PluginStates.initialize(node, false);
        PluginStates states = new PluginStates(node);

        assertEquals(List.of(true, true, true, true, true, true, true, true, true),
                List.of("outline", "table-of-contents", "word-count", "reading-time", "templates", "daily-notes",
                        "calendar", "dataview", "mermaid-renderer").stream()
                        .map(id -> states.isEnabled(id, PluginOrigin.CORE)).toList());
        assertEquals(List.of(false, false, false),
                List.of("mcp-server", "publish", "auto-backup").stream()
                        .map(id -> states.isEnabled(id, PluginOrigin.CORE)).toList(),
                "a port, a site written somewhere, backups written somewhere: off until the user wants them");
        assertTrue(states.isEnabled("kanban-extra", PluginOrigin.COMMUNITY));
    }

    @Test
    void whoeverUpdatesKeepsWhatTheyHad() {
        node.putBoolean("plugin.disabled.calendar", true);

        PluginStates.initialize(node, true);
        PluginStates states = new PluginStates(node);

        assertTrue(states.isEnabled("mcp-server", PluginOrigin.CORE), "it was on before");
        assertTrue(states.isEnabled("publish", PluginOrigin.CORE));
        assertFalse(states.isEnabled("calendar", PluginOrigin.CORE), "they had turned it off");
        assertNull(node.get("plugin.disabled.calendar", null), "the old key is replaced");
    }

    @Test
    void itIsDecidedOnceAndTheUsersChoiceStays() {
        PluginStates.initialize(node, true);
        PluginStates states = new PluginStates(node);
        states.setEnabled("mcp-server", false);

        PluginStates.initialize(node, true);

        assertFalse(new PluginStates(node).isEnabled("mcp-server", PluginOrigin.CORE));
    }

    @Test
    void whatTheUserChoosesWinsOverTheDefault() {
        PluginStates.initialize(node, false);
        PluginStates states = new PluginStates(node);

        states.setEnabled("mcp-server", true);
        states.setEnabled("word-count", false);

        assertTrue(states.isEnabled("mcp-server", PluginOrigin.CORE));
        assertFalse(states.isEnabled("word-count", PluginOrigin.CORE));
        states.forget("mcp-server");
        assertFalse(states.isEnabled("mcp-server", PluginOrigin.CORE), "forgotten: back to its default");
    }

    @Test
    void theOldKeyIsStillRead() {
        node.putBoolean("plugin.disabled.kanban-extra", true);

        assertFalse(new PluginStates(node).isEnabled("kanban-extra", PluginOrigin.COMMUNITY));
    }
}
