package com.example.jylos.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Repository setup (init, remote configuration) and remote synchronization:
 * pull, push, and the one-shot commit → pull → push {@link #sync}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitRemoteSyncService {

    private final GitProcessRunner runner;
    private final GitRepositoryProbe probe;
    private final GitConfigHelper configHelper;
    private final GitStatusService statusService;
    private final GitCommitService commitService;
    private final GitNestedRepositoryGuard nestedRepositoryGuard;

    GitRemoteSyncService(GitProcessRunner runner, GitRepositoryProbe probe, GitConfigHelper configHelper,
            GitStatusService statusService, GitCommitService commitService,
            GitNestedRepositoryGuard nestedRepositoryGuard) {
        this.runner = runner;
        this.probe = probe;
        this.configHelper = configHelper;
        this.statusService = statusService;
        this.commitService = commitService;
        this.nestedRepositoryGuard = nestedRepositoryGuard;
    }

    /**
     * Initializes a new repository in {@code dir}: {@code git init}, author config,
     * a default {@code .gitignore}, then an initial commit of the whole vault.
     */
    GitResult init(Path dir) {
        if (!probe.isGitAvailable()) {
            return GitResult.of(GitResult.Status.GIT_UNAVAILABLE, "git is not installed");
        }
        if (dir == null || !Files.isDirectory(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Vault directory not available");
        }
        if (probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "Already a Git repository");
        }
        Proc init = runner.run(dir, "init");
        if (!init.success()) {
            return GitResult.of(GitResult.Status.ERROR, "git init failed: " + init.detail());
        }
        configHelper.ensureAuthor(dir);
        configHelper.ensureGitignore(dir);
        runner.run(dir, "add", "-A", "--", ".");
        Proc commit = runner.run(dir, "-c", "commit.gpgsign=false", "commit", "-m", "Initial vault setup");
        if (!commit.success() && !GitOutputClassifier.isNothingToCommit(commit.detail())) {
            return GitResult.of(GitResult.Status.ERROR, "Initial commit failed: " + commit.detail());
        }
        return GitResult.ok("Git initialized");
    }

    /**
     * Connects {@code origin}, validates it with a fetch, and configures tracking
     * when the remote already contains the current branch. No merge or push is
     * performed implicitly.
     */
    GitResult setRemote(Path dir, String url) {
        if (dir == null || url == null || url.isBlank()) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid remote URL");
        }
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        String branch = statusService.currentBranch(dir);
        if (branch.isBlank()) {
            return GitResult.of(GitResult.Status.NO_UPSTREAM,
                    "Cannot configure tracking while HEAD is detached");
        }

        String previousUrl = statusService.getRemoteUrl(dir);
        boolean hadOrigin = previousUrl != null;
        Proc configured = hadOrigin
                ? runner.run(dir, "remote", "set-url", "origin", url.trim())
                : runner.run(dir, "remote", "add", "origin", url.trim());
        if (!configured.success()) {
            return GitOutputClassifier.resultForFailure("Could not configure remote", configured);
        }

        Proc fetched = runner.runRemote(dir, "fetch", "--quiet", "--prune", "origin");
        if (!fetched.success()) {
            restoreRemote(dir, previousUrl);
            return GitOutputClassifier.resultForFailure("Could not reach remote", fetched);
        }

        String remoteBranch = "origin/" + branch;
        Proc remoteHead = runner.run(dir, "rev-parse", "--verify", "--quiet", remoteBranch);
        if (!remoteHead.success()) {
            return GitResult.ok("Remote configured; push to publish " + branch);
        }
        Proc commonBase = runner.run(dir, "merge-base", "HEAD", remoteBranch);
        if (!commonBase.success()) {
            restoreRemote(dir, previousUrl);
            return GitResult.of(GitResult.Status.INCOMPATIBLE_HISTORY,
                    "Remote branch has unrelated history; remote setup was reverted");
        }
        Proc tracking = runner.run(dir, "branch", "--set-upstream-to=" + remoteBranch, branch);
        if (!tracking.success()) {
            return GitOutputClassifier.resultForFailure("Could not configure upstream", tracking);
        }
        return GitResult.ok("Remote and upstream configured");
    }

    private void restoreRemote(Path dir, String previousUrl) {
        if (previousUrl == null) {
            runner.run(dir, "remote", "remove", "origin");
        } else {
            runner.run(dir, "remote", "set-url", "origin", previousUrl);
        }
    }

    /** Pulls from the remote without rebasing. */
    GitResult pull(Path dir) {
        return pull(dir, null);
    }

    /**
     * Pulls from the remote and forwards Git's progress output when a listener is provided.
     * The listener is deliberately UI-agnostic so the service remains usable outside JavaFX.
     */
    GitResult pull(Path dir, Consumer<String> progressListener) {
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!statusService.hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        String upstream = statusService.upstream(dir);
        if (upstream.isBlank()) {
            return GitResult.of(GitResult.Status.NO_UPSTREAM,
                    "Current branch has no upstream; push it first or configure tracking");
        }
        Proc pull = runner.runRemote(dir, progressListener, "pull", "--progress", "--no-rebase");
        if (pull.success()) {
            return GitResult.ok("Pulled");
        }
        String detail = pull.detail();
        if (GitOutputClassifier.mentions(detail, "conflict")) {
            return GitResult.of(GitResult.Status.CONFLICT, "Merge conflict — resolve it manually");
        }
        if (GitOutputClassifier.isNetworkError(detail)) {
            return GitResult.of(GitResult.Status.NETWORK_ERROR, "Network error during pull");
        }
        if (GitOutputClassifier.isAuthError(detail)) {
            return GitResult.of(GitResult.Status.AUTH_ERROR, "Authentication failed");
        }
        return GitOutputClassifier.resultForFailure("Pull failed", pull);
    }

    /** Pushes to the remote, classifying common failures. */
    GitResult push(Path dir) {
        return push(dir, null);
    }

    /**
     * Pushes to the remote and forwards Git's progress output when a listener is provided.
     * A first push also establishes the current branch's upstream.
     */
    GitResult push(Path dir, Consumer<String> progressListener) {
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!statusService.hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        GitResult oversizedObjects = statusService.githubOversizedObjectsResult(dir);
        if (oversizedObjects != null) {
            return oversizedObjects;
        }
        String branch = statusService.currentBranch(dir);
        if (branch.isBlank()) {
            return GitResult.of(GitResult.Status.NO_UPSTREAM, "Cannot push while HEAD is detached");
        }
        Proc push = statusService.upstream(dir).isBlank()
                ? runner.runRemote(dir, progressListener, "push", "--progress", "--set-upstream", "origin", branch)
                : runner.runRemote(dir, progressListener, "push", "--progress");
        if (push.success()) {
            return GitResult.ok("Pushed");
        }
        String detail = push.detail();
        if (GitOutputClassifier.mentions(detail, "non-fast-forward", "[rejected]", "fetch first")) {
            return GitResult.of(GitResult.Status.REJECTED, "Push rejected — pull first");
        }
        if (GitOutputClassifier.isAuthError(detail)) {
            return GitResult.of(GitResult.Status.AUTH_ERROR, "Authentication failed");
        }
        if (GitOutputClassifier.isNetworkError(detail)) {
            return GitResult.of(GitResult.Status.NETWORK_ERROR, "Network error during push");
        }
        return GitOutputClassifier.resultForFailure("Push failed", push);
    }

    /**
     * One-shot synchronization: commit staged vault changes (if any), then pull and push
     * when a remote is configured. Stops and reports the first blocking failure.
     */
    GitResult sync(Path dir, String commitMessage) {
        return sync(dir, commitMessage, null);
    }

    /**
     * Runs the standard commit, pull and push sequence while optionally reporting remote
     * transfer progress to the caller.
     */
    GitResult sync(Path dir, String commitMessage, Consumer<String> progressListener) {
        if (!probe.isGitAvailable()) {
            return GitResult.of(GitResult.Status.GIT_UNAVAILABLE, "git is not installed");
        }
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        GitResult nestedRepository = nestedRepositoryGuard.dirtyNestedRepositoryResult(dir);
        if (nestedRepository != null) {
            return nestedRepository;
        }
        GitResult commit = commitService.commit(dir, commitMessage);
        if (!commit.ok()) {
            return commit;
        }
        if (!statusService.hasRemote(dir)) {
            // Local-only history is still valuable; report as OK with a hint.
            return GitResult.of(GitResult.Status.NO_REMOTE, "Committed locally (no remote configured)");
        }
        GitResult pull = pull(dir, progressListener);
        if (pull.status() == GitResult.Status.NO_UPSTREAM) {
            return push(dir, progressListener);
        }
        if (!pull.ok() && pull.status() != GitResult.Status.NO_REMOTE) {
            return pull;
        }
        return push(dir, progressListener);
    }
}
