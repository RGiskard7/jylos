package com.example.jylos.git;

/**
 * A commit entry from the repository history.
 *
 * @param hash      full commit hash
 * @param shortHash abbreviated hash
 * @param author    author name
 * @param date      author date (ISO-8601 string, as produced by {@code %aI})
 * @param message   commit subject line
 * @param refs      decoration with branch/tag refs (may be empty)
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public record GitCommit(
        String hash,
        String shortHash,
        String author,
        String date,
        String message,
        String refs) {

    /** Date portion ({@code yyyy-MM-dd}) for compact display. */
    public String shortDate() {
        return date != null && date.length() >= 10 ? date.substring(0, 10) : (date != null ? date : "");
    }
}
