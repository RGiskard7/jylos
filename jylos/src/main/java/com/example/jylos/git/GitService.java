package com.example.jylos.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

/**
 * Git-backed vault synchronization, implemented by driving the system {@code git}
 * CLI (the same approach Tolaria uses), so there is no native library to bundle.
 *
 * <h3>Operations</h3>
 * init, status (modified count + ahead/behind vs. upstream), commit, pull, push,
 * a one-shot {@link #sync(Path, String)} (commit → pull → push) and remote setup.
 *
 * <h3>Threading</h3>
 * Every method blocks on the {@code git} process; callers must invoke them off the
 * JavaFX Application Thread (e.g. in a {@code Task}). Output is drained on separate
 * threads to avoid pipe-buffer deadlocks.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public final class GitService {

    private static final Logger logger = LoggerConfig.getLogger(GitService.class);
    private static final int TIMEOUT_SECONDS = 120;

    private static final String DEFAULT_GITIGNORE = String.join("\n",
            "# Jylos / OS metadata",
            ".DS_Store",
            "Thumbs.db",
            ".trash/",
            "*.tmp",
            "*.bak",
            "");

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

    /**
     * Reads the vault's Git status. Performs a best-effort {@code fetch} when a
     * remote is configured so ahead/behind counts are current.
     */
    public GitStatus status(Path dir) {
        if (dir == null || !isRepository(dir)) {
            return GitStatus.none();
        }
        int modified = countModified(dir);
        boolean hasRemote = hasRemote(dir);
        String branch = run(dir, "branch", "--show-current").out().trim();

        int ahead = 0;
        int behind = 0;
        if (hasRemote) {
            run(dir, "fetch", "--quiet"); // best-effort, ignore failures (offline)
            Proc rev = run(dir, "rev-list", "--left-right", "--count", "HEAD...@{upstream}");
            if (rev.success()) {
                String[] parts = rev.out().trim().split("\\s+");
                if (parts.length >= 2) {
                    ahead = parseInt(parts[0]);
                    behind = parseInt(parts[1]);
                }
            }
        }
        return new GitStatus(true, hasRemote, branch, modified, ahead, behind);
    }

    private int countModified(Path dir) {
        Proc p = run(dir, "status", "--porcelain");
        if (!p.success()) {
            return 0;
        }
        int count = 0;
        for (String line : p.out().split("\n")) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRemote(Path dir) {
        Proc p = run(dir, "remote");
        return p.success() && !p.out().trim().isEmpty();
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
        run(dir, "add", ".");
        Proc commit = run(dir, "-c", "commit.gpgsign=false", "commit", "-m", "Initial vault setup");
        if (!commit.success() && !isNothingToCommit(commit.detail())) {
            return GitResult.of(GitResult.Status.ERROR, "Initial commit failed: " + commit.detail());
        }
        return GitResult.ok("Git initialized");
    }

    /** Adds (or updates) the {@code origin} remote URL. */
    public GitResult setRemote(Path dir, String url) {
        if (dir == null || url == null || url.isBlank()) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid remote URL");
        }
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc add = run(dir, "remote", "add", "origin", url.trim());
        if (!add.success()) {
            // origin may already exist → update it
            Proc set = run(dir, "remote", "set-url", "origin", url.trim());
            if (!set.success()) {
                return GitResult.of(GitResult.Status.ERROR, "Could not set remote: " + set.detail());
            }
        }
        return GitResult.ok("Remote configured");
    }

    // ── Commit / pull / push ──────────────────────────────────────────────────

    /** Stages everything and commits with {@code message}. */
    public GitResult commit(Path dir, String message) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc add = run(dir, "add", "-A");
        if (!add.success()) {
            return GitResult.of(GitResult.Status.ERROR, "git add failed: " + add.detail());
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
        return GitResult.of(GitResult.Status.ERROR, detail.isBlank() ? "git commit failed" : detail);
    }

    /** Pulls from the remote (no rebase, like Tolaria). */
    public GitResult pull(Path dir) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        Proc pull = run(dir, "pull", "--no-rebase");
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
        return GitResult.of(GitResult.Status.ERROR, "Pull failed: " + detail);
    }

    /** Pushes to the remote, classifying common failures. */
    public GitResult push(Path dir) {
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!hasRemote(dir)) {
            return GitResult.of(GitResult.Status.NO_REMOTE, "No remote configured");
        }
        Proc push = run(dir, "push");
        if (push.success()) {
            return GitResult.ok("Pushed");
        }
        String detail = push.detail();
        if (mentions(detail, "non-fast-forward", "[rejected]", "fetch first")) {
            return GitResult.of(GitResult.Status.REJECTED, "Push rejected — pull first");
        }
        if (mentions(detail, "no upstream", "set-upstream", "has no upstream")) {
            // First push of a new branch: set the upstream.
            String branch = run(dir, "branch", "--show-current").out().trim();
            if (!branch.isEmpty()) {
                Proc up = run(dir, "push", "--set-upstream", "origin", branch);
                if (up.success()) {
                    return GitResult.ok("Pushed");
                }
                detail = up.detail();
            }
        }
        if (isAuthError(detail)) {
            return GitResult.of(GitResult.Status.AUTH_ERROR, "Authentication failed");
        }
        if (isNetworkError(detail)) {
            return GitResult.of(GitResult.Status.NETWORK_ERROR, "Network error during push");
        }
        return GitResult.of(GitResult.Status.ERROR, "Push failed: " + detail);
    }

    /**
     * One-shot synchronization: commit local changes (if any), then pull and push
     * when a remote is configured. Stops and reports the first blocking failure.
     */
    public GitResult sync(Path dir, String commitMessage) {
        if (!isGitAvailable()) {
            return GitResult.of(GitResult.Status.GIT_UNAVAILABLE, "git is not installed");
        }
        if (!isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        GitResult commit = commit(dir, commitMessage);
        if (!commit.ok()) {
            return commit;
        }
        if (!hasRemote(dir)) {
            // Local-only history is still valuable; report as OK with a hint.
            return GitResult.of(GitResult.Status.NO_REMOTE, "Committed locally (no remote configured)");
        }
        GitResult pull = pull(dir);
        if (!pull.ok() && pull.status() != GitResult.Status.NO_REMOTE) {
            return pull;
        }
        return push(dir);
    }

    // ── Changes, staging & history ────────────────────────────────────────────

    /**
     * Lists uncommitted Markdown changes with best-effort line statistics
     * (added/deleted) and their staged state.
     */
    public List<GitChange> listChanges(Path dir) {
        List<GitChange> changes = new ArrayList<>();
        if (!isRepository(dir)) {
            return changes;
        }
        Proc status = run(dir, "status", "--porcelain", "--untracked-files=all");
        if (!status.success()) {
            return changes;
        }
        java.util.Map<String, int[]> stats = numstat(dir);
        for (String line : status.out().split("\n")) {
            if (line.length() < 4) {
                continue;
            }
            String code = line.substring(0, 2);
            String path = line.substring(3).trim();
            if (path.contains(" -> ")) {
                path = path.substring(path.indexOf(" -> ") + 4).trim();
            }
            // Track notes AND attachments (pdf/images) the vault surfaces, not just .md.
            if (!com.example.jylos.util.AttachmentType.isSupportedVaultFile(path)) {
                continue;
            }
            boolean untracked = code.equals("??");
            boolean staged = !untracked && code.charAt(0) != ' ';
            int added;
            int deleted;
            if (untracked) {
                added = countFileLines(dir.resolve(path));
                deleted = 0;
            } else {
                int[] s = stats.get(path);
                added = s != null ? s[0] : -1;
                deleted = s != null ? s[1] : -1;
            }
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            changes.add(new GitChange(path, fileName, statusLabel(code), added, deleted, staged));
        }
        return changes;
    }

    /** Stages a single path ({@code git add -- path}). */
    public GitResult stage(Path dir, String relativePath) {
        Proc p = run(dir, "add", "--", relativePath);
        return p.success() ? GitResult.ok("Staged")
                : GitResult.of(GitResult.Status.ERROR, "Stage failed: " + p.detail());
    }

    /** Unstages a single path ({@code git reset -q HEAD -- path}). */
    public GitResult unstage(Path dir, String relativePath) {
        Proc p = run(dir, "reset", "-q", "HEAD", "--", relativePath);
        return p.success() ? GitResult.ok("Unstaged")
                : GitResult.of(GitResult.Status.ERROR, "Unstage failed: " + p.detail());
    }

    /**
     * Returns recent commits across all branches (newest first).
     *
     * @param limit maximum number of commits
     */
    public List<GitCommit> history(Path dir, int limit) {
        List<GitCommit> commits = new ArrayList<>();
        if (!isRepository(dir)) {
            return commits;
        }
        Proc log = run(dir, "log", "--all", "--date=iso-strict",
                "--pretty=%H%x1f%h%x1f%an%x1f%aI%x1f%s%x1f%D", "-n", String.valueOf(Math.max(1, limit)));
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
        Proc diff = run(dir, "diff", "--numstat", "HEAD", "--");
        if (!diff.success()) {
            return map;
        }
        for (String line : diff.out().split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length >= 3) {
                int added = "-".equals(parts[0]) ? -1 : parseInt(parts[0]);
                int deleted = "-".equals(parts[1]) ? -1 : parseInt(parts[1]);
                map.put(parts[2].trim(), new int[] { added, deleted });
            }
        }
        return map;
    }

    private static int countFileLines(Path file) {
        try {
            return (int) Files.lines(file, StandardCharsets.UTF_8).count();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String statusLabel(String code) {
        if (code.equals("??")) {
            return "untracked";
        }
        char c = code.trim().isEmpty() ? ' ' : code.trim().charAt(0);
        return switch (c) {
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

    private static boolean isNothingToCommit(String detail) {
        return mentions(detail, "nothing to commit", "nothing added to commit", "no changes added");
    }

    /** True when git failed because the index lock could not be acquired. */
    private static boolean isLockError(String detail) {
        return mentions(detail, "index.lock");
    }

    /**
     * Removes a stale {@code .git/index.lock} left behind by an interrupted git
     * process. Safe because all our git calls are serialized via {@link #GIT_LOCK},
     * so a lock seen here is never held by one of our own running commands.
     *
     * @return {@code true} if a lock file existed and was deleted
     */
    private boolean clearStaleIndexLock(Path dir) {
        try {
            Path lock = dir.resolve(".git").resolve("index.lock");
            if (Files.exists(lock)) {
                Files.delete(lock);
                logger.log(Level.WARNING, "Removed stale Git index.lock at {0}", lock);
                return true;
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not remove stale index.lock", e);
        }
        return false;
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

    /** Serializes git subprocesses app-wide so two never contend for the index lock. */
    private static final Object GIT_LOCK = new Object();

    /**
     * Runs {@code git -c core.quotePath=false <args>} in {@code dir} (or the
     * process working dir when {@code dir} is null), draining both streams.
     *
     * <p>All git invocations are serialized and, if a command fails because a
     * stale {@code index.lock} is present (left by a previously interrupted git
     * process), the lock is removed and the command retried once.</p>
     */
    private Proc run(Path dir, String... args) {
        synchronized (GIT_LOCK) {
            Proc result = runOnce(dir, args);
            if (dir != null && !result.success() && isLockError(result.detail())
                    && clearStaleIndexLock(dir)) {
                result = runOnce(dir, args);
            }
            return result;
        }
    }

    private Proc runOnce(Path dir, String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-c");
        command.add("core.quotePath=false");
        for (String a : args) {
            command.add(a);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (dir != null) {
                pb.directory(dir.toFile());
            }
            Process process = pb.start();
            CompletableFuture<String> out = readAsync(process.getInputStream());
            CompletableFuture<String> err = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Proc(-1, "", "git timed out");
            }
            return new Proc(process.exitValue(), out.get(), err.get());
        } catch (IOException e) {
            return new Proc(-1, "", "Failed to run git: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Proc(-1, "", "git interrupted");
        } catch (Exception e) {
            return new Proc(-1, "", "git error: " + e.getMessage());
        }
    }

    private static CompletableFuture<String> readAsync(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = in) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }
}
