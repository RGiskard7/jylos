package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real behavioural check for {@link PluginClassLoader}: compiles two tiny, real plugin
 * JARs on the fly (same technique {@code PluginManagerLifecycleTest} already uses) —
 * one that only touches {@link PluginApiSurface}-allowed classes, one that reaches for
 * an unlisted internal class ({@code com.example.jylos.ui.UiDialogs}) — and loads both
 * through the real, unmodified {@link PluginLoader#loadPluginJar} path. This is the one
 * thing a text-scanning guard (which only proves what a plugin's own source imports)
 * cannot: proof that the boundary is actually enforced at class-load time, not just
 * documented as a convention.
 */
class PluginClassLoaderBoundaryTest {

    @TempDir
    Path tempDir;

    @Test
    void aClassWithinTheDocumentedSurfaceLoadsFine() throws Exception {
        Path jar = compilePluginJar("good.plugin", "GoodPlugin", """
                package good.plugin;

                public class GoodPlugin implements com.example.jylos.plugin.Plugin {
                    public String getId() { return "good-plugin-classloader-test"; }
                    public String getName() { return "Good"; }
                    public String getVersion() { return "1.0.0"; }
                    public void initialize(com.example.jylos.plugin.PluginContext context) {
                        // Forces real resolution of an ALLOWED class through this plugin's
                        // own classloader — data.models is explicitly in PluginApiSurface.
                        try {
                            Class.forName("com.example.jylos.data.models.Note", true, getClass().getClassLoader());
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    public void shutdown() {}
                }
                """);

        Plugin plugin = PluginLoader.loadPluginJar(jar);
        try {
            assertNotNull(plugin, "a valid plugin JAR must load");
            // Must not throw: Note is squarely inside the documented surface.
            plugin.initialize(dummyContext());
        } finally {
            PluginLoader.closeAllClassLoaders();
        }
    }

    @Test
    void aClassOutsideTheDocumentedSurfaceIsRejectedAtLoadTimeWithAClearMessage() throws Exception {
        Path jar = compilePluginJar("rogue.plugin", "RoguePlugin", """
                package rogue.plugin;

                public class RoguePlugin implements com.example.jylos.plugin.Plugin {
                    public String getId() { return "rogue-plugin-classloader-test"; }
                    public String getName() { return "Rogue"; }
                    public String getVersion() { return "1.0.0"; }
                    public void initialize(com.example.jylos.plugin.PluginContext context) {
                        // UiDialogs is a real class on the app's own classpath, reachable
                        // by name, but NOT in PluginApiSurface — exactly the coupling
                        // this boundary exists to stop. Reflection instead of a plain
                        // import/call: the point under test is class LOADING being
                        // refused, not merely that it would be poor style to import it.
                        try {
                            Class.forName("com.example.jylos.ui.UiDialogs", true, getClass().getClassLoader());
                        } catch (Throwable t) {
                            throw new RuntimeException("REJECTED: " + t, t);
                        }
                    }
                    public void shutdown() {}
                }
                """);

        Plugin plugin = PluginLoader.loadPluginJar(jar);
        try {
            // Loading the PLUGIN class itself must still succeed — UiDialogs is only
            // referenced inside initialize()'s body, resolved lazily on first use, not
            // eagerly when the plugin class itself is loaded/instantiated. The rejection
            // is expected to surface only once that method actually runs.
            assertNotNull(plugin, "the rogue plugin class itself has nothing unsupported in its own "
                    + "signature/constructor, so it must still load");

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> plugin.initialize(dummyContext()),
                    "initialize() must fail once it actually tries to resolve the unlisted internal class");
            String chain = describeChain(thrown);
            assertTrue(chain.contains("com.example.jylos.ui.UiDialogs"),
                    "the failure must name the exact class that was rejected: " + chain);
            assertTrue(chain.contains("not part of the documented plugin API"),
                    "the failure must point at the documented API, not just fail generically: " + chain);
        } finally {
            PluginLoader.closeAllClassLoaders();
        }
    }

    private static String describeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable current = t; current != null; current = current.getCause()) {
            sb.append(current).append(" | ");
            if (current.getCause() == current) {
                break; // guard against a (theoretical) self-referential cause loop
            }
        }
        return sb.toString();
    }

    private static PluginContext dummyContext() {
        return new PluginContext("classloader-boundary-test", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    /** Compiles {@code sourceBody} (must declare {@code package} + a class named {@code className}
     *  implementing {@link Plugin}) and packages it into a real JAR with a {@code Plugin-Class}
     *  manifest entry — the exact shape {@link PluginLoader} expects, built the same way
     *  {@code PluginManagerLifecycleTest#createPluginJar} already does. */
    private Path compilePluginJar(String packageName, String className, String sourceBody) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            fail("Tests must run on a JDK, not a JRE");
        }

        String packagePath = packageName.replace('.', '/');
        Path sourceDir = tempDir.resolve("src-" + className).resolve(packagePath);
        Path classesDir = tempDir.resolve("classes-" + className);
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

        Path sourceFile = sourceDir.resolve(className + ".java");
        Files.writeString(sourceFile, sourceBody, StandardCharsets.UTF_8);

        int result = compiler.run(null, null, null,
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result, "plugin fixture must compile cleanly");

        Path jarPath = tempDir.resolve(className + ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Plugin-Class"), packageName + "." + className);

        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest);
                var files = Files.walk(classesDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String entryName = classesDir.relativize(file).toString().replace('\\', '/');
                    jar.putNextEntry(new JarEntry(entryName));
                    Files.copy(file, jar);
                    jar.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOExceptionWrapper(e);
                }
            });
        } catch (UncheckedIOExceptionWrapper wrapped) {
            throw wrapped.cause;
        }
        return jarPath;
    }

    /** Plain {@link RuntimeException} to carry an {@link IOException} out of the lambda above
     *  without java.io.UncheckedIOException's misleading name suggesting something else. */
    private static final class UncheckedIOExceptionWrapper extends RuntimeException {
        private final IOException cause;

        UncheckedIOExceptionWrapper(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
