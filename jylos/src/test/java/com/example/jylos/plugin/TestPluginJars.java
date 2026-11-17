package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Builds real plugin JARs for tests: a compiled class implementing {@link Plugin}, and its manifest. */
public final class TestPluginJars {

    private TestPluginJars() {
    }

    /** A plugin with this id in {@code dir/jarName}, its class named after the id. */
    public static Path build(Path dir, String pluginId, String jarName) throws IOException {
        return build(dir, pluginId, jarName, Map.of());
    }

    /** Same, with these extra manifest attributes. */
    public static Path build(Path dir, String pluginId, String jarName, Map<String, String> manifestAttributes)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on a JDK, not a JRE");
        String className = "P" + Integer.toHexString(pluginId.hashCode() & 0x7fffffff) + "x" + System.nanoTime();
        Path work = Files.createTempDirectory(dir, "src");
        Path source = Files.createDirectories(work.resolve("testplugins")).resolve(className + ".java");
        Files.writeString(source, """
                package testplugins;

                public class %s implements com.example.jylos.plugin.Plugin {
                    public String getId() { return "%s"; }
                    public String getName() { return "Test %s"; }
                    public String getVersion() { return "1.0.0"; }
                    public void initialize(com.example.jylos.plugin.PluginContext context) {}
                    public void shutdown() {}
                }
                """.formatted(className, pluginId, pluginId), StandardCharsets.UTF_8);
        Path classes = Files.createDirectories(work.resolve("classes"));
        int result = compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(), source.toString());
        assertEquals(0, result, "the test plugin compiles");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Plugin-Class"), "testplugins." + className);
        manifestAttributes.forEach((k, v) -> manifest.getMainAttributes().put(new Attributes.Name(k), v));
        Path jar = Files.createDirectories(dir).resolve(jarName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
                Stream<Path> files = Files.walk(classes)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    out.write(Files.readAllBytes(file));
                    out.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return jar;
    }
}
