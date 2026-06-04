package com.example.jylos.git;

/**
 * Outcome of a Git operation, with a categorized status and a user-facing message.
 *
 * @param status  outcome category
 * @param message human-readable detail (localized by the caller where relevant)
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public record GitResult(Status status, String message) {

    /** Categorized outcome of a Git operation. */
    public enum Status {
        OK,
        NOTHING_TO_DO,
        NO_REMOTE,
        REJECTED,      // non-fast-forward; a pull is needed first
        AUTH_ERROR,
        NETWORK_ERROR,
        CONFLICT,
        GIT_UNAVAILABLE,
        ERROR
    }

    public boolean ok() {
        return status == Status.OK || status == Status.NOTHING_TO_DO;
    }

    public static GitResult ok(String message) {
        return new GitResult(Status.OK, message);
    }

    public static GitResult of(Status status, String message) {
        return new GitResult(status, message);
    }
}
