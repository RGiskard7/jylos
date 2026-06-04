package com.example.jylos.git;

/**
 * A single uncommitted change in the vault working tree.
 *
 * @param relativePath path relative to the vault root (forward slashes)
 * @param fileName     display file name (last path segment)
 * @param status       short human label: "modified", "added", "deleted", "renamed", "untracked"
 * @param added        added line count (best-effort; -1 if unknown/binary)
 * @param deleted      deleted line count (best-effort; -1 if unknown/binary)
 * @param staged       whether the change is staged in the index
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public record GitChange(
        String relativePath,
        String fileName,
        String status,
        int added,
        int deleted,
        boolean staged) {

    /** Note title shown in the changes list (file name without the {@code .md} extension). */
    public String displayTitle() {
        String name = fileName != null ? fileName : "";
        return name.toLowerCase().endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }
}
