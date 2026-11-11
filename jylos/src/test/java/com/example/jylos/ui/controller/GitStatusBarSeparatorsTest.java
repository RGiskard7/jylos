package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.git.GitStatus;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;

/**
 * A folder vault that is not a Git repository shows only "Initialize Git" in the status
 * bar. The separators between the hidden Git entries must hide with them, or the bar shows
 * a row of stray "| | | |".
 */
class GitStatusBarSeparatorsTest {

    @Test
    void separatorsBetweenGitEntriesFollowWhetherTheVaultIsARepository() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Long> visibleSeparators = onFx(() -> {
            Label init = new Label();
            Label remote = new Label();
            Label changes = new Label();
            Label commit = new Label();
            Label sync = new Label();
            Label history = new Label();
            // Same order as MainView.fxml's gitBar.
            HBox bar = new HBox(init, remote, new Separator(), changes, new Separator(), commit, new Separator(),
                    sync, new Separator(), history);
            GitController git = new GitController();
            git.wire(new Separator(), bar, init, remote, changes, commit, sync, history, null, key -> key,
                    message -> { }, () -> null, () -> { });
            Method apply = GitController.class.getDeclaredMethod("applyGitStatus", GitStatus.class);
            apply.setAccessible(true);

            apply.invoke(git, GitStatus.none());
            long withoutRepo = shownSeparators(bar);
            apply.invoke(git, new GitStatus(true, false, false, "", "main", 0, 0, 0));
            long withRepo = shownSeparators(bar);
            return List.of(withoutRepo, withRepo);
        });

        // A repository shows one compact entry (see the next test), so no separators either.
        assertEquals(List.of(0L, 0L), visibleSeparators);
    }

    /**
     * In a repository the Git part of the status bar is one entry: the branch with its sync
     * state and the number of changes. A click opens the Git panel (unchanged), and its
     * context menu keeps one-click access to changes, commit, history and adding a remote.
     */
    @Test
    void aRepositoryShowsOneCompactEntryWithTheOtherActionsInItsMenu() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Label init = new Label();
            Label remote = new Label();
            Label changes = new Label();
            Label commit = new Label();
            Label sync = new Label();
            Label history = new Label();
            HBox bar = new HBox(init, remote, new Separator(), changes, new Separator(), commit, new Separator(),
                    sync, new Separator(), history);
            GitController git = new GitController();
            git.wire(new Separator(), bar, init, remote, changes, commit, sync, history, null, key -> key,
                    message -> { }, () -> null, () -> { });
            Method apply = GitController.class.getDeclaredMethod("applyGitStatus", GitStatus.class);
            apply.setAccessible(true);
            apply.invoke(git, new GitStatus(true, false, false, "", "master", 107, 0, 0));

            List<String> out = new ArrayList<>();
            for (javafx.scene.Node node : bar.getChildren()) {
                if (node.isVisible() && node.isManaged()) {
                    out.add(node instanceof Label l ? l.getText() : "|");
                }
            }
            out.add("tooltip: " + (sync.getTooltip() != null ? sync.getTooltip().getText().replace("\n", " / ") : ""));
            for (javafx.scene.control.MenuItem item : sync.getContextMenu().getItems()) {
                out.add("menu: " + item.getText());
            }
            return out;
        });

        assertEquals(List.of("master  ✓ · 107", "tooltip: git.no_remote / git.changes / git.tooltip_compact",
                "menu: git.changes", "menu: git.commit", "menu: git.history", "menu: action.git_add_remote"), seen);
    }

    private static long shownSeparators(HBox bar) {
        return bar.getChildren().stream().filter(n -> n instanceof Separator).filter(Node::isVisible)
                .filter(Node::isManaged).count();
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
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
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
