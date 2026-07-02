package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ServiceLayerDependencyGuardTest {

    private static final Path SERVICE_DIR = Path.of("src/main/java/com/example/jylos/service");

    @Test
    void servicesShouldStayIndependentFromPresentationAndGlobalServiceLookups() throws IOException {
        try (Stream<Path> files = Files.list(SERVICE_DIR)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("import javafx."),
                        "Service layer must not depend on JavaFX UI types: " + file);
                assertFalse(source.contains("import com.example.jylos.ui."),
                        "Service layer must not depend on UI packages: " + file);
                if ("package-info.java".equals(file.getFileName().toString())) {
                    continue;
                }
                assertFalse(source.contains("import com.example.jylos.config.AppContext")
                                || source.contains("AppContext."),
                        "Service layer should not use AppContext as a hidden dependency: " + file);
                assertFalse(source.contains("EventBus.getInstance("),
                        "Service layer should receive EventBus explicitly instead of global lookup: " + file);
            }
        }
    }
}
