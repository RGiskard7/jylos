package com.example.jylos.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the system {@code git} CLI as a subprocess, serialized app-wide so two
 * invocations never contend for the index lock, and drains output on separate
 * threads to avoid pipe-buffer deadlocks.
 *
 * <p>This is the sole process-execution seam every {@code Git*Service} class
 * goes through; it owns no Git domain logic.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitProcessRunner {

    private static final Duration LOCAL_OPERATION_TIMEOUT = Duration.ofMinutes(2);

    /** Serializes Git subprocesses app-wide so two never contend for the index lock. */
    private static final Object GIT_LOCK = new Object();

    private final Object activeProcessLock = new Object();
    private volatile Process activeProcess;

    /** Runs {@code git -c core.quotePath=false <args>} in {@code dir}, bounded by a local-operation timeout. */
    Proc run(Path dir, String... args) {
        return run(dir, LOCAL_OPERATION_TIMEOUT, null, args);
    }

    /**
     * Runs a network operation without an artificial timeout. Transfers can legitimately
     * take longer than a fixed limit for a large vault or a slow connection; callers can
     * explicitly cancel through {@link #cancelActiveOperation()} instead.
     */
    Proc runRemote(Path dir, String... args) {
        return run(dir, null, null, args);
    }

    /** Runs a network operation and exposes Git's stderr progress output to the caller. */
    Proc runRemote(Path dir, Consumer<String> progressListener, String... args) {
        return run(dir, null, progressListener, args);
    }

    private Proc run(Path dir, Duration timeout, Consumer<String> progressListener, String... args) {
        synchronized (GIT_LOCK) {
            return runOnce(dir, timeout, progressListener, args);
        }
    }

    private Proc runOnce(Path dir, Duration timeout, Consumer<String> progressListener, String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-c");
        command.add("core.quotePath=false");
        for (String a : args) {
            command.add(a);
        }
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (dir != null) {
                pb.directory(dir.toFile());
            }
            // Force a stable C locale so git's messages are in English: GitOutputClassifier's
            // phrase matching (nothing-to-commit, conflict, rejected, auth, network) matches
            // English phrases and would otherwise misfire on localized systems.
            pb.environment().put("LC_ALL", "C");
            pb.environment().put("LANG", "C");
            process = pb.start();
            synchronized (activeProcessLock) {
                activeProcess = process;
            }
            CompletableFuture<String> out = readAsync(process.getInputStream(), null);
            CompletableFuture<String> err = readAsync(process.getErrorStream(), progressListener);
            boolean finished = timeout == null
                    ? waitForCompletion(process)
                    : process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                terminateProcessTree(process);
                return new Proc(-1, "", "git timed out");
            }
            return new Proc(process.exitValue(), out.get(), err.get());
        } catch (IOException e) {
            return new Proc(-1, "", "Failed to run git: " + e.getMessage());
        } catch (InterruptedException e) {
            if (process != null) {
                terminateProcessTree(process);
            }
            Thread.currentThread().interrupt();
            return new Proc(-1, "", "git interrupted");
        } catch (Exception e) {
            return new Proc(-1, "", "git error: " + e.getMessage());
        } finally {
            synchronized (activeProcessLock) {
                if (activeProcess == process) {
                    activeProcess = null;
                }
            }
        }
    }

    /**
     * Cancels the currently running Git command, including its helper processes such as
     * {@code ssh} and {@code git pack-objects}. This prevents a cancelled transfer from
     * continuing in the background after its parent process exits.
     *
     * @return {@code true} when there was an active Git command to cancel
     */
    boolean cancelActiveOperation() {
        Process process;
        synchronized (activeProcessLock) {
            process = activeProcess;
        }
        if (process == null || !process.isAlive()) {
            return false;
        }
        terminateProcessTree(process);
        return true;
    }

    /** Waits indefinitely for a user-cancellable remote process to finish. */
    private static boolean waitForCompletion(Process process) throws InterruptedException {
        process.waitFor();
        return true;
    }

    /** Terminates a Git process and every helper process it started. */
    private static void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /** Drains an output stream and optionally forwards chunks to a progress listener. */
    private static CompletableFuture<String> readAsync(InputStream in, Consumer<String> progressListener) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = in) {
                StringBuilder output = new StringBuilder();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
                    output.append(chunk);
                    if (progressListener != null) {
                        progressListener.accept(chunk);
                    }
                }
                return output.toString();
            } catch (IOException e) {
                return "";
            }
        });
    }
}
