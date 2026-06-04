package com.example.jylos.git;

/**
 * Immutable snapshot of a vault's Git state.
 *
 * @param repository whether the vault directory is a Git repository
 * @param hasRemote  whether an {@code origin}/remote is configured
 * @param branch     current branch name (empty if unknown / detached)
 * @param modified   number of uncommitted changes ({@code git status --porcelain})
 * @param ahead      commits the local branch is ahead of its upstream
 * @param behind     commits the local branch is behind its upstream
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public record GitStatus(
        boolean repository,
        boolean hasRemote,
        String branch,
        int modified,
        int ahead,
        int behind) {

    /** A non-repository / unavailable state. */
    public static GitStatus none() {
        return new GitStatus(false, false, "", 0, 0, 0);
    }

    /** True when there are local uncommitted changes. */
    public boolean isDirty() {
        return modified > 0;
    }

    /** True when local and remote diverge (something to push and/or pull). */
    public boolean needsSync() {
        return ahead > 0 || behind > 0;
    }
}
