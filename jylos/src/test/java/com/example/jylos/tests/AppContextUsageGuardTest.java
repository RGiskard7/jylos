package com.example.jylos.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppContextUsageGuardTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java/com/example/jylos");
    private static final Path APP_CONTEXT = Path.of("src/main/java/com/example/jylos/config/AppContext.java");

    @Test
    void appContextClassShouldBeGone() {
        assertFalse(Files.exists(APP_CONTEXT), "AppContext should be removed once explicit wiring owns bootstrap.");
    }

    @Test
    void productionCodeShouldNotReferenceRemovedArchitectureResidues() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("AppContext."),
                        "Production code should not reference AppContext anymore: " + file);
                assertFalse(source.contains("UIEvents"),
                        "Production code should not keep residual UIEvents references: " + file);
                assertFalse(source.contains("NoteOpenRequestEvent"),
                        "Production code should not keep residual note-open request events: " + file);
                assertFalse(source.contains("NoteModifiedEvent"),
                        "Production code should not keep residual note-modified events: " + file);
            }
        }
    }

    @Test
    void architectureDocsShouldNotRecommendRemovedAppContextModel() throws IOException {
        Path docDir = Files.exists(Path.of("doc")) ? Path.of("doc") : Path.of("..", "doc");
        try (Stream<Path> files = Files.walk(docDir)) {
            boolean foundRecommendedAppContextUsage = false;
            for (Path file : files.filter(path -> path.toString().endsWith(".md")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("AppContext"),
                        "Architecture docs should not mention the removed AppContext model: " + file);
                foundRecommendedAppContextUsage = true;
            }
            assertTrue(foundRecommendedAppContextUsage, "Expected architecture documentation files to be present.");
        }
    }
}
