package com.example.jylos.git;

import java.util.Locale;

/**
 * Pure text classification of Git subprocess output and porcelain status codes,
 * shared by every {@code Git*Service}. Stateless.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitOutputClassifier {

    private GitOutputClassifier() {
    }

    static GitResult resultForFailure(String operation, Proc process) {
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

    static boolean isNothingToCommit(String detail) {
        return mentions(detail, "nothing to commit", "nothing added to commit", "no changes added");
    }

    /** True when Git failed because another process holds the index lock. */
    static boolean isLockError(String detail) {
        return mentions(detail, "index.lock");
    }

    static boolean isAuthError(String detail) {
        return mentions(detail, "authentication failed", "could not read username",
                "permission denied", "403", "401", "invalid username or password");
    }

    static boolean isNetworkError(String detail) {
        return mentions(detail, "could not resolve host", "connection timed out", "network is unreachable",
                "failed to connect", "unable to access");
    }

    static boolean mentions(String text, String... needles) {
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

    /**
     * True for an unmerged (conflicted) porcelain code. These are the seven states
     * Git reports during a merge/rebase conflict; they require manual resolution and
     * must never be auto-staged or auto-resolved.
     */
    static boolean isConflict(String code) {
        return switch (code) {
            case "DD", "AU", "UD", "UA", "DU", "AA", "UU" -> true;
            default -> false;
        };
    }

    /** True when porcelain emits a second, original-path record for a rename or copy. */
    static boolean isRenameOrCopy(String code) {
        return code.length() == 2
                && (code.charAt(0) == 'R' || code.charAt(0) == 'C'
                        || code.charAt(1) == 'R' || code.charAt(1) == 'C');
    }

    static String statusLabel(char status) {
        return switch (status) {
            case 'A' -> "added";
            case 'D' -> "deleted";
            case 'R' -> "renamed";
            case 'C' -> "copied";
            default -> "modified";
        };
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
