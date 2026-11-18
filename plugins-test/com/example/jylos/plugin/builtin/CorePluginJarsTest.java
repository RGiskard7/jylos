package com.example.jylos.plugin.builtin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.example.jylos.AppConfig;
import com.example.jylos.plugin.CorePlugins;
import com.example.jylos.plugin.Plugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The JARs scripts/build-plugins.sh makes are Jylos's own plugins, each with its card in the
 * manifest: Plugin-Id, the plugin's real id, and Plugin-Min-Jylos, the version it was built with.
 * Run by scripts/test-plugins.sh after the build, from the jylos/ module.
 */
public final class CorePluginJarsTest {

    private static int passed;
    private static int failed;

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + "  -> " + detail);
        }
    }

    public static void main(String[] args) throws Exception {
        Path plugins = Path.of("plugins");
        Set<String> built = new TreeSet<>();
        try (Stream<Path> jars = Files.list(plugins)) {
            jars.filter(p -> p.toString().endsWith(".jar")).forEach(p -> built.add(p.getFileName().toString()));
        }

        System.out.println("\n-- the JARs built are Jylos's own --");
        check("exactly the JARs Jylos ships", built.equals(new TreeSet<>(CorePlugins.shippedJarNames())),
                built + " vs " + new TreeSet<>(CorePlugins.shippedJarNames()));

        String version = AppConfig.getAppVersion();
        Map<String, String> webNames = new HashMap<>();
        JsonObject catalog = JsonParser.parseString(
                Files.readString(Path.of("..", "plugins-source", "core-plugins.json"))).getAsJsonObject();
        for (JsonElement entry : catalog.getAsJsonArray("plugins")) {
            webNames.put(entry.getAsJsonObject().get("id").getAsString(),
                    entry.getAsJsonObject().get("name").getAsString());
        }
        for (String name : built) {
            System.out.println("\n-- " + name + " --");
            Path jar = plugins.resolve(name);
            Attributes manifest = manifest(jar);
            String declared = manifest.getValue("Plugin-Id");
            Plugin plugin = instance(jar, manifest.getValue("Plugin-Class"));
            String real = plugin.getId();
            check(name + " says its id", declared != null && declared.equals(real), declared + " vs " + real);
            check(name + " is a core plugin", CorePlugins.isCore(real), real);
            check(name + " has the website's name", plugin.getName().equals(webNames.get(real)),
                    plugin.getName() + " vs " + webNames.get(real));
            check(name + " needs this Jylos", version.equals(manifest.getValue("Plugin-Min-Jylos")),
                    manifest.getValue("Plugin-Min-Jylos") + " vs " + version);
        }

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Attributes manifest(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getManifest().getMainAttributes();
        }
    }

    /** The plugin in the JAR; its classloader stays open while the test reads it. */
    private static Plugin instance(Path jar, String pluginClass) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()},
                CorePluginJarsTest.class.getClassLoader());
        return (Plugin) loader.loadClass(pluginClass).getDeclaredConstructor().newInstance();
    }
}
