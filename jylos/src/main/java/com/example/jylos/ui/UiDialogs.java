package com.example.jylos.ui;

import java.util.List;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

/**
 * Applies the active application theme to JavaFX dialogs and alerts.
 *
 * <p>JavaFX dialogs do <em>not</em> inherit their owner scene's stylesheets, so
 * without this they render with the default (light) look — illegible over a dark
 * theme. The active theme stylesheets are registered once (and refreshed on theme
 * changes) via {@link #setStylesheets(List)}, then applied to each dialog with
 * {@link #apply(Dialog)} / {@link #apply(DialogPane)}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public final class UiDialogs {

    private static volatile List<String> stylesheets = List.of();

    private UiDialogs() {
    }

    /** Registers the active theme stylesheets (called by the shell on theme changes). */
    public static void setStylesheets(List<String> sheets) {
        stylesheets = sheets != null ? List.copyOf(sheets) : List.of();
    }

    /** Themes a dialog so it matches the current light/dark theme. */
    public static void apply(Dialog<?> dialog) {
        if (dialog != null) {
            apply(dialog.getDialogPane());
        }
    }

    /** Themes a dialog and shows it modally, returning its result. */
    public static <T> java.util.Optional<T> show(Dialog<T> dialog) {
        apply(dialog);
        return dialog.showAndWait();
    }

    /** Themes a dialog pane (also used for {@code Alert}s). */
    public static void apply(DialogPane pane) {
        if (pane == null) {
            return;
        }
        if (!stylesheets.isEmpty()) {
            pane.getStylesheets().setAll(stylesheets);
            // Combo-box / context-menu popups are separate windows that read their
            // stylesheets from the owning Scene, not from this node — so propagate the
            // theme to the scene too (now and whenever it changes), or those popups
            // render with the default (light) look.
            applyToScene(pane.getScene());
            pane.sceneProperty().addListener((obs, old, scene) -> applyToScene(scene));
        }
        if (!pane.getStyleClass().contains("root-container")) {
            pane.getStyleClass().add("root-container");
        }
    }

    /**
     * Themes a whole {@link javafx.scene.Scene} (for custom {@code Stage}-based
     * windows such as the plugin manager). Adds the {@code root-container} style
     * class to the root so theme variables resolve.
     */
    public static void apply(javafx.scene.Scene scene) {
        if (scene == null) {
            return;
        }
        applyToScene(scene);
        if (scene.getRoot() != null && !scene.getRoot().getStyleClass().contains("root-container")) {
            scene.getRoot().getStyleClass().add("root-container");
        }
    }

    private static void applyToScene(javafx.scene.Scene scene) {
        if (scene != null && !stylesheets.isEmpty()) {
            scene.getStylesheets().setAll(stylesheets);
        }
    }
}
