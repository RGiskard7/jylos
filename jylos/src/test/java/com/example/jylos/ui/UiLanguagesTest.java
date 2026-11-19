package com.example.jylos.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The languages of the interface are the ones the app carries a {@code messages_<code>} file
 * for, found where the app runs from (folders when developing, a JAR when installed): adding a
 * language is adding its file. Each one is named in its own language, so it is found whatever
 * the language the interface is in.
 */
class UiLanguagesTest {

    @Test
    void theAppsLanguagesAreTheOnesItHasAFileFor() {
        assertEquals(List.of("en", "es"), UiLanguages.available());
    }

    @Test
    void aLanguageIsAddedByAddingItsFileAlsoInsideAJar(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("app.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream entries = new JarOutputStream(out)) {
            for (String name : List.of("messages.properties", "messages_en.properties", "messages_ru.properties",
                    "other_fr.properties")) {
                entries.putNextEntry(new JarEntry("com/example/jylos/i18n/" + name));
                entries.write("k=v\n".getBytes(StandardCharsets.UTF_8));
                entries.closeEntry();
            }
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            assertEquals(List.of("en", "ru"), UiLanguages.available(loader));
        }
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            assertEquals(List.of("en"), UiLanguages.available(empty), "with no file, English");
        }
    }

    @Test
    void eachLanguageIsNamedInItsOwnLanguage() {
        assertEquals(List.of("English", "Español", "Русский", "Français"),
                List.of(UiLanguages.nameOf("en"), UiLanguages.nameOf("es"), UiLanguages.nameOf("ru"),
                        UiLanguages.nameOf("fr")));
    }
}
