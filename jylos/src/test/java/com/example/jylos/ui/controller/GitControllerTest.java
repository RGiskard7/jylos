package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.tests.FxTestSupport;

/**
 * Smoke-tests {@link GitController}'s public actions end-to-end against a real
 * temporary Git repository (mirroring {@code GitServiceTest}'s style, since
 * {@code GitService} is a concrete final class with no injectable seam — a real
 * repo is more representative than a mock here anyway). {@code init()}/{@code sync()}
 * run through an async {@link javafx.concurrent.Task}, so tests wait on the status
 * callback rather than asserting synchronously.
 */
class GitControllerTest {

    private Preferences prefs;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable(), "JavaFX runtime not available");
        prefs = Preferences.userRoot().node("jylos-test-git-controller-" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (prefs != null) {
            prefs.removeNode();
        }
    }

    private GitController controllerFor(Path vault, List<String> statusMessages) {
        prefs.put("storage_type", "filesystem");
        prefs.put("filesystem_path", vault.toAbsolutePath().toString());

        GitController controller = new GitController();
        controller.wire(null, null, null, null, null, null, null, null,
                prefs, key -> key, statusMessages::add, () -> null, () -> {
                });
        return controller;
    }

    /** Blocks until at least {@code count} status messages have arrived, or fails the test. */
    private void awaitStatusCount(List<String> statusMessages, int count) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            while (statusMessages.size() < count) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            latch.countDown();
        });
        waiter.setDaemon(true);
        waiter.start();
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Timed out waiting for " + count + " status message(s); got: " + statusMessages);
    }

    @Test
    void initShouldCreateAGitRepositoryInTheVault(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("note.md"), "# Hello");
        List<String> statusMessages = new CopyOnWriteArrayList<>();
        GitController controller = controllerFor(vault, statusMessages);

        controller.init();
        awaitStatusCount(statusMessages, 2);

        assertTrue(Files.isDirectory(vault.resolve(".git")), "init() should create a .git directory in the vault");
        assertEquals("status.git_initializing", statusMessages.get(0));
    }

    @Test
    void syncWithNoRemoteShouldCommitLocallyAndReportNoRemote(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("note.md"), "# Hello");
        List<String> statusMessages = new CopyOnWriteArrayList<>();
        GitController controller = controllerFor(vault, statusMessages);

        controller.init();
        awaitStatusCount(statusMessages, 2);
        statusMessages.clear();

        Files.writeString(vault.resolve("note.md"), "# Hello again");
        runGit(vault, "add", "-A", ".");

        controller.sync();
        awaitStatusCount(statusMessages, 2);

        assertEquals("status.git_syncing", statusMessages.get(0));
        // No remote configured: sync() commits locally and reports that state via
        // GitResult.Status.NO_REMOTE, mapped by describeGitResult() — not an error.
        assertEquals("status.git_no_remote", statusMessages.get(1));
    }

    private static void runGit(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        process.waitFor(10, TimeUnit.SECONDS);
    }
}
