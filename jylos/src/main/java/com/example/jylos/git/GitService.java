package com.example.jylos.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;

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
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public final class GitService {

    private static final Logger logger = LoggerConfig.getLogger(GitService.class);
    private static final Duration LOCAL_OPERATION_TIMEOUT = Duration.ofMinutes(2);
    private static final String DEFAULT_GITIGNORE = String.join("\n",
            "# Jylos / OS metadata",
            ".DS_Store",
            "Thumbs.db",
            ".trash/",
            "*.tmp",
            "*.bak");

    /** Result of running a git subprocess. */
    private record Proc(int code, String out, String err) {
        boolean success() {
            return code == 0;
        }
        String detail() {
            String e = err != null ? err.trim() : "";
            return e.isEmpty() ? (out != null ? out.trim() : "") : e;
        }
    }

    private final Object activeProcessLock = new Object();
    private volatile Process activeProcess;

    // ── Availability ────────────────────────────────────────────────────────

    /** True if the {@code git} executable is available on PATH. */
    public boolean isGitAvailable() {
        try {
            return run(null, "--version").success();
        } catch (Exception e) {
            return false;
        }
    }

    /** True if {@code dir} is inside a Git working tree. */
    public boolean isRepository(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        if (Files.isDirectory(dir.resolve(".git"))) {
            return true;
        }
        Proc p = run(dir, "rev-parse", "--is-inside-work-tree");
        return p.success() && "true".equals(p.out().trim());
    }

    // ── Status ──────────────────────────────────────────────────────────────

    /** Reads local Git state without performing network I/O. */
    public GitStatus status(Path dir) {
        if (dir == null || !isRepository(dir)) {
            return GitStatus.none();
        }
        int modified = countModified(dir);
        boolean hasRemote = hasRemote(dir);
        String branch = currentBranch(dir);
        String upstream = upstream(dir);
        boolean hasUpstream = !upstream.isBlank();

        int ahead = 0;
        int behind = 0;
        if (hasUpstream) {
            Proc rev = run(dir, "rev-list", "--left-right", "--count", "HEAD...@{upstream}");
            if (rev.success()) {
                String[] parts = rev.out().trim().split("\\s+");
                if (parts.length >= 2) {
                    ahead = parseInt(parts[0]);
                    behind = parseInt(parts[1]);
                }
            }
        }
        return new GitStatus(true, hasRemote, hasUpstream, upstream, branch, modified, ahead, behind);
    }

    /**
     * Fetches configured remotes once, then returns the resulting local status.
     * Network access is explicit so status-bar refreshes remain fast and offline-safe.
     */
    public GitStatus refreshRemoteStatus(Path dir) {
        if (dir == null || !isRepository(dir)) {
            return GitStatus.none();
        }
        fetchRemote(dir);
        return status(dir);
    }

    /** Fetches and prunes {@code origin}, returning a classified result for UI feedback. */
    public GitResult fetchRemote(Path dir) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        Proc fetch = runRemote(dir, "fetch", "--quiet", "--prune", "origin");
        return fetch.success() ? GitResult.ok("Fetched") : resultForFailure("Fetch failed", fetch);
    }

    private int countModified(Path dir) {
        Proc p = run(dir, "status", "--porcelain=v1", "-z", "--", ".");
        if (!p.success()) {
            return 0;
        }
        int count = 0;
        String[] entries = p.out().split("\u0000", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.length() >= 4) {
                count++;
                if (isRenameOrCopy(entry.substring(0, 2))) {
                    index++; // The NUL format emits the original path as a second record.
                }
            }
        }
        return count;
    }

    private boolean hasRemote(Path dir) {
        return getRemoteUrl(dir) != null;
    }

    private String currentBranch(Path dir) {
        Proc p = run(dir, "branch", "--show-current");
        return p.success() ? p.out().trim() : "";
    }

    private String upstream(Path dir) {
        Proc p = run(dir, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}");
        return p.success() ? p.out().trim() : "";
    }

    // ── Repository setup ──────────────────────────────────────────────────────

    /**
     * Initializes a new repository in {@code dir}: {@code git init}, author config,
     * a default {@code .gitignore}, then an initial commit of the whole vault.
     */
    public GitResult init(Path dir) {
        if (!isGitAvailable()) {
            return GitResult.of(GitResult.Status.GIT_UNAVAILABLE, "git is not installed");
        }
        if (dir == null || !Files.isDirectory(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Vault directory not available");
        }
        if (isRepository(dir)) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "Already a Git repository");
        }
        Proc init = run(dir, "init");
        if (!init.success()) {
            return GitResult.of(GitResult.Status.ERROR, "git init failed: " + init.detail());
        }
        ensureAuthor(dir);
        ensureGitignore(dir);
        run(dir, "add", "-A", "--", ".");
        Proc commit = run(dir, "-c", "commit.gpgsign=false", "commit", "-m", "Initial vault setup");
        if (!commit.success() && !isNothingToCommit(commit.detail())) {
            return GitResult.of(GitResult.Status.ERROR, "Initial commit failed: " + commit.detail());
        }
        return GitResult.ok("Git initialized");
    }

    /**
     * Connects {@code origin}, validates it with a fetch, and configures tracking
     * when the remote already contains the current branch. No merge or push is
     * performed implicitly.
     */
    public GitResult setRemote(Path dir, String url) {
        if (dir == null || url == null || url.isBlank()) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid remote URL");
        }
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        String branch = currentBranch(dir);
        if (branch.isBlank()) {
            return GitResult.of(GitResult.Status.NO_UPSTREAM,
                    "Cannot configure tracking while HEAD is detached");
        }

        String previousUrl = getRemoteUrl(dir);
        boolean hadOrigin = previousUrl != null;
        Proc configured = hadOrigin
                ? run(dir, "remote", "set-url", "origin", url.trim())
                : run(dir, "remote", "add", "origin", url.trim());
        if (!configured.success()) {
            return resultForFailure("Could not configure remote", configured);
        }

        Proc fetched = runRemote(dir, "fetch", "--quiet", "--prune", "origin");
        if (!fetched.success()) {
            restoreRemote(dir, previousUrl);
            return resultForFailure("Could not reach remote", fetched);
        }

        String remoteBranch = "origin/" + branch;
        Proc remoteHead = run(dir, "rev-parse", "--verify", "--quiet", remoteBranch);
        if (!remoteHead.success()) {
            return GitResult.ok("Remote configured; push to publish " + branch);
        }
        Proc commonBase = run(dir, "merge-base", "HEAD", remoteBranch);
        if (!commonBase.success()) {
            restoreRemote(dir, previousUrl);
            return GitResult.of(GitResult.Status.INCOMPATIBLE_HISTORY,
                    "Remote branch has unrelated history; remote setup was reverted");
        }
        Proc tracking = run(dir, "branch", "--set-upstream-to=" + remoteBranch, branch);
        if (!tracking.success()) {
            return resultForFailure("Could not configure upstream", tracking);
        }
        return GitResult.ok("Remote and upstream configured");
    }

    // ── Branches ─────────────────────────────────────────────────────────────

    /**
     * Lists local branches for a vault that is itself the Git working-tree root.
     * Branch operations are intentionally unavailable for a vault nested inside a
     * larger repository: switching there would also switch files outside Jylos.
     */
    public List<String> branches(Path dir) {
        if (!supportsBranchOperations(dir)) {
            return List.of();
        }
        Proc branches = run(dir, "for-each-ref", "--format=%(refname:short)", "refs/heads");
        if (!branches.success()) {
            return List.of();
        }
        return branches.out().lines()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .sorted()
                .toList();
    }

    /** True when switching branches cannot affect files outside the current vault. */
    public boolean supportsBranchOperations(Path dir) {
        if (dir == null || !isRepository(dir)) {
            return false;
        }
        Path root = repositoryRoot(dir);
        if (root == null) {
            return false;
        }
        try {
            return Files.isSameFile(root, dir);
        } catch (IOException e) {
            return false;
        }
    }

    /** Creates and switches to a new local branch after verifying a clean working tree. */
    public GitResult createBranch(Path dir, String branch) {
        GitResult readiness = branchOperationReadiness(dir);
        if (readiness != null) {
            return readiness;
        }
        String name = branch != null ? branch.trim() : "";
        Proc validName = run(dir, "check-ref-format", "--branch", name);
        if (!validName.success()) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid branch name");
        }
        Proc created = run(dir, "switch", "-c", name);
        return created.success() ? GitResult.ok("Created and switched to " + name)
                : resultForFailure("Could not create branch", created);
    }

    /** Switches to an existing local branch without carrying uncommitted changes across it. */
    public GitResult switchBranch(Path dir, String branch) {
        GitResult readiness = branchOperationReadiness(dir);
        if (readiness != null) {
            return readiness;
        }
        String name = branch != null ? branch.trim() : "";
        if (!branches(dir).contains(name)) {
            return GitResult.of(GitResult.Status.ERROR, "Unknown local branch: " + name);
        }
        if (name.equals(currentBranch(dir))) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "Already on " + name);
        }
        Proc switched = run(dir, "switch", name);
        return switched.success() ? GitResult.ok("Switched to " + name)
                : resultForFailure("Could not switch branch", switched);
    }

    // ── Commit / pull / push ──────────────────────────────────────────────────

    /** Commits the vault files that are already staged in Git's index. */
    public GitResult commit(Path dir, String message) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc staged = run(dir, "diff", "--cached", "--quiet", "--", ".");
        if (staged.code() == 0) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "No staged vault changes to commit");
        }
        if (staged.code() != 1) {
            return resultForFailure("Could not inspect staged changes", staged);
        }
        if (hasStagedChangesOutsideVault(dir)) {
            return GitResult.of(GitResult.Status.OUTSIDE_VAULT_STAGED,
                    "Staged changes outside this vault must be committed from a Git client first");
        }
        ensureAuthor(dir);
        // Disable commit signing for app-generated commits: GUI apps often can't
        // reach a GPG/SSH signer non-interactively, which would otherwise fail every
        // commit on machines with commit.gpgsign=true.
        Proc commit = run(dir, "-c", "commit.gpgsign=false", "commit", "-m", message);
        if (commit.success()) {
            return GitResult.ok("Committed");
        }
        String detail = commit.detail();
        if (isNothingToCommit(detail)) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "Nothing to commit");
        }
        return resultForFailure("Commit failed", commit);
    }

    /** Pulls from the remote without rebasing. */
    public GitResult pull(Path dir) {
        return pull(dir, null);
    }

    /**
     * Pulls from the remote and forwards Git's progress output when a listener is provided.
     * The listener is deliberately UI-agnostic so the service remains usable outside JavaFX.
     */
    public GitResult pull(Path dir, Consumer<String> progressListener) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        String upstream = upstream(dir);
        if (upstream.isBlank()) {
            return GitResult.of(GitResult.Status.NO_UPSTREAM,
                    "Current branch has no upstream; push it first or configure tracking");
        }
        Proc pull = runRemote(dir, progressListener, "pull", "--progress", "--no-rebase");
        if (pull.success()) {
            return GitResult.ok("Pulled");
        }
        String detail = pull.detail();
        if (mentions(detail, "conflict")) {
            return GitResult.of(GitResult.Status.CONFLICT, "Merge conflict — resolve it manually");
        }
        if (isNetworkError(detail)) {
            return GitResult.of(GitResult.Status.NETWORK_ERROR, "Network error during pull");
        }
        if (isAuthError(detail)) {
            return GitResult.of(GitResult.Status.AUTH_ERROR, "Authentication failed");
        }
        return resultForFailure("Pull failed", pull);
    }

    /** Pushes to the remote, classifying common failures. */
    public GitResult push(Path dir) {
        return push(dir, null);
    }

    /**
     * Pushes to the remote and forwards Git's progress output when a listener is provided.
     * A first push also establishes the current branch's upstream.
     */
    public GitResult push(Path dir, Consumer<String> progressListener) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        GitResult oversizedObjects = githubOversizedObjectsResult(dir);
        if (oversizedObjects != null) {
            return oversizedObjects;
        }
        String branch = currentBranch(dir);
        if (branch.isBlank()) {
            return GitResult.of(GitResult.Status.NO_UPSTREAM, "Cannot push while HEAD is detached");
        }
        Proc push = upstream(dir).isBlank()
                ? runRemote(dir, progressListener, "push", "--progress", "--set-upstream", "origin", branch)
                : runRemote(dir, progressListener, "push", "--progress");
        if (push.success()) {
            return GitResult.ok("Pushed");
        }
        String detail = push.detail();
        if (mentions(detail, "non-fast-forward", "[rejected]", "fetch first")) {
            return GitResult.of(GitResult.Status.REJECTED, "Push rejected — pull first");
        }
        if (isAuthError(detail)) {
            return GitResult.of(GitResult.Status.AUTH_ERROR, "Authentication failed");
        }
        if (isNetworkError(detail)) {
            return GitResult.of(GitResult.Status.NETWORK_ERROR, "Network error during push");
        }
        return resultForFailure("Push failed", push);
    }

    /**
     * One-shot synchronization: commit staged vault changes (if any), then pull and push
     * when a remote is configured. Stops and reports the first blocking failure.
     */
    public GitResult sync(Path dir, String commitMessage) {
        return sync(dir, commitMessage, null);
    }

    /**
     * Runs the standard commit, pull and push sequence while optionally reporting remote
     * transfer progress to the caller.
     */
    public GitResult sync(Path dir, String commitMessage, Consumer<String> progressListener) {
        if (!isGitAvailable()) {
            return GitResult.of(GitResult.Status.GIT_UNAVAILABLE, "git is not installed");
        }
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        GitResult nestedRepository = dirtyNestedRepositoryResult(dir);
        if (nestedRepository != null) {
            return nestedRepository;
        }
        GitResult commit = commit(dir, commitMessage);
        if (!commit.ok()) {
            return commit;
        }
        if (!hasRemote(dir)) {
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

    // ── Changes, staging & history ────────────────────────────────────────────

    /** Lists every uncommitted change beneath the vault, with best-effort line statistics. */
    public List<GitChange> listChanges(Path dir) {
        List<GitChange> changes = new ArrayList<>();
        if (!isRepository(dir)) {
            return changes;
        }
        Proc status = run(dir, "status", "--porcelain=v1", "-z", "--untracked-files=all", "--", ".");
        if (!status.success()) {
            return changes;
        }
        java.util.Map<String, int[]> stats = numstat(dir);
        String[] entries = status.out().split("\u0000", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.length() < 4) {
                continue;
            }
            String code = entry.substring(0, 2);
            String repositoryPath = entry.substring(3);
            if (isRenameOrCopy(code)) {
                if (++index >= entries.length) {
                    logger.warning("Ignoring incomplete Git rename/copy status entry");
                    break;
                }
            }
            String path = vaultRelativePath(dir, repositoryPath);
            if (path == null) {
                logger.fine("Ignoring Git status path outside vault: " + repositoryPath);
                continue;
            }
            int added;
            int deleted;
            if (code.equals("??")) {
                added = countFileLines(dir.resolve(path));
                deleted = 0;
            } else {
                int[] s = stats.get(path);
                added = s != null ? s[0] : -1;
                deleted = s != null ? s[1] : -1;
            }
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            boolean nestedRepositoryDirty = code.length() == 2 && code.charAt(1) != ' '
                    && isDirtyNestedRepository(dir, path);
            addChangesForPorcelainEntry(changes, code, path, fileName, added, deleted, nestedRepositoryDirty);
        }
        return changes;
    }

    /**
     * Expands Git's two-column porcelain status into the index and work-tree entries
     * users actually need to act on. A file changed again after staging therefore
     * appears once as staged and once as unstaged instead of hiding one state.
     */
    private static void addChangesForPorcelainEntry(List<GitChange> changes, String code, String path,
            String fileName, int added, int deleted, boolean nestedRepositoryDirty) {
        if ("??".equals(code)) {
            changes.add(new GitChange(path, fileName, "untracked", added, deleted, false));
            return;
        }
        if (isConflict(code)) {
            // Conflicted paths must never be offered as a stage/unstage operation.
            changes.add(new GitChange(path, fileName, "conflicted", added, deleted, false));
            return;
        }
        char indexStatus = code.charAt(0);
        char workTreeStatus = code.charAt(1);
        if (indexStatus != ' ') {
            changes.add(new GitChange(path, fileName, statusLabel(indexStatus), added, deleted, true));
        }
        if (workTreeStatus != ' ') {
            String status = nestedRepositoryDirty ? "nested_repository_dirty" : statusLabel(workTreeStatus);
            changes.add(new GitChange(path, fileName, status, added, deleted, false));
        }
    }

    /** Stages a single vault-relative path. */
    public GitResult stage(Path dir, String relativePath) {
        String path = validVaultRelativePath(relativePath);
        if (path == null) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid vault-relative path");
        }
        if (isDirtyNestedRepository(dir, path)) {
            return nestedRepositoryDirtyResult(List.of(path));
        }
        Proc p = run(dir, "add", "--", path);
        return p.success() ? GitResult.ok("Staged")
                : resultForFailure("Stage failed", p);
    }

    /** Unstages a single vault-relative path. */
    public GitResult unstage(Path dir, String relativePath) {
        String path = validVaultRelativePath(relativePath);
        if (path == null) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid vault-relative path");
        }
        Proc p = run(dir, "reset", "-q", "HEAD", "--", path);
        return p.success() ? GitResult.ok("Unstaged")
                : resultForFailure("Unstage failed", p);
    }

    /** Stages every change below the vault root, without affecting a parent repository. */
    public GitResult stageAll(Path dir) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc p = run(dir, "add", "-A", "--", ".");
        if (!p.success()) {
            return resultForFailure("Stage all failed", p);
        }
        GitResult nestedRepository = dirtyNestedRepositoryResult(dir);
        return nestedRepository != null ? nestedRepository : GitResult.ok("Staged all");
    }

    /** Unstages every vault change, leaving working-tree edits untouched. */
    public GitResult unstageAll(Path dir) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc p = run(dir, "reset", "-q", "HEAD", "--", ".");
        return p.success() ? GitResult.ok("Unstaged all")
                : resultForFailure("Unstage all failed", p);
    }

    /**
     * Returns recent commits affecting the current vault (newest first).
     *
     * @param limit maximum number of commits
     */
    public List<GitCommit> history(Path dir, int limit) {
        List<GitCommit> commits = new ArrayList<>();
        if (!isRepository(dir)) {
            return commits;
        }
        Proc log = run(dir, "log", "--date=iso-strict",
                "--pretty=%H%x1f%h%x1f%an%x1f%aI%x1f%s%x1f%D", "-n", String.valueOf(Math.max(1, limit)),
                "--", ".");
        if (!log.success()) {
            return commits; // e.g. no commits yet
        }
        for (String line : log.out().split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split("\u001f", -1);
            if (f.length < 5) {
                continue;
            }
            commits.add(new GitCommit(f[0], f[1], f[2], f[3], f[4], f.length >= 6 ? f[5] : ""));
        }
        return commits;
    }

    /** Returns the {@code origin} remote URL, or null when none is configured. */
    public String getRemoteUrl(Path dir) {
        if (!isRepository(dir)) {
            return null;
        }
        Proc p = run(dir, "remote", "get-url", "origin");
        if (p.success()) {
            String url = p.out().trim();
            return url.isEmpty() ? null : url;
        }
        return null;
    }

    private java.util.Map<String, int[]> numstat(Path dir) {
        java.util.Map<String, int[]> map = new java.util.HashMap<>();
        Proc head = run(dir, "rev-parse", "--verify", "HEAD");
        if (!head.success()) {
            return map; // no commits yet
        }
        Proc diff = run(dir, "diff", "--numstat", "--no-renames", "-z", "HEAD", "--", ".");
        if (!diff.success()) {
            return map;
        }
        for (String entry : diff.out().split("\u0000", -1)) {
            String[] parts = entry.split("\t", 3);
            if (parts.length >= 3) {
                int added = "-".equals(parts[0]) ? -1 : parseInt(parts[0]);
                int deleted = "-".equals(parts[1]) ? -1 : parseInt(parts[1]);
                String path = vaultRelativePath(dir, parts[2]);
                if (path != null) {
                    map.put(path, new int[] { added, deleted });
                }
            }
        }
        return map;
    }

    private static int countFileLines(Path file) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return (int) lines.count();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * True for an unmerged (conflicted) porcelain code. These are the seven states
     * Git reports during a merge/rebase conflict; they require manual resolution and
     * must never be auto-staged or auto-resolved.
     */
    private static boolean isConflict(String code) {
        return switch (code) {
            case "DD", "AU", "UD", "UA", "DU", "AA", "UU" -> true;
            default -> false;
        };
    }

    /** True when porcelain emits a second, original-path record for a rename or copy. */
    private static boolean isRenameOrCopy(String code) {
        return code.length() == 2
                && (code.charAt(0) == 'R' || code.charAt(0) == 'C'
                        || code.charAt(1) == 'R' || code.charAt(1) == 'C');
    }

    private static String statusLabel(char status) {
        return switch (status) {
            case 'A' -> "added";
            case 'D' -> "deleted";
            case 'R' -> "renamed";
            case 'C' -> "copied";
            default -> "modified";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureAuthor(Path dir) {
        ensureConfig(dir, "user.name", "Jylos");
        ensureConfig(dir, "user.email", "vault@jylos.local");
    }

    private void ensureConfig(Path dir, String key, String fallback) {
        Proc existing = run(dir, "config", "--local", key);
        if (existing.success() && !existing.out().trim().isEmpty()) {
            return;
        }
        run(dir, "config", "--local", key, fallback);
    }

    private void ensureGitignore(Path dir) {
        Path gitignore = dir.resolve(".gitignore");
        if (Files.exists(gitignore)) {
            return;
        }
        try {
            Files.writeString(gitignore, DEFAULT_GITIGNORE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not write .gitignore", e);
        }
    }

    /**
     * Detects blobs that GitHub will reject before starting an expensive HTTP push.
     * The check spans reachable history because removing a file only from the current
     * working tree does not remove the old blob from a first push.
     */
    private GitResult githubOversizedObjectsResult(Path dir) {
        if (!usesGitHubRemote(dir)) {
            return null;
        }
        Proc filtered = run(dir, "rev-list", "--objects", "--all", "--filter=blob:limit=100m",
                "--filter-print-omitted");
        if (!filtered.success()) {
            return null;
        }
        Set<String> oversizedIds = new HashSet<>();
        for (String line : filtered.out().split("\\R")) {
            if (line.startsWith("~") && line.length() > 1) {
                oversizedIds.add(line.substring(1).trim());
            }
        }
        if (oversizedIds.isEmpty()) {
            return null;
        }
        Proc objects = run(dir, "rev-list", "--objects", "--all");
        if (!objects.success()) {
            return null;
        }
        List<String> paths = new ArrayList<>();
        for (String line : objects.out().split("\\R")) {
            int separator = line.indexOf(' ');
            if (separator <= 0 || !oversizedIds.contains(line.substring(0, separator))) {
                continue;
            }
            String path = line.substring(separator + 1).trim();
            if (!path.isEmpty() && !paths.contains(path)) {
                paths.add(path);
            }
        }
        String listedPaths = paths.isEmpty() ? "reachable repository history" : String.join(", ", paths);
        return GitResult.of(GitResult.Status.FILE_TOO_LARGE,
                "GitHub rejects files larger than 100 MiB: " + listedPaths
                        + ". Remove them from Git history or use Git LFS");
    }

    private boolean usesGitHubRemote(Path dir) {
        String remote = getRemoteUrl(dir);
        if (remote == null) {
            return false;
        }
        String value = remote.toLowerCase(Locale.ROOT);
        return value.contains("github.com/") || value.contains("github.com:");
    }

    private static boolean isNothingToCommit(String detail) {
        return mentions(detail, "nothing to commit", "nothing added to commit", "no changes added");
    }

    /** True when Git failed because another process holds the index lock. */
    private static boolean isLockError(String detail) {
        return mentions(detail, "index.lock");
    }

    private static boolean isAuthError(String detail) {
        return mentions(detail, "authentication failed", "could not read username",
                "permission denied", "403", "401", "invalid username or password");
    }

    private static boolean isNetworkError(String detail) {
        return mentions(detail, "could not resolve host", "connection timed out", "network is unreachable",
                "failed to connect", "unable to access");
    }

    private static boolean mentions(String text, String... needles) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private GitResult resultForFailure(String operation, Proc process) {
        String detail = process.detail();
        if (isLockError(detail)) {
            return GitResult.of(GitResult.Status.INDEX_LOCKED,
                    "Git index is locked by another process; close it and try again");
        }
        if (isAuthError(detail)) {
            return GitResult.of(GitResult.Status.AUTH_ERROR, "Authentication failed");
        }
        if (isNetworkError(detail)) {
            return GitResult.of(GitResult.Status.NETWORK_ERROR, "Network error");
        }
        return GitResult.of(GitResult.Status.ERROR,
                detail.isBlank() ? operation : operation + ": " + detail);
    }

    private void restoreRemote(Path dir, String previousUrl) {
        if (previousUrl == null) {
            run(dir, "remote", "remove", "origin");
        } else {
            run(dir, "remote", "set-url", "origin", previousUrl);
        }
    }

    /** Returns a blocking result when a branch operation would be unsafe. */
    private GitResult branchOperationReadiness(Path dir) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!supportsBranchOperations(dir)) {
            return GitResult.of(GitResult.Status.BRANCH_SCOPE_UNSUPPORTED,
                    "Branch operations require the vault to be the repository root");
        }
        Proc dirty = run(dir, "status", "--porcelain");
        if (!dirty.success()) {
            return resultForFailure("Could not inspect working tree", dirty);
        }
        if (!dirty.out().isBlank()) {
            return GitResult.of(GitResult.Status.DIRTY_WORKTREE,
                    "Commit, stash or discard changes before switching branches");
        }
        return null;
    }

    private boolean hasStagedChangesOutsideVault(Path dir) {
        Path repositoryRoot = repositoryRoot(dir);
        if (repositoryRoot == null) {
            return true;
        }
        Proc staged = run(repositoryRoot, "diff", "--cached", "--name-only", "-z");
        if (!staged.success()) {
            return true;
        }
        String prefix = repositoryRelativePrefix(dir);
        if (prefix.isEmpty()) {
            return false;
        }
        for (String path : staged.out().split("\u0000", -1)) {
            if (!path.isBlank() && !path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String repositoryRelativePrefix(Path dir) {
        Proc prefix = run(dir, "rev-parse", "--show-prefix");
        if (!prefix.success()) {
            return "";
        }
        String value = prefix.out().trim().replace('\\', '/');
        return value.isEmpty() || value.endsWith("/") ? value : value + "/";
    }

    private Path repositoryRoot(Path dir) {
        Proc root = run(dir, "rev-parse", "--show-toplevel");
        if (!root.success() || root.out().trim().isEmpty()) {
            return null;
        }
        try {
            return Path.of(root.out().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Returns a blocking result when a tracked nested repository has its own
     * uncommitted work. The parent repository can stage only its gitlink commit,
     * never the nested repository's files.
     */
    private GitResult dirtyNestedRepositoryResult(Path dir) {
        List<String> paths = dirtyNestedRepositoryPaths(dir);
        return paths.isEmpty() ? null : nestedRepositoryDirtyResult(paths);
    }

    private static GitResult nestedRepositoryDirtyResult(List<String> paths) {
        return GitResult.of(GitResult.Status.NESTED_REPOSITORY_DIRTY,
                "Nested Git repository has uncommitted changes: " + String.join(", ", paths)
                        + ". Commit its changes from that repository first");
    }

    private List<String> dirtyNestedRepositoryPaths(Path dir) {
        List<String> paths = new ArrayList<>();
        Proc status = run(dir, "status", "--porcelain=v1", "-z", "--untracked-files=all", "--", ".");
        if (!status.success()) {
            return paths;
        }
        String[] entries = status.out().split("\u0000", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.length() < 4 || entry.charAt(1) == ' ') {
                continue;
            }
            String code = entry.substring(0, 2);
            String repositoryPath = entry.substring(3);
            if (isRenameOrCopy(code) && ++index >= entries.length) {
                logger.warning("Ignoring incomplete Git nested-repository status entry");
                break;
            }
            String path = vaultRelativePath(dir, repositoryPath);
            if (path != null && isDirtyNestedRepository(dir, path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private boolean isDirtyNestedRepository(Path dir, String relativePath) {
        String path = validVaultRelativePath(relativePath);
        if (path == null || !isGitlink(dir, path)) {
            return false;
        }
        Path nested = dir.resolve(path);
        if (!isRepository(nested)) {
            return false;
        }
        Proc status = run(nested, "status", "--porcelain", "--untracked-files=all");
        return status.success() && !status.out().isBlank();
    }

    private boolean isGitlink(Path dir, String relativePath) {
        Proc entry = run(dir, "ls-files", "--stage", "--", relativePath);
        return entry.success() && entry.out().lines().anyMatch(line -> line.startsWith("160000 "));
    }

    /**
     * Converts a porcelain path, which Git defines relative to the repository root,
     * into the vault-relative form accepted by operations run from {@code dir}.
     *
     * <p>This matters when a vault is a directory within a larger repository: Git
     * reports {@code vault/note.md}, whereas {@code git add} executed in the vault
     * must receive {@code note.md}.</p>
     */
    private String vaultRelativePath(Path dir, String repositoryPath) {
        Path root = repositoryRoot(dir);
        if (root == null || repositoryPath == null || repositoryPath.isBlank()) {
            return null;
        }
        try {
            Path vault = dir.toRealPath();
            Path document = root.toRealPath().resolve(repositoryPath).normalize();
            if (!document.startsWith(vault)) {
                return null;
            }
            String relative = vault.relativize(document).toString().replace('\\', '/');
            return validVaultRelativePath(relative);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String validVaultRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            return null;
        }
        String value = normalized.toString().replace('\\', '/');
        return value.equals(".") ? null : value;
    }

    /** Serializes Git subprocesses app-wide so two never contend for the index lock. */
    private static final Object GIT_LOCK = new Object();

    /**
     * Cancels the currently running Git command, including its helper processes such as
     * {@code ssh} and {@code git pack-objects}. This prevents a cancelled transfer from
     * continuing in the background after its parent process exits.
     *
     * @return {@code true} when there was an active Git command to cancel
     */
    public boolean cancelActiveOperation() {
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

    /**
     * Runs {@code git -c core.quotePath=false <args>} in {@code dir} (or the
     * process working dir when {@code dir} is null), draining both streams.
     *
     * <p>All invocations are serialized inside Jylos. A lock held by another Git
     * client is reported to the user and is never removed by the application.</p>
    */
    private Proc run(Path dir, String... args) {
        return run(dir, LOCAL_OPERATION_TIMEOUT, null, args);
    }

    /**
     * Runs a network operation without an artificial timeout. Transfers can legitimately
     * take longer than a fixed limit for a large vault or a slow connection; callers can
     * explicitly cancel through {@link #cancelActiveOperation()} instead.
     */
    private Proc runRemote(Path dir, String... args) {
        return run(dir, null, null, args);
    }

    /** Runs a network operation and exposes Git's stderr progress output to the caller. */
    private Proc runRemote(Path dir, Consumer<String> progressListener, String... args) {
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
            // Force a stable C locale so git's messages are in English: the status
            // classification below (nothing-to-commit, conflict, rejected, auth, network)
            // matches English phrases and would otherwise misfire on localized systems.
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
