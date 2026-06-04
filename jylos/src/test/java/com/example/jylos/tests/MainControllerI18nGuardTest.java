package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class MainControllerI18nGuardTest {

    private static final Path MAIN_CONTROLLER_PATH = Path.of(
            "src/main/java/com/example/jylos/ui/controller/MainController.java");

    @Test
    void shouldNotUseHardcodedDialogStringsInMainController() throws IOException {
        String source = Files.readString(MAIN_CONTROLLER_PATH, StandardCharsets.UTF_8);

        assertFalse(hasMatch(source, "setTitle\\(\"[^\"]+\"\\)"),
                "Found hardcoded dialog title. Use getString(\"...\") instead.");
        assertFalse(hasMatch(source, "setHeaderText\\(\"[^\"]+\"\\)"),
                "Found hardcoded dialog header. Use getString(\"...\") instead.");
        assertFalse(hasMatch(source, "setContentText\\(\"[^\"]+\"\\)"),
                "Found hardcoded dialog content. Use getString(\"...\") instead.");
        assertFalse(hasMatch(source, "new ButtonType\\(\"[^\"]+\""),
                "Found hardcoded ButtonType label. Use getString(\"...\") instead.");
    }

    private boolean hasMatch(String source, String regex) {
        return Pattern.compile(regex).matcher(source).find();
    }
}
