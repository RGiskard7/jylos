package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class AllNotesContractGuardTest {

    private static final List<Path> CONTROLLERS = List.of(
            Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java"),
            Path.of("src/main/java/com/example/jylos/ui/controller/NotesListController.java"),
            Path.of("src/main/java/com/example/jylos/ui/controller/SidebarController.java"));

    @Test
    void shouldNotDependOnAllNotesLiteralForBehavior() throws IOException {
        Pattern forbidden = Pattern.compile("equals\\(\"All Notes\"\\)");
        for (Path controller : CONTROLLERS) {
            String source = Files.readString(controller, StandardCharsets.UTF_8);
            assertFalse(forbidden.matcher(source).find(),
                    "Found forbidden behavioral literal in " + controller
                            + ". Use ALL_NOTES_VIRTUAL id contract instead.");
        }
    }
}
