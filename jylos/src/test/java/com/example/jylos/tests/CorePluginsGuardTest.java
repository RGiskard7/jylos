package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.CorePlugins;
import com.example.jylos.plugin.mermaid.MermaidPlugin;

/**
 * Invariant guard, not behaviour coverage: the list of Jylos's own plugins is the plugins built
 * from {@code plugins-source/} plus Mermaid. A plugin added there without its id here would be
 * treated as a community plugin in the portable build; one removed there would stay listed.
 */
class CorePluginsGuardTest {

    private static final Path PLUGINS_SOURCE = Path.of("..", "plugins-source");
    /** {@code String ID = "x"} or {@code getId() { return "x"; }}. */
    private static final Pattern ID = Pattern.compile(
            "String (?:PLUGIN_)?ID\\s*=\\s*\"([^\"]+)\"|getId\\(\\)\\s*\\{\\s*return\\s*\"([^\"]+)\"");

    @Test
    void theCoreListIsThePluginsBuiltFromTheSourcesAndMermaid() throws IOException {
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.walk(PLUGINS_SOURCE)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")
                    && !f.getFileName().toString().equals("package-info.java")).toList()) {
                Matcher m = ID.matcher(Files.readString(file));
                while (m.find()) {
                    ids.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
            }
        }
        ids.add(new MermaidPlugin().getId());

        assertEquals(new TreeSet<>(CorePlugins.ids()), ids);
    }
}
