package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.CorePlugins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The website lists Jylos's own plugins from {@code plugins-source/core-plugins.json}: it has
 * exactly the core plugins, each one said to start on or off as it does in a new install, and
 * with a description in both languages.
 */
class CorePluginsCatalogTest {

    @Test
    void theWebsitesListIsTheCorePluginsAsTheyStart() throws IOException {
        JsonObject catalog = JsonParser.parseString(
                Files.readString(Path.of("..", "plugins-source", "core-plugins.json"))).getAsJsonObject();
        Map<String, Boolean> listed = new TreeMap<>();
        for (JsonElement entry : catalog.getAsJsonArray("plugins")) {
            JsonObject plugin = entry.getAsJsonObject();
            String id = plugin.get("id").getAsString();
            listed.put(id, plugin.get("enabledByDefault").getAsBoolean());
            assertFalse(plugin.get("name").getAsString().isBlank(), id);
            assertFalse(plugin.getAsJsonObject("description").get("en").getAsString().isBlank(), id + " in English");
            assertFalse(plugin.getAsJsonObject("description").get("es").getAsString().isBlank(), id + " in Spanish");
        }

        Map<String, Boolean> actual = new TreeMap<>();
        CorePlugins.ids().forEach(id -> actual.put(id, CorePlugins.enabledByDefault(id)));
        assertEquals(actual, listed);
    }
}
