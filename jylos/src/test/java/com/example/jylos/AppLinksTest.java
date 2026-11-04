package com.example.jylos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AppLinksTest {

    @Test
    void describesTheRuntimeForABugReport() {
        Map<String, String> props = Map.of(
                "java.version", "21.0.11", "java.vendor", "Eclipse Adoptium",
                "javafx.runtime.version", "23.0.2+3",
                "os.name", "Windows 11", "os.version", "10.0", "os.arch", "amd64");

        assertEquals(List.of("Jylos 2.5.8", "Java 21.0.11 (Eclipse Adoptium)", "JavaFX 23.0.2+3",
                "Windows 11 10.0 (amd64)"), AppLinks.runtimeDetails("2.5.8", props::get));
    }

    @Test
    void missingPropertiesAreShownAsUnknownInsteadOfNull() {
        assertEquals(List.of("Jylos 2.5.8", "Java ?", "JavaFX ?", "?"),
                AppLinks.runtimeDetails("2.5.8", key -> null));
    }

    @Test
    void linksPointAtTheProject() {
        assertEquals("https://github.com/RGiskard7/jylos/issues", AppLinks.ISSUES);
        assertEquals("https://github.com/RGiskard7/jylos/blob/main/LICENSE", AppLinks.LICENSE);
    }
}
