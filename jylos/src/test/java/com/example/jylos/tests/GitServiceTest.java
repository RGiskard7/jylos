package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.git.GitChange;
import com.example.jylos.git.GitResult;
import com.example.jylos.git.GitService;
import com.example.jylos.git.GitStatus;

/**
 * Behavioral tests for {@link GitService} against a throwaway repository.
 *
 * <p>The whole class is skipped when {@code git} is not installed, so it never
 * fails on machines without Git (it exercises real Git, like Tolaria's tests).</p>
 */
class GitServiceTest {

    private final GitService git = new GitService();

    @BeforeEach
    void requireGit() {
        assumeTrue(git.isGitAvailable(), "git not installed — skipping Git integration tests");
    }

    @Test
    void initCreatesRepositoryWithCleanStatus(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("note.md"), "# Hello\n", StandardCharsets.UTF_8);

        assertFalse(git.isRepository(vault), "temp dir should not be a repo yet");
        GitResult init = git.init(vault);
        assertTrue(init.ok(), "init should succeed: " + init.message());

        assertTrue(git.isRepository(vault));
        assertTrue(Files.exists(vault.resolve(".gitignore")), "init should write .gitignore");

        GitStatus status = git.status(vault);
        assertTrue(status.repository());
        assertFalse(status.hasRemote());
        assertFalse(status.isDirty(), "everything committed by init");
        assertFalse(status.branch().isBlank(), "branch should be known");
    }

    @Test
    void statusReportsModifiedAndCommitClearsIt(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "one\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());

        Files.writeString(vault.resolve("a.md"), "one\ntwo\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("b.md"), "new\n", StandardCharsets.UTF_8);
        assertTrue(git.status(vault).modified() >= 2, "two files should be dirty");

        GitResult commit = git.commit(vault, "update");
        assertTrue(commit.ok(), commit.message());
        assertEquals(0, git.status(vault).modified(), "commit clears the working tree");
    }

    @Test
    void commitWithNothingToDoIsNotAnError(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "x\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());

        GitResult commit = git.commit(vault, "noop");
        assertEquals(GitResult.Status.NOTHING_TO_DO, commit.status());
        assertTrue(commit.ok());
    }

    @Test
    void pushWithoutRemoteReportsNoRemote(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "x\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());
        assertEquals(GitResult.Status.NO_REMOTE, git.push(vault).status());
    }

    @Test
    void commitRecoversFromStaleIndexLock(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "one\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());

        // Simulate a lock left by an interrupted/crashed git process.
        Path lock = vault.resolve(".git").resolve("index.lock");
        Files.writeString(lock, "", StandardCharsets.UTF_8);

        Files.writeString(vault.resolve("a.md"), "one\ntwo\n", StandardCharsets.UTF_8);
        GitResult commit = git.commit(vault, "update despite stale lock");
        assertTrue(commit.ok(), "commit should clear the stale lock and succeed: " + commit.message());
        assertFalse(Files.exists(lock), "stale index.lock should have been removed");
        assertEquals(0, git.status(vault).modified(), "commit clears the working tree");
    }

    @Test
    void listChangesIncludesAttachmentsAndTracksStaging(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Files.writeString(vault.resolve("note.md"), "# note\n", StandardCharsets.UTF_8);
        Files.write(vault.resolve("image.png"), new byte[] { 1, 2, 3, 4 });

        // Both new files are untracked → unstaged.
        var changes = git.listChanges(vault);
        assertTrue(changes.stream().anyMatch(c -> c.fileName().equals("note.md")), "note listed");
        assertTrue(changes.stream().anyMatch(c -> c.fileName().equals("image.png")), "attachment listed");
        assertTrue(changes.stream().noneMatch(com.example.jylos.git.GitChange::staged), "all unstaged initially");

        // Stage only the note → it becomes staged, the image stays unstaged.
        assertTrue(git.stage(vault, "note.md").ok());
        var staged = git.listChanges(vault);
        assertTrue(staged.stream().anyMatch(c -> c.fileName().equals("note.md") && c.staged()), "note staged");
        assertTrue(staged.stream().anyMatch(c -> c.fileName().equals("image.png") && !c.staged()), "image still unstaged");
    }

    @Test
    void stageAllAndUnstageAllToggleTheWholeIndex(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Files.writeString(vault.resolve("note.md"), "# new\n", StandardCharsets.UTF_8);
        Files.write(vault.resolve("pic.png"), new byte[] { 1, 2, 3 });

        assertTrue(git.stageAll(vault).ok());
        List<GitChange> staged = git.listChanges(vault);
        assertFalse(staged.isEmpty(), "there should be changes to stage");
        assertTrue(staged.stream().allMatch(GitChange::staged), "stageAll stages every change");

        assertTrue(git.unstageAll(vault).ok());
        List<GitChange> unstaged = git.listChanges(vault);
        assertTrue(unstaged.stream().noneMatch(GitChange::staged), "unstageAll clears the index");
    }

    @Test
    void listChangesFlagsMergeConflicts(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Files.writeString(vault.resolve("a.md"), "base\n", StandardCharsets.UTF_8);
        assertTrue(git.commit(vault, "base").ok());

        // Diverge two branches on the same line, then merge to force a conflict.
        runGit(vault, "checkout", "-b", "feature");
        Files.writeString(vault.resolve("a.md"), "feature side\n", StandardCharsets.UTF_8);
        assertTrue(git.commit(vault, "feature edit").ok());

        runGit(vault, "checkout", "-"); // back to the default branch
        Files.writeString(vault.resolve("a.md"), "main side\n", StandardCharsets.UTF_8);
        assertTrue(git.commit(vault, "main edit").ok());

        runGit(vault, "merge", "feature"); // conflicts on a.md (non-zero exit, ignored)

        List<GitChange> changes = git.listChanges(vault);
        assertTrue(changes.stream()
                        .anyMatch(c -> c.fileName().equals("a.md") && "conflicted".equals(c.status())),
                "a.md should be reported as conflicted; got: " + changes);
        assertTrue(changes.stream().filter(c -> c.fileName().equals("a.md")).noneMatch(GitChange::staged),
                "a conflicted file must never be reported as staged");
    }

    /** Runs a raw git command in {@code dir} (test setup only; ignores the exit code). */
    private static void runGit(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        process.waitFor();
    }
}
