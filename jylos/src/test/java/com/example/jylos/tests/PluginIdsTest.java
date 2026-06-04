package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.PluginIds;

class PluginIdsTest {

    @Test
    void commandIdUsesPluginPrefixAndSlug() {
        String id = PluginIds.commandId("word-count", "Word Count: Current Note");
        assertTrue(id.startsWith("plugin.word-count."));
        assertEquals("plugin.word-count.word-count-current-note", id);
    }
}
