package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DataLayerDependencyGuardTest {

    private static final Path DATA_DIR = Path.of("src/main/java/com/example/jylos/data");

    @Test
    void dataLayerShouldNotImportUiPackages() throws IOException {
        try (Stream<Path> files = Files.walk(DATA_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("import com.example.jylos.ui."),
                        "Data layer must not depend on UI packages: " + file);
            }
        }
    }
}
