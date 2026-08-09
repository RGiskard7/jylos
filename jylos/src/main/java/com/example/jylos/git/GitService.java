package com.example.jylos.git;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Git-backed vault synchronization, implemented by driving the system {@code git}
 * CLI so there is no native library to bundle.
 *
 * <h2>Operations</h2>
 * init, local/remote status, explicit staging, commit, pull, push, local branch
 * creation/switching, a one-shot {@link #sync(Path, String)} (commit → pull → push),
 * and remote setup.
 *
 * <h2>Threading</h2>
 * Every method blocks on the {@code git} process; callers must invoke them off the
 * JavaFX Application Thread (e.g. in a {@code Task}). Output is drained on separate
 * threads to avoid pipe-buffer deadlocks.
 *
 * <h2>Structure</h2>
 * This class is a thin facade with a stable public API. Its behavior is implemented
 * by focused package-private collaborators: {@link GitProcessRunner} (subprocess
 * execution, index-lock serialization, cancellation), {@link GitOutputClassifier}
 * (stateless text classification), {@link GitRepositoryProbe} (availability/is-repo
 * checks), {@link GitPathResolver} (repository/vault path translation),
 * {@link GitNestedRepositoryGuard} (submodule safety checks), {@link GitStatusService},
 * {@link GitStagingService}, {@link GitCommitService}, {@link GitRemoteSyncService},
 * and {@link GitBranchService}. All Git subprocesses still funnel through one
 * {@link GitProcessRunner} instance, so the app-wide index-lock serialization is
 * unchanged.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public final class GitService {

    private final GitProcessRunner runner = new GitProcessRunner();
    private final GitRepositoryProbe probe = new GitRepositoryProbe(runner);
    private final GitPathResolver pathResolver = new GitPathResolver(runner);
    private final GitConfigHelper configHelper = new GitConfigHelper(runner);
    private final GitNestedRepositoryGuard nestedRepositoryGuard =
            new GitNestedRepositoryGuard(runner, probe, pathResolver);
    private final GitStatusService statusService =
            new GitStatusService(runner, probe, pathResolver, nestedRepositoryGuard);
    private final GitStagingService stagingService =
            new GitStagingService(runner, probe, nestedRepositoryGuard);
    private final GitCommitService commitService =
            new GitCommitService(runner, probe, configHelper, nestedRepositoryGuard);
    private final GitRemoteSyncService remoteSyncService = new GitRemoteSyncService(
            runner, probe, configHelper, statusService, commitService, nestedRepositoryGuard);
    private final GitBranchService branchService =
            new GitBranchService(runner, probe, pathResolver, statusService);

    // ── Availability ────────────────────────────────────────────────────────

    /** True if the {@code git} executable is available on PATH. */
    public boolean isGitAvailable() {
        return probe.isGitAvailable();
    }

    /** True if {@code dir} is inside a Git working tree. */
    public boolean isRepository(Path dir) {
        return probe.isRepository(dir);
    }

    // ── Status ──────────────────────────────────────────────────────────────

    /** Reads local Git state without performing network I/O. */
    public GitStatus status(Path dir) {
        return statusService.status(dir);
    }

    /**
     * Fetches configured remotes once, then returns the resulting local status.
     * Network access is explicit so status-bar refreshes remain fast and offline-safe.
     */
    public GitStatus refreshRemoteStatus(Path dir) {
        return statusService.refreshRemoteStatus(dir);
    }

    /** Fetches and prunes {@code origin}, returning a classified result for UI feedback. */
    public GitResult fetchRemote(Path dir) {
        return statusService.fetchRemote(dir);
    }

    // ── Repository setup ──────────────────────────────────────────────────────

    /**
     * Initializes a new repository in {@code dir}: {@code git init}, author config,
     * a default {@code .gitignore}, then an initial commit of the whole vault.
     */
    public GitResult init(Path dir) {
        return remoteSyncService.init(dir);
    }

    /**
     * Connects {@code origin}, validates it with a fetch, and configures tracking
     * when the remote already contains the current branch. No merge or push is
     * performed implicitly.
     */
    public GitResult setRemote(Path dir, String url) {
        return remoteSyncService.setRemote(dir, url);
    }

    // ── Branches ─────────────────────────────────────────────────────────────

    /**
     * Lists local branches for a vault that is itself the Git working-tree root.
     * Branch operations are intentionally unavailable for a vault nested inside a
     * larger repository: switching there would also switch files outside Jylos.
     */
    public List<String> branches(Path dir) {
        return branchService.branches(dir);
    }

    /** True when switching branches cannot affect files outside the current vault. */
    public boolean supportsBranchOperations(Path dir) {
        return branchService.supportsBranchOperations(dir);
    }

    /** Creates and switches to a new local branch after verifying a clean working tree. */
    public GitResult createBranch(Path dir, String branch) {
        return branchService.createBranch(dir, branch);
    }

    /** Switches to an existing local branch without carrying uncommitted changes across it. */
    public GitResult switchBranch(Path dir, String branch) {
        return branchService.switchBranch(dir, branch);
    }

    // ── Commit / pull / push ──────────────────────────────────────────────────

    /** Commits the vault files that are already staged in Git's index. */
    public GitResult commit(Path dir, String message) {
        return commitService.commit(dir, message);
    }

    /** Pulls from the remote without rebasing. */
    public GitResult pull(Path dir) {
        return remoteSyncService.pull(dir);
    }

    /**
     * Pulls from the remote and forwards Git's progress output when a listener is provided.
     * The listener is deliberately UI-agnostic so the service remains usable outside JavaFX.
     */
    public GitResult pull(Path dir, Consumer<String> progressListener) {
        return remoteSyncService.pull(dir, progressListener);
    }

    /** Pushes to the remote, classifying common failures. */
    public GitResult push(Path dir) {
        return remoteSyncService.push(dir);
    }

    /**
     * Pushes to the remote and forwards Git's progress output when a listener is provided.
     * A first push also establishes the current branch's upstream.
     */
    public GitResult push(Path dir, Consumer<String> progressListener) {
        return remoteSyncService.push(dir, progressListener);
    }

    /**
     * One-shot synchronization: commit staged vault changes (if any), then pull and push
     * when a remote is configured. Stops and reports the first blocking failure.
     */
    public GitResult sync(Path dir, String commitMessage) {
        return remoteSyncService.sync(dir, commitMessage);
    }

    /**
     * Runs the standard commit, pull and push sequence while optionally reporting remote
     * transfer progress to the caller.
     */
    public GitResult sync(Path dir, String commitMessage, Consumer<String> progressListener) {
        return remoteSyncService.sync(dir, commitMessage, progressListener);
    }

    // ── Changes, staging & history ────────────────────────────────────────────

    /**
     * Lists every uncommitted change beneath the vault, with best-effort line statistics.
     * Untracked files intentionally omit line counts so a large vault can be listed
     * without opening every file during a UI refresh.
     */
    public List<GitChange> listChanges(Path dir) {
        return statusService.listChanges(dir);
    }

    /** Stages a single vault-relative path. */
    public GitResult stage(Path dir, String relativePath) {
        return stagingService.stage(dir, relativePath);
    }

    /** Unstages a single vault-relative path. */
    public GitResult unstage(Path dir, String relativePath) {
        return stagingService.unstage(dir, relativePath);
    }

    /** Stages every change below the vault root, without affecting a parent repository. */
    public GitResult stageAll(Path dir) {
        return stagingService.stageAll(dir);
    }

    /** Unstages every vault change, leaving working-tree edits untouched. */
    public GitResult unstageAll(Path dir) {
        return stagingService.unstageAll(dir);
    }

    /**
     * Returns recent commits affecting the current vault (newest first).
     *
     * @param limit maximum number of commits
     */
    public List<GitCommit> history(Path dir, int limit) {
        return statusService.history(dir, limit);
    }

    /** Returns the {@code origin} remote URL, or null when none is configured. */
    public String getRemoteUrl(Path dir) {
        return statusService.getRemoteUrl(dir);
    }

    // ── Cancellation ─────────────────────────────────────────────────────────

    /**
     * Cancels the currently running Git command, including its helper processes such as
     * {@code ssh} and {@code git pack-objects}. This prevents a cancelled transfer from
     * continuing in the background after its parent process exits.
     *
     * @return {@code true} when there was an active Git command to cancel
     */
    public boolean cancelActiveOperation() {
        return runner.cancelActiveOperation();
    }
}
