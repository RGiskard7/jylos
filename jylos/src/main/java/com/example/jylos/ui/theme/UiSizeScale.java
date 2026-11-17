package com.example.jylos.ui.theme;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.config.LoggerConfig;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;

/**
 * Rewrites the rules of a stylesheet that size icons and controls in fixed pixels, for an
 * interface font other than the default 13 px.
 *
 * <p>Ikonli icons cannot be sized relative to the font ({@code -fx-icon-size} is a whole number
 * of pixels, and an icon sets its own font size), and buttons, tabs and fields are sized in
 * pixels too. So the sizes are recomputed here: every icon size, every width or height of a
 * control's size (16 to 48 px; panel widths and small details are left alone) and every tree
 * level's indent, comes out
 * multiplied, under the same selector, in the same order. Added after the theme, these rules
 * win over it and leave everything else as the theme says.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class UiSizeScale {

    /** Interface font the stylesheets are written for. */
    public static final double DEFAULT_FONT_PX = 13.0;

    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern ICON_SIZE = Pattern.compile("-fx-icon-size\\s*:\\s*(\\d+(?:\\.\\d+)?)(px)?\\s*;");
    private static final Pattern DIMENSION = Pattern.compile(
            "(-fx-(?:pref|min|max)-(?:width|height))\\s*:\\s*(\\d+(?:\\.\\d+)?)px\\s*;");
    private static final Pattern INDENT = Pattern.compile("-fx-indent\\s*:\\s*(\\d+(?:\\.\\d+)?)px\\s*;");
    private static final double MIN_CONTROL_PX = 16;
    private static final double MAX_CONTROL_PX = 48;

    private static final Logger logger = LoggerConfig.getLogger(UiSizeScale.class);
    private static final String FACTORS = "jylos.uiSizeScale.factors";
    private static final String SHEET = "jylos.uiSizeScale.sheet";
    private static final String UPDATING = "jylos.uiSizeScale.updating";
    private static final String PENDING = "jylos.uiSizeScale.pending";
    private static final Map<String, String> SHEET_TEXT = new ConcurrentHashMap<>();

    private UiSizeScale() {
    }

    /**
     * Sizes the scene's icons and controls for an interface font of {@code fontPx}, with icons
     * further multiplied by {@code iconFactor}: a stylesheet of the rescaled rules goes last in
     * the scene, and is rebuilt whenever the scene's stylesheets change (a new theme or
     * snippet), so it always reflects them and stays last. At 13 px and factor 1 there is none.
     */
    public static void apply(Scene scene, double fontPx, double iconFactor) {
        if (scene == null) {
            return;
        }
        scene.getProperties().put(FACTORS, new double[] {fontPx / DEFAULT_FONT_PX, iconFactor});
        if (!scene.getProperties().containsKey(SHEET)) {
            scene.getProperties().put(SHEET, "");
            // JavaFX does not allow changing the list from inside its own change event: the
            // sheet is put back right after, once the change (a theme switch, a snippet) is done.
            scene.getStylesheets().addListener((ListChangeListener<String>) change -> refreshSoon(scene));
        }
        refresh(scene);
    }

    private static void refreshSoon(Scene scene) {
        if (Boolean.TRUE.equals(scene.getProperties().get(UPDATING))
                || Boolean.TRUE.equals(scene.getProperties().put(PENDING, true))) {
            return;
        }
        Platform.runLater(() -> {
            scene.getProperties().put(PENDING, false);
            refresh(scene);
        });
    }

    private static void refresh(Scene scene) {
        if (Boolean.TRUE.equals(scene.getProperties().get(UPDATING))) {
            return;
        }
        double[] factors = (double[]) scene.getProperties().get(FACTORS);
        String previous = (String) scene.getProperties().get(SHEET);
        List<String> sheets = scene.getStylesheets();
        StringBuilder css = new StringBuilder();
        if (factors[0] != 1.0 || factors[1] != 1.0) {
            for (String url : sheets) {
                if (!url.equals(previous)) {
                    css.append(scaledRules(textOf(url), factors[0], factors[1]));
                }
            }
        }
        String sheet = css.isEmpty() ? "" : "data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.toString().getBytes(StandardCharsets.UTF_8));
        boolean last = !sheets.isEmpty() && sheets.get(sheets.size() - 1).equals(sheet);
        if (sheet.equals(previous) && (sheet.isEmpty() || last)) {
            return;
        }
        scene.getProperties().put(UPDATING, true);
        try {
            if (!previous.isEmpty()) {
                sheets.remove(previous);
            }
            if (!sheet.isEmpty()) {
                sheets.add(sheet);
            }
            scene.getProperties().put(SHEET, sheet);
        } finally {
            scene.getProperties().put(UPDATING, false);
        }
    }

    /** A stylesheet's text, read once per address; empty if it cannot be read. */
    private static String textOf(String url) {
        if (url.startsWith("data:")) {
            return "";
        }
        return SHEET_TEXT.computeIfAbsent(url, u -> {
            try (InputStream in = URI.create(u).toURL().openStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException | IllegalArgumentException e) {
                logger.warning("Could not read stylesheet " + u + " to scale its sizes: " + e.getMessage());
                return "";
            }
        });
    }

    /**
     * The rules of {@code css} that size icons or controls, rescaled: icons by
     * {@code fontFactor × iconFactor}, control sizes by the larger of {@code fontFactor} and
     * that product, so an enlarged icon still fits its button. One rule per line.
     */
    public static String scaledRules(String css, double fontFactor, double iconFactor) {
        double iconScale = fontFactor * iconFactor;
        double controlScale = Math.max(fontFactor, iconScale);
        StringBuilder out = new StringBuilder();
        Matcher rule = RULE.matcher(COMMENT.matcher(css).replaceAll(""));
        while (rule.find()) {
            String body = rule.group(2);
            List<String> declarations = new ArrayList<>();
            Matcher icon = ICON_SIZE.matcher(body);
            while (icon.find()) {
                declarations.add("-fx-icon-size: " + px(Double.parseDouble(icon.group(1)), iconScale) + ";");
            }
            Matcher dimension = DIMENSION.matcher(body);
            while (dimension.find()) {
                double value = Double.parseDouble(dimension.group(2));
                if (value >= MIN_CONTROL_PX && value <= MAX_CONTROL_PX) {
                    declarations.add(dimension.group(1) + ": " + px(value, controlScale) + ";");
                }
            }
            Matcher indent = INDENT.matcher(body);
            while (indent.find()) {
                declarations.add("-fx-indent: " + px(Double.parseDouble(indent.group(1)), controlScale) + ";");
            }
            if (!declarations.isEmpty()) {
                String selector = rule.group(1).trim().replaceAll("\\s+", " ");
                out.append(selector).append(" { ").append(String.join(" ", declarations)).append(" }\n");
            }
        }
        return out.toString();
    }

    private static String px(double value, double scale) {
        return Math.round(value * scale) + "px";
    }
}
