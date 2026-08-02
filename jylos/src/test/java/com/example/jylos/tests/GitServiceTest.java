package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.RandomAccessFile;
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
 * fails on machines without Git while still exercising real repositories.</p>
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
        assertTrue(git.stageAll(vault).ok());

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
    void initPreservesExistingGitignore(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve(".gitignore"), "*.private\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("note.md"), "# Note\n", StandardCharsets.UTF_8);

        assertTrue(git.init(vault).ok());
        assertEquals("*.private\n", Files.readString(vault.resolve(".gitignore"), StandardCharsets.UTF_8));
    }

    @Test
    void githubPushReportsOversizedHistoryBeforeNetworkTransfer(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Path oversized = vault.resolve("archive.bin");
        try (RandomAccessFile file = new RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(101L * 1024 * 1024);
        }
        assertTrue(git.stage(vault, "archive.bin").ok());
        assertTrue(git.commit(vault, "large archive").ok());
        runGit(vault, "remote", "add", "origin", "https://github.com/example/vault.git");

        GitResult push = git.push(vault);
        assertEquals(GitResult.Status.FILE_TOO_LARGE, push.status());
        assertTrue(push.message().contains("archive.bin"));
    }

    @Test
    void commitNeverDeletesAnIndexLockOwnedByAnotherGitProcess(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("a.md"), "one\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());

        Files.writeString(vault.resolve("a.md"), "one\ntwo\n", StandardCharsets.UTF_8);
        assertTrue(git.stageAll(vault).ok());

        // A lock can belong to another Git client. Jylos must report it, never delete it.
        Path lock = vault.resolve(".git").resolve("index.lock");
        Files.writeString(lock, "", StandardCharsets.UTF_8);

        GitResult commit = git.commit(vault, "must not remove lock");
        assertEquals(GitResult.Status.INDEX_LOCKED, commit.status());
        assertTrue(Files.exists(lock), "Jylos must not remove another Git process's lock");
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
    void listChangesAndStagingPreservePathsWithSpaces(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Path note = vault.resolve("folder with spaces").resolve("note with spaces.md");
        Files.createDirectories(note.getParent());
        Files.writeString(note, "# Note\n", StandardCharsets.UTF_8);

        String relativePath = "folder with spaces/note with spaces.md";
        GitChange change = git.listChanges(vault).stream()
                .filter(candidate -> relativePath.equals(candidate.relativePath()))
                .findFirst()
                .orElseThrow();

        assertTrue(git.stage(vault, change.relativePath()).ok());
        assertTrue(git.listChanges(vault).stream()
                .anyMatch(candidate -> relativePath.equals(candidate.relativePath()) && candidate.staged()));
        assertTrue(git.unstage(vault, relativePath).ok());
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
    void nestedRepositoryChangesAreNeverReportedAsStageable(@TempDir Path temp) throws Exception {
        Path vault = temp.resolve("vault");
        Path nestedSource = temp.resolve("nested-source");
        Files.createDirectories(vault);
        Files.createDirectories(nestedSource);
        assertTrue(git.init(vault).ok());

        runGit(nestedSource, "init");
        runGit(nestedSource, "config", "user.name", "Test User");
        runGit(nestedSource, "config", "user.email", "test@example.com");
        Files.writeString(nestedSource.resolve("base.md"), "base\n", StandardCharsets.UTF_8);
        runGit(nestedSource, "add", "base.md");
        runGit(nestedSource, "commit", "-m", "base");

        runGit(vault, "-c", "protocol.file.allow=always", "submodule", "add",
                nestedSource.toUri().toString(), "nested");
        runGit(vault, "add", ".");
        runGit(vault, "commit", "-m", "add nested repository");
        Files.writeString(vault.resolve("nested").resolve("draft.md"), "draft\n", StandardCharsets.UTF_8);

        GitChange nested = git.listChanges(vault).stream().findFirst().orElseThrow();
        assertEquals("nested", nested.relativePath());
        assertEquals("nested_repository_dirty", nested.status());
        assertFalse(nested.staged());

        assertEquals(GitResult.Status.NESTED_REPOSITORY_DIRTY, git.stage(vault, "nested").status());
        assertEquals(GitResult.Status.NESTED_REPOSITORY_DIRTY, git.stageAll(vault).status());
        assertEquals(GitResult.Status.NESTED_REPOSITORY_DIRTY, git.sync(vault, "sync").status());
        assertTrue(runGitOutput(vault, "status", "--porcelain").contains(" M nested"));
    }

    @Test
    void listChangesKeepsStagedAndSubsequentUnstagedEditsSeparate(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Path note = vault.resolve("note.md");
        Files.writeString(note, "base\n", StandardCharsets.UTF_8);
        assertTrue(git.stage(vault, "note.md").ok());
        assertTrue(git.commit(vault, "base").ok());

        Files.writeString(note, "staged edit\n", StandardCharsets.UTF_8);
        assertTrue(git.stage(vault, "note.md").ok());
        Files.writeString(note, "staged edit\nunstaged edit\n", StandardCharsets.UTF_8);

        List<GitChange> changes = git.listChanges(vault);
        assertTrue(changes.stream().anyMatch(change -> change.relativePath().equals("note.md") && change.staged()),
                "the index version must remain staged");
        assertTrue(changes.stream().anyMatch(change -> change.relativePath().equals("note.md") && !change.staged()),
                "the later working-tree edit must remain unstaged");
    }

    @Test
    void listChangesFlagsMergeConflicts(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Files.writeString(vault.resolve("a.md"), "base\n", StandardCharsets.UTF_8);
        assertTrue(git.commit(vault, "base").ok());

        // Diverge two branches on the same line, then merge to force a conflict.
        runGit(vault, "checkout", "-b", "feature");
        Files.writeString(vault.resolve("a.md"), "feature side\n", StandardCharsets.UTF_8);
        assertTrue(git.stageAll(vault).ok());
        assertTrue(git.commit(vault, "feature edit").ok());

        runGit(vault, "checkout", "-"); // back to the default branch
        Files.writeString(vault.resolve("a.md"), "main side\n", StandardCharsets.UTF_8);
        assertTrue(git.stageAll(vault).ok());
        assertTrue(git.commit(vault, "main edit").ok());

        runGit(vault, "merge", "feature"); // conflicts on a.md (non-zero exit, ignored)

        List<GitChange> changes = git.listChanges(vault);
        assertTrue(changes.stream()
                        .anyMatch(c -> c.fileName().equals("a.md") && "conflicted".equals(c.status())),
                "a.md should be reported as conflicted; got: " + changes);
        assertTrue(changes.stream().filter(c -> c.fileName().equals("a.md")).noneMatch(GitChange::staged),
                "a conflicted file must never be reported as staged");
    }

    @Test
    void commitUsesOnlyTheFilesExplicitlyStagedInTheVault(@TempDir Path vault) throws Exception {
        assertTrue(git.init(vault).ok());
        Files.writeString(vault.resolve("selected.md"), "selected\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("unselected.md"), "unselected\n", StandardCharsets.UTF_8);

        assertTrue(git.stage(vault, "selected.md").ok());
        assertTrue(git.commit(vault, "selected only").ok());

        List<GitChange> changes = git.listChanges(vault);
        assertTrue(changes.stream().noneMatch(change -> change.relativePath().equals("selected.md")));
        assertTrue(changes.stream().anyMatch(change -> change.relativePath().equals("unselected.md")));
    }

    @Test
    void nestedVaultNeverStagesOrCommitsParentRepositoryFiles(@TempDir Path temp) throws Exception {
        Path repository = temp.resolve("repository");
        Path vault = repository.resolve("vault");
        Files.createDirectories(vault);
        runGit(repository.getParent(), "init", repository.getFileName().toString());
        runGit(repository, "config", "user.name", "Test User");
        runGit(repository, "config", "user.email", "test@example.com");
        Files.writeString(vault.resolve("note.md"), "base\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("outside.txt"), "base\n", StandardCharsets.UTF_8);
        runGit(repository, "add", ".");
        runGit(repository, "commit", "-m", "base");

        Files.writeString(vault.resolve("note.md"), "vault change\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("outside.txt"), "outside change\n", StandardCharsets.UTF_8);

        assertTrue(git.stageAll(vault).ok());
        assertTrue(git.commit(vault, "vault only").ok());

        String lastCommitFiles = runGitOutput(repository, "show", "--name-only", "--format=", "HEAD");
        assertTrue(lastCommitFiles.contains("vault/note.md"));
        assertFalse(lastCommitFiles.contains("outside.txt"));
        assertTrue(runGitOutput(repository, "status", "--short").contains("outside.txt"));
    }

    @Test
    void nestedVaultUsesVaultRelativePathsForPerFileStaging(@TempDir Path temp) throws Exception {
        Path repository = temp.resolve("repository");
        Path vault = repository.resolve("vault");
        Files.createDirectories(vault);
        runGit(repository.getParent(), "init", repository.getFileName().toString());
        runGit(repository, "config", "user.name", "Test User");
        runGit(repository, "config", "user.email", "test@example.com");
        Path note = vault.resolve("note.md");
        Files.writeString(note, "base\n", StandardCharsets.UTF_8);
        runGit(repository, "add", ".");
        runGit(repository, "commit", "-m", "base");

        Files.writeString(note, "changed\n", StandardCharsets.UTF_8);
        GitChange change = git.listChanges(vault).stream()
                .filter(candidate -> candidate.fileName().equals("note.md"))
                .findFirst()
                .orElseThrow();

        assertEquals("note.md", change.relativePath(), "panel paths must be relative to the vault");
        assertTrue(git.stage(vault, change.relativePath()).ok());
        assertTrue(git.listChanges(vault).stream()
                .anyMatch(candidate -> candidate.relativePath().equals("note.md") && candidate.staged()));

        assertTrue(git.unstage(vault, change.relativePath()).ok());
        assertTrue(git.listChanges(vault).stream()
                .anyMatch(candidate -> candidate.relativePath().equals("note.md") && !candidate.staged()));
    }

    @Test
    void nestedVaultRefusesToCommitWhenParentChangesAreAlreadyStaged(@TempDir Path temp) throws Exception {
        Path repository = temp.resolve("repository");
        Path vault = repository.resolve("vault");
        Files.createDirectories(vault);
        runGit(repository.getParent(), "init", repository.getFileName().toString());
        runGit(repository, "config", "user.name", "Test User");
        runGit(repository, "config", "user.email", "test@example.com");
        Files.writeString(vault.resolve("note.md"), "base\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("outside.txt"), "base\n", StandardCharsets.UTF_8);
        runGit(repository, "add", ".");
        runGit(repository, "commit", "-m", "base");

        Files.writeString(vault.resolve("note.md"), "vault change\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("outside.txt"), "outside change\n", StandardCharsets.UTF_8);
        assertTrue(git.stageAll(vault).ok());
        runGit(repository, "add", "outside.txt");

        GitResult commit = git.commit(vault, "must not include parent work");
        assertEquals(GitResult.Status.OUTSIDE_VAULT_STAGED, commit.status());
        assertTrue(runGitOutput(repository, "log", "-1", "--format=%s").contains("base"));
    }

    @Test
    void syncConfiguresUpstreamForANewRemoteBranch(@TempDir Path temp) throws Exception {
        Path vault = temp.resolve("vault");
        Path remote = temp.resolve("remote.git");
        Files.createDirectories(vault);
        runGit(temp, "init", "--bare", remote.getFileName().toString());
        Files.writeString(vault.resolve("note.md"), "content\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());

        GitResult configured = git.setRemote(vault, remote.toUri().toString());
        assertTrue(configured.ok(), configured.message());
        assertFalse(git.status(vault).hasUpstream());

        assertTrue(git.sync(vault, "initial sync").ok());
        GitStatus status = git.refreshRemoteStatus(vault);
        assertTrue(status.hasUpstream());
        assertFalse(status.upstream().isBlank());
    }

    @Test
    void branchOperationsCreateAndSwitchOnlyWithACleanVault(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("note.md"), "base\n", StandardCharsets.UTF_8);
        assertTrue(git.init(vault).ok());
        String initial = git.status(vault).branch();

        GitResult create = git.createBranch(vault, "research");
        assertTrue(create.ok(), create.message());
        assertEquals("research", git.status(vault).branch());
        assertTrue(git.branches(vault).containsAll(List.of(initial, "research")));

        Files.writeString(vault.resolve("note.md"), "dirty\n", StandardCharsets.UTF_8);
        assertEquals(GitResult.Status.DIRTY_WORKTREE, git.switchBranch(vault, initial).status());

        assertTrue(git.stageAll(vault).ok());
        assertTrue(git.commit(vault, "research change").ok());
        assertTrue(git.switchBranch(vault, initial).ok());
        assertEquals(initial, git.status(vault).branch());
    }

    @Test
    void nestedVaultDoesNotOfferBranchOperations(@TempDir Path temp) throws Exception {
        Path repository = temp.resolve("repository");
        Path vault = repository.resolve("vault");
        Files.createDirectories(vault);
        runGit(repository.getParent(), "init", repository.getFileName().toString());
        runGit(repository, "config", "user.name", "Test User");
        runGit(repository, "config", "user.email", "test@example.com");
        Files.writeString(vault.resolve("note.md"), "base\n", StandardCharsets.UTF_8);
        runGit(repository, "add", ".");
        runGit(repository, "commit", "-m", "base");

        assertFalse(git.supportsBranchOperations(vault));
        assertTrue(git.branches(vault).isEmpty());
        assertEquals(GitResult.Status.BRANCH_SCOPE_UNSUPPORTED, git.createBranch(vault, "feature").status());
    }

    /** Runs a raw git command in {@code dir} (test setup only; ignores the exit code). */
    private static void runGit(Path dir, String... args) throws Exception {
        runGitOutput(dir, args);
    }

    private static String runGitOutput(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output;
    }
}
