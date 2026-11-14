package com.example.jylos.tests;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    /** Generous: a few tests run hundreds of graph simulation frames in one call. */
    private static final int FX_WORK_TIMEOUT_SECONDS = 120;

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

    /** Work for the FX thread that returns nothing and may throw. */
    @FunctionalInterface
    public interface FxWork {
        void run() throws Exception;
    }

    /**
     * Runs {@code work} on the JavaFX Application Thread and returns its result. A failure in
     * it is thrown here as an {@link AssertionError} wrapping it; on the FX thread already, it
     * runs right away.
     */
    public static <T> T onFx(Callable<T> work) throws Exception {
        // Started here too: a test class run on its own may not have started it.
        if (!isFxRuntimeAvailable()) {
            throw new AssertionError("the JavaFX runtime could not be started");
        }
        if (Platform.isFxApplicationThread()) {
            return work.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(FX_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("never completed on the FX thread");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    /** {@link #onFx} for work that returns nothing. */
    public static void runFx(FxWork work) throws Exception {
        onFx(() -> {
            work.run();
            return null;
        });
    }
}
