package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every theme of the repository can be read: its text stands out from what it is written on,
 * by the contrast ratio of WCAG 2, at level AA. Normal and muted text on the background, and
 * text on the sidebar, the selection, a hover, a control and a tooltip, reach 4.5; faint text,
 * the accent on the background and the text on an accent button 3, as for large text and parts
 * of the interface.
 */
class ThemeContrastTest {

    private static final Pattern COLOUR = Pattern.compile("-fx-([\\w-]+):\\s*(#[0-9a-fA-F]{6})\\s*;");

    /** foreground, background, minimum ratio. */
    private static final List<Object[]> PAIRS = List.of(
            new Object[] {"text-main", "base-bg", 4.5},
            new Object[] {"text-main", "sidebar-bg", 4.5},
            new Object[] {"text-muted", "base-bg", 4.5},
            new Object[] {"text-faint", "base-bg", 3.0},
            new Object[] {"selected-text", "selected-bg", 4.5},
            new Object[] {"hover-text", "hover-bg", 4.5},
            new Object[] {"control-text", "control-bg", 4.5},
            new Object[] {"tooltip-text", "tooltip-bg", 4.5},
            new Object[] {"accent", "base-bg", 3.0},
            new Object[] {"accent-contrast", "accent", 3.0});

    @Test
    void everyThemeOfTheRepositoryCanBeRead() throws IOException {
        List<String> checked = new ArrayList<>();
        List<String> tooLow = new ArrayList<>();
        try (Stream<Path> themes = Files.list(Path.of("../themes"))) {
            for (Path dir : themes.filter(Files::isDirectory).sorted().toList()) {
                Path css = dir.resolve("theme.css");
                if (dir.getFileName().toString().startsWith("_") || !Files.isRegularFile(css)) {
                    continue;
                }
                Map<String, String> colours = new HashMap<>();
                Matcher m = COLOUR.matcher(Files.readString(css, StandardCharsets.UTF_8));
                while (m.find()) {
                    colours.putIfAbsent(m.group(1), m.group(2));
                }
                for (Object[] pair : PAIRS) {
                    String fg = colours.get((String) pair[0]);
                    String bg = colours.get((String) pair[1]);
                    if (fg == null || bg == null) {
                        continue; // a theme that does not set both keeps its base theme's
                    }
                    double ratio = contrast(fg, bg);
                    if (ratio < (double) pair[2]) {
                        tooLow.add(String.format("%s %s %s on %s %s: %.2f < %.1f", dir.getFileName(), pair[0], fg,
                                pair[1], bg, ratio, (double) pair[2]));
                    }
                }
                checked.add(dir.getFileName().toString());
            }
        }
        assertFalse(checked.isEmpty(), "no theme was found to check");
        assertEquals(List.of(), tooLow, "checked: " + checked);
    }

    private static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] rgb = new double[3];
        for (int i = 0; i < 3; i++) {
            double c = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            rgb[i] = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }
}
