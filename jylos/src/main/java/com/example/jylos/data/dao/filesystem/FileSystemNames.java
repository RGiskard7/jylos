package com.example.jylos.data.dao.filesystem;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Naming rules shared by the filesystem DAOs, so notes and folders follow the same ones.
 */
final class FileSystemNames {

    /**
     * Anything that is not a letter (any script), a combining mark (NFD accents), a digit,
     * a dot, a hyphen, an underscore or a space is unsafe in a file name on some platform.
     */
    private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}.\\-_ ]");

    private FileSystemNames() {
    }

    /**
     * Turns a user-facing title into a file or directory name: unsafe characters become
     * {@code _}, surrounding whitespace is dropped, and a name left empty falls back to
     * {@code fallback}.
     */
    static String sanitize(String title, String fallback) {
        String sanitized = UNSAFE_NAME_CHARS.matcher(title == null ? "" : title).replaceAll("_").trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    /**
     * Whether {@code path} is hidden <em>within</em> the vault: some segment of its path
     * relative to {@code root} starts with a dot ({@code .trash}, {@code .git},
     * {@code .obsidian}, {@code .jylos}). Directories above the vault are not considered, so
     * a vault that itself lives under a hidden directory (e.g. {@code ~/.config/Jylos/data})
     * still lists its content.
     */
    static boolean isHiddenWithinVault(Path root, Path path) {
        Path relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }
}
