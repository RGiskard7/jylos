package com.example.jylos.ui;

import java.io.InputStream;
import java.util.Optional;

import com.example.jylos.AppConfig;

import javafx.scene.image.Image;

/**
 * Loads the application window icon from {@link AppConfig#getWindowIconPath()}.
 */
public final class AppIconLoader {

    private AppIconLoader() {
    }

    /** Natural-size icon (e.g. stage / dialog chrome). */
    public static Optional<Image> load() {
        return load(0);
    }

    /**
     * @param displaySize max width and height in pixels; {@code 0} keeps natural size
     */
    public static Optional<Image> load(double displaySize) {
        String path = AppConfig.getWindowIconPath();
        try (InputStream in = AppIconLoader.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                return Optional.empty();
            }
            if (displaySize > 0) {
                return Optional.of(new Image(in, displaySize, displaySize, true, true));
            }
            return Optional.of(new Image(in));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
