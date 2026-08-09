package com.example.jylos.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;

/**
 * Starts the JavaFX runtime once for tests that need real UI controls.
 *
 * <p>CI runners can be slow while JavaFX extracts and locks native libraries.
 * This helper avoids interrupting {@link Platform#startup(Runnable)} directly,
 * because interrupting that native load can leave the toolkit in a bad state.
 */
public final class FxTestSupport {

    private static final int STARTUP_TIMEOUT_SECONDS = 30;

    private static volatile Boolean available;

    private FxTestSupport() {
    }

    public static boolean isFxRuntimeAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }

        synchronized (FxTestSupport.class) {
            if (available == null) {
                available = startFxRuntime();
            }
            return available;
        }
    }

    private static boolean startFxRuntime() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean startupFailed = new AtomicBoolean(false);

        Thread startupThread = new Thread(() -> {
            try {
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    latch.countDown();
                });
            } catch (IllegalStateException alreadyStarted) {
                try {
                    Platform.runLater(() -> {
                        Platform.setImplicitExit(false);
                        latch.countDown();
                    });
                } catch (RuntimeException stoppedToolkit) {
                    startupFailed.set(true);
                    latch.countDown();
                }
            } catch (RuntimeException error) {
                startupFailed.set(true);
                latch.countDown();
            }
        }, "jylos-test-javafx-startup");
        startupThread.setDaemon(true);
        startupThread.start();

        try {
            return latch.await(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS) && !startupFailed.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
