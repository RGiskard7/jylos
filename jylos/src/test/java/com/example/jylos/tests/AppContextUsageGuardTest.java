package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class AppContextUsageGuardTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java/com/example/jylos");
    private static final Pattern APP_CONTEXT_ACCESS = Pattern.compile("AppContext\\.(get|isInitialized)\\(");
    private static final Set<Path> ALLOWED = Set.of();

    @Test
    void appContextGetsShouldStayLimitedToExplicitExceptions() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (APP_CONTEXT_ACCESS.matcher(source).find()) {
                    assertTrue(ALLOWED.contains(file),
                            "Unexpected AppContext getter usage outside the approved exceptions: " + file);
                }
            }
        }
    }
}
