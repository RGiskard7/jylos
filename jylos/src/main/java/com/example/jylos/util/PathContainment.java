package com.example.jylos.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Keeps vault-relative references (note ids, image sources) inside their root directory.
 *
 * <p>A filesystem vault identifies a note by its path relative to the vault root, and that
 * id can come from outside the app (an MCP client, a plugin, a note's own Markdown). Without
 * a containment check, {@code ../} segments or an absolute path would let such a reference
 * read or overwrite files anywhere the user can write.</p>
 *
 * <p>The check is lexical ({@link Path#normalize()}), not {@code toRealPath()}: symlinked
 * folders inside a vault keep working, as they do in other Markdown vault apps.</p>
 */
public final class PathContainment {

    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*");

    private PathContainment() {
    }

    /**
     * Resolves {@code relative} against {@code root}, returning empty when the result would
     * leave the root. Backslashes are treated as separators, absolute paths and drive-letter
     * paths are rejected, and a {@code null}/empty reference resolves to the root itself.
     */
    public static Optional<Path> resolveInside(Path root, String relative) {
        if (root == null) {
            return Optional.empty();
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String reference = relative == null ? "" : relative.replace('\\', '/');
        if (reference.startsWith("/") || DRIVE_LETTER.matcher(reference).matches()) {
            return Optional.empty();
        }
        try {
            if (Path.of(reference).isAbsolute()) {
                return Optional.empty();
            }
            Path resolved = normalizedRoot.resolve(reference).normalize();
            return resolved.startsWith(normalizedRoot) ? Optional.of(resolved) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** Whether {@code candidate} is {@code root} itself or a path below it. */
    public static boolean isInside(Path root, Path candidate) {
        if (root == null || candidate == null) {
            return false;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return candidate.toAbsolutePath().normalize().startsWith(normalizedRoot);
    }
}
