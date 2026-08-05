package com.example.jylos.git;

import java.nio.file.Path;

/**
 * Commits the vault files already staged in Git's index.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitCommitService {

    private final GitProcessRunner runner;
    private final GitRepositoryProbe probe;
    private final GitConfigHelper configHelper;
    private final GitNestedRepositoryGuard nestedRepositoryGuard;

    GitCommitService(GitProcessRunner runner, GitRepositoryProbe probe, GitConfigHelper configHelper,
            GitNestedRepositoryGuard nestedRepositoryGuard) {
        this.runner = runner;
        this.probe = probe;
        this.configHelper = configHelper;
        this.nestedRepositoryGuard = nestedRepositoryGuard;
    }

    /** Commits the vault files that are already staged in Git's index. */
    GitResult commit(Path dir, String message) {
        if (!probe.isRepository(dir)) {
            return GitResult.of(GitResult.Status.ERROR, "Not a Git repository");
        }
        Proc staged = runner.run(dir, "diff", "--cached", "--quiet", "--", ".");
        if (staged.code() == 0) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "No staged vault changes to commit");
        }
        if (staged.code() != 1) {
            return GitOutputClassifier.resultForFailure("Could not inspect staged changes", staged);
        }
        if (nestedRepositoryGuard.hasStagedChangesOutsideVault(dir)) {
            return GitResult.of(GitResult.Status.OUTSIDE_VAULT_STAGED,
                    "Staged changes outside this vault must be committed from a Git client first");
        }
        configHelper.ensureAuthor(dir);
        // Disable commit signing for app-generated commits: GUI apps often can't
        // reach a GPG/SSH signer non-interactively, which would otherwise fail every
        // commit on machines with commit.gpgsign=true.
        Proc commit = runner.run(dir, "-c", "commit.gpgsign=false", "commit", "-m", message);
        if (commit.success()) {
            return GitResult.ok("Committed");
        }
        String detail = commit.detail();
        if (GitOutputClassifier.isNothingToCommit(detail)) {
            return GitResult.of(GitResult.Status.NOTHING_TO_DO, "Nothing to commit");
        }
        return GitOutputClassifier.resultForFailure("Commit failed", commit);
    }
}
