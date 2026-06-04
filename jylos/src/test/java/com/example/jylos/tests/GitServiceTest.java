package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
