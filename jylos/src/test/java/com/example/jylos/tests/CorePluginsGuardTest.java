package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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

    /**
     * The JAR names Jylos's own plugins are shipped as, as scripts/build-plugins.sh names them: a
     * bundle's {@code plugin.jar}, or a single-file plugin's class name.
     */
    @Test
    void theShippedJarNamesAreTheOnesTheBuildMakes() throws IOException {
        Path builtin = PLUGINS_SOURCE.resolve("com/example/jylos/plugin/builtin");
        Set<String> names = new TreeSet<>();
        try (Stream<Path> entries = Files.list(builtin)) {
            for (Path entry : entries.toList()) {
                String file = entry.getFileName().toString();
                if (Files.isDirectory(entry) && Files.exists(entry.resolve("plugin.properties"))) {
                    Properties bundle = new Properties();
                    try (var in = Files.newInputStream(entry.resolve("plugin.properties"))) {
                        bundle.load(in);
                    }
                    names.add(bundle.getProperty("plugin.jar") + ".jar");
                } else if (file.endsWith(".java") && !file.equals("package-info.java")) {
                    names.add(file.replace(".java", ".jar"));
                }
            }
        }

        assertEquals(new TreeSet<>(CorePlugins.shippedJarNames()), names);
    }
}
