package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class UiControllersLoggingQualityGuardTest {

    private static final List<Path> CONTROLLERS = List.of(
            Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java"),
            Path.of("src/main/java/com/example/jylos/ui/controller/SidebarController.java"),
            Path.of("src/main/java/com/example/jylos/ui/controller/NotesListController.java"));

    @Test
    void controllersShouldAvoidStringConcatenatedExceptionLogging() throws IOException {
        Pattern weakPattern = Pattern.compile("logger\\.(severe|warning)\\(\"[^\"]*\"\\s*\\+\\s*e\\.getMessage\\(\\)\\)");
        for (Path controller : CONTROLLERS) {
            String source = Files.readString(controller, StandardCharsets.UTF_8);
            assertFalse(weakPattern.matcher(source).find(),
                    "Found weak exception logging in " + controller + ". Prefer logger.log(Level, message, e).");
        }
    }
}
