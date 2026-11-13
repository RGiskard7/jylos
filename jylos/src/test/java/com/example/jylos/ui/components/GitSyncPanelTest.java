package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.git.GitChange;
import com.example.jylos.git.GitService;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.control.Button;
import javafx.scene.control.ListView;

/** Verifies that staging actions refresh the visible Git change list. */
class GitSyncPanelTest {

    private static boolean fxRuntimeAvailable;

    @BeforeAll
    static void initFxRuntime() {
        fxRuntimeAvailable = FxTestSupport.isFxRuntimeAvailable();
    }

    @Test
    void stageAndUnstageAllRefreshTheVisibleChangeState(@TempDir Path vault) throws Exception {
        assumeTrue(fxRuntimeAvailable, "JavaFX runtime unavailable");
        GitService git = new GitService();
        assumeTrue(git.isGitAvailable(), "git unavailable");

        Files.writeString(vault.resolve("note.md"), "base\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());
        Files.writeString(vault.resolve("note.md"), "changed\n", StandardCharsets.UTF_8);

        GitSyncPanel panel = onFx(() -> new GitSyncPanel(git, vault, key -> key, null, () -> { }));
        refresh(panel);
        assertTrue(awaitFx(() -> !visibleChanges(panel).isEmpty()), "unstaged change should be visible");
        assertTrue(visibleChanges(panel).stream().noneMatch(GitChange::staged));

        onFx(() -> {
            button(panel, "stageAllBtn").fire();
            return null;
        });
        assertTrue(awaitFx(() -> !visibleChanges(panel).isEmpty()
                && visibleChanges(panel).stream().allMatch(GitChange::staged)),
                "stage all must refresh rows as staged");

        onFx(() -> {
            button(panel, "unstageAllBtn").fire();
            return null;
        });
        assertTrue(awaitFx(() -> !visibleChanges(panel).isEmpty()
                && visibleChanges(panel).stream().noneMatch(GitChange::staged)),
                "unstage all must refresh rows as unstaged");
    }

    @Test
    void refreshShowsBothStatesWhenAStagedFileChangesAgain(@TempDir Path vault) throws Exception {
        assumeTrue(fxRuntimeAvailable, "JavaFX runtime unavailable");
        GitService git = new GitService();
        assumeTrue(git.isGitAvailable(), "git unavailable");

        Path note = vault.resolve("note.md");
        Files.writeString(note, "base\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());
        Files.writeString(note, "staged edit\n", StandardCharsets.UTF_8);
        assertTrue(git.stageAll(vault).ok());
        Files.writeString(note, "staged edit\nnew edit\n", StandardCharsets.UTF_8);

        GitSyncPanel panel = onFx(() -> new GitSyncPanel(git, vault, key -> key, null, () -> { }));
        refresh(panel);

        assertTrue(awaitFx(() -> visibleChanges(panel).size() == 2),
                "a staged file changed again must produce two visible rows");
        assertTrue(visibleChanges(panel).stream().anyMatch(GitChange::staged));
        assertTrue(visibleChanges(panel).stream().anyMatch(change -> !change.staged()));
    }

    private static void refresh(GitSyncPanel panel) throws Exception {
        Method method = GitSyncPanel.class.getDeclaredMethod("refresh", boolean.class);
        method.setAccessible(true);
        onFx(() -> {
            try {
                method.invoke(panel, false);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static List<GitChange> visibleChanges(GitSyncPanel panel) {
        try {
            Field field = GitSyncPanel.class.getDeclaredField("changesList");
            field.setAccessible(true);
            ListView<GitChange> list = (ListView<GitChange>) field.get(panel);
            return List.copyOf(list.getItems());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Button button(GitSyncPanel panel, String name) {
        try {
            Field field = GitSyncPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Button) field.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean awaitFx(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                return true;
            }
            Thread.sleep(25);
        }
        return onFx(condition::getAsBoolean);
    }

}
