package com.example.jylos.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Local branch listing, creation and switching. Branch operations are
 * intentionally unavailable for a vault nested inside a larger repository:
 * switching there would also switch files outside Jylos.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitBranchService {

    private final GitProcessRunner runner;
    private final GitRepositoryProbe probe;
    private final GitPathResolver pathResolver;
    private final GitStatusService statusService;

    GitBranchService(GitProcessRunner runner, GitRepositoryProbe probe, GitPathResolver pathResolver,
            GitStatusService statusService) {
        this.runner = runner;
        this.probe = probe;
        this.pathResolver = pathResolver;
        this.statusService = statusService;
    }

    /**
     * Lists local branches for a vault that is itself the Git working-tree root.
     */
    List<String> branches(Path dir) {
        if (!supportsBranchOperations(dir)) {
            return List.of();
        }
        Proc branches = runner.run(dir, "for-each-ref", "--format=%(refname:short)", "refs/heads");
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
    boolean supportsBranchOperations(Path dir) {
        if (dir == null || !probe.isRepository(dir)) {
            return false;
        }
        Path root = pathResolver.repositoryRoot(dir);
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
    GitResult createBranch(Path dir, String branch) {
        GitResult readiness = branchOperationReadiness(dir);
        if (readiness != null) {
            return readiness;
        }
        String name = branch != null ? branch.trim() : "";
        Proc validName = runner.run(dir, "check-ref-format", "--branch", name);
        if (!validName.success()) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid branch name");
        }
        Proc created = runner.run(dir, "switch", "-c", name);
        return created.success() ? GitResult.ok("Created and switched to " + name)
                : GitOutputClassifier.resultForFailure("Could not create branch", created);
    }

    /** Switches to an existing local branch without carrying uncommitted changes across it. */
    GitResult switchBranch(Path dir, String branch) {
        GitResult readiness = branchOperationReadiness(dir);
        if (readiness != null) {
            return readiness;
        }
        String name = branch != null ? branch.trim() : "";
        if (!branches(dir).contains(name)) {
            return GitResult.of(GitResult.Status.ERROR, "Unknown local branch: " + name);
        }
        if (name.equals(statusService.currentBranch(dir))) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "Already on " + name);
        }
        Proc switched = runner.run(dir, "switch", name);
        return switched.success() ? GitResult.ok("Switched to " + name)
                : GitOutputClassifier.resultForFailure("Could not switch branch", switched);
    }

    /** Returns a blocking result when a branch operation would be unsafe. */
    private GitResult branchOperationReadiness(Path dir) {
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        if (!supportsBranchOperations(dir)) {
            return GitResult.of(GitResult.Status.BRANCH_SCOPE_UNSUPPORTED,
                    "Branch operations require the vault to be the repository root");
        }
        Proc dirty = runner.run(dir, "status", "--porcelain");
        if (!dirty.success()) {
            return GitOutputClassifier.resultForFailure("Could not inspect working tree", dirty);
        }
        if (!dirty.out().isBlank()) {
            return GitResult.of(GitResult.Status.DIRTY_WORKTREE,
                    "Commit, stash or discard changes before switching branches");
        }
        return null;
    }
}
