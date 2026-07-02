package com.example.jylos.ui.theme;

import java.util.function.Supplier;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * Polls the OS appearance while the app is in built-in "system" theme mode.
 */
public final class SystemThemeMonitor {

    private static final Logger logger = LoggerConfig.getLogger(SystemThemeMonitor.class);
    private static final Duration POLL_INTERVAL = Duration.seconds(1.5);

    private final Supplier<String> detector;
    private final Runnable onOsThemeChange;
    private Timeline timeline;
    private String lastDetected = "";

    public SystemThemeMonitor(Supplier<String> detector, Runnable onOsThemeChange) {
        this.detector = detector;
        this.onOsThemeChange = onOsThemeChange;
    }

    public void setActive(boolean active) {
        stop();
        if (!active) {
            return;
        }
        lastDetected = detector.get();
        timeline = new Timeline(new KeyFrame(POLL_INTERVAL, event -> poll()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        logger.fine("System theme monitor started (detected=" + lastDetected + ")");
    }

    public void refreshOnFocus() {
        if (timeline == null) {
            return;
        }
        String detected = detector.get();
        if (!detected.equals(lastDetected)) {
            lastDetected = detected;
            logger.info("OS theme changed on focus (now " + detected + ")");
            Platform.runLater(onOsThemeChange);
        }
    }

    private void poll() {
        String detected = detector.get();
        if (detected.equals(lastDetected)) {
            return;
        }
        lastDetected = detected;
        logger.info("OS theme changed (now " + detected + ")");
        Platform.runLater(onOsThemeChange);
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}
