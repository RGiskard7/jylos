package com.example.jylos.git;

import java.nio.file.Path;
import java.util.List;

/**
 * Explicit index staging: stage/unstage a single vault-relative path, or
 * every change beneath the vault.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitStagingService {

    private final GitProcessRunner runner;
    private final GitRepositoryProbe probe;
    private final GitNestedRepositoryGuard nestedRepositoryGuard;

    GitStagingService(GitProcessRunner runner, GitRepositoryProbe probe,
            GitNestedRepositoryGuard nestedRepositoryGuard) {
        this.runner = runner;
        this.probe = probe;
        this.nestedRepositoryGuard = nestedRepositoryGuard;
    }

    /** Stages a single vault-relative path. */
    GitResult stage(Path dir, String relativePath) {
        String path = GitPathResolver.validVaultRelativePath(relativePath);
        if (path == null) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid vault-relative path");
        }
        if (nestedRepositoryGuard.isDirtyNestedRepository(dir, path)) {
            return GitNestedRepositoryGuard.nestedRepositoryDirtyResult(List.of(path));
        }
        Proc p = runner.run(dir, "add", "--", path);
        return p.success() ? GitResult.ok("Staged")
                : GitOutputClassifier.resultForFailure("Stage failed", p);
    }

    /** Unstages a single vault-relative path. */
    GitResult unstage(Path dir, String relativePath) {
        String path = GitPathResolver.validVaultRelativePath(relativePath);
        if (path == null) {
            return GitResult.of(GitResult.Status.ERROR, "Invalid vault-relative path");
        }
        Proc p = runner.run(dir, "reset", "-q", "HEAD", "--", path);
        return p.success() ? GitResult.ok("Unstaged")
                : GitOutputClassifier.resultForFailure("Unstage failed", p);
    }

    /** Stages every change below the vault root, without affecting a parent repository. */
    GitResult stageAll(Path dir) {
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc p = runner.run(dir, "add", "-A", "--", ".");
        if (!p.success()) {
            return GitOutputClassifier.resultForFailure("Stage all failed", p);
        }
        GitResult nestedRepository = nestedRepositoryGuard.dirtyNestedRepositoryResult(dir);
        return nestedRepository != null ? nestedRepository : GitResult.ok("Staged all");
    }

    /** Unstages every vault change, leaving working-tree edits untouched. */
    GitResult unstageAll(Path dir) {
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc p = runner.run(dir, "reset", "-q", "HEAD", "--", ".");
        return p.success() ? GitResult.ok("Unstaged all")
                : GitOutputClassifier.resultForFailure("Unstage all failed", p);
    }
}
