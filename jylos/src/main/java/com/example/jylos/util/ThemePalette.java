package com.example.jylos.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.config.LoggerConfig;

import javafx.scene.paint.Color;

/**
 * The colours of a theme, as {@code #rrggbb}, for what JavaFX's stylesheets do not reach: the
 * editor, the reading view and the graph. They are the variables the theme gives {@code .root}
 * ({@code -fx-base-bg}, {@code -fx-text-main}, {@code -fx-accent}...), the later stylesheet
 * winning, as in the window.
 *
 * @param background {@code -fx-base-bg}
 * @param surface    {@code -fx-sidebar-bg}: panels, code, quotes
 * @param header     {@code -fx-header-bg}
 * @param text       {@code -fx-text-main}
 * @param muted      {@code -fx-text-muted}
 * @param faint      {@code -fx-text-faint}
 * @param border     {@code -fx-fn-border-color}
 * @param hover      {@code -fx-hover-bg}
 * @param accent     {@code -fx-accent}
 * @param selection  {@code -fx-selected-bg}
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public record ThemePalette(String background, String surface, String header, String text, String muted,
        String faint, String border, String hover, String accent, String selection) {

    private static final Logger logger = LoggerConfig.getLogger(ThemePalette.class);
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern BLOCK = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern DECLARATION = Pattern.compile("(-fx-[a-z0-9-]+)\\s*:\\s*([^;]+);?");
    private static final Pattern VARIABLE = Pattern.compile("-fx-[a-z0-9-]+");

    /**
     * The palette of these stylesheets, in the order the window stacks them, or {@code null} if
     * they do not give a background and a text colour.
     */
    public static ThemePalette fromStylesheets(List<String> stylesheets) {
        Map<String, String> colours = new HashMap<>();
        for (String css : stylesheets) {
            Map<String, String> declared = rootDeclarations(css);
            for (Map.Entry<String, String> entry : declared.entrySet()) {
                String colour = colour(entry.getValue(), declared, colours, 0);
                if (colour != null) {
                    colours.put(entry.getKey(), colour);
                }
            }
        }
        String background = colours.get("-fx-base-bg");
        String text = colours.get("-fx-text-main");
        if (background == null || text == null) {
            return null;
        }
        String muted = colours.getOrDefault("-fx-text-muted", text);
        String border = colours.getOrDefault("-fx-fn-border-color", muted);
        String accent = colours.getOrDefault("-fx-accent", text);
        return new ThemePalette(background,
                colours.getOrDefault("-fx-sidebar-bg", background),
                colours.getOrDefault("-fx-header-bg", background),
                text, muted,
                colours.getOrDefault("-fx-text-faint", muted),
                border,
                colours.getOrDefault("-fx-hover-bg", background),
                accent,
                colours.getOrDefault("-fx-selected-bg", accent));
    }

    /** The palette of the stylesheets at these addresses; one that cannot be read is left out. */
    public static ThemePalette read(List<String> uris) {
        List<String> sheets = uris.stream().map(ThemePalette::readSheet).filter(s -> s != null).toList();
        return fromStylesheets(sheets);
    }

    private static String readSheet(String uri) {
        try (InputStream in = URI.create(uri).toURL().openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException e) {
            logger.warning("Could not read the theme's colours from " + uri + ": " + e.getMessage());
            return null;
        }
    }

    /** The variables declared in the blocks whose selectors include {@code .root}. */
    private static Map<String, String> rootDeclarations(String css) {
        Map<String, String> declared = new HashMap<>();
        Matcher block = BLOCK.matcher(COMMENT.matcher(css == null ? "" : css).replaceAll(""));
        while (block.find()) {
            boolean root = false;
            for (String selector : block.group(1).split(",")) {
                root |= selector.strip().equals(".root");
            }
            if (!root) {
                continue;
            }
            Matcher declaration = DECLARATION.matcher(block.group(2));
            while (declaration.find()) {
                declared.put(declaration.group(1), declaration.group(2).strip());
            }
        }
        return declared;
    }

    /**
     * The colour of a value: a colour, or a variable declared beside it or before; {@code null}
     * for anything else (a gradient, {@code derive(...)}).
     */
    private static String colour(String value, Map<String, String> declared, Map<String, String> before, int depth) {
        if (VARIABLE.matcher(value).matches()) {
            if (depth > 8) {
                return null;
            }
            String own = declared.get(value);
            return own != null ? colour(own, declared, before, depth + 1) : before.get(value);
        }
        try {
            Color c = Color.web(value);
            return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(c.getRed() * 255),
                    Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
