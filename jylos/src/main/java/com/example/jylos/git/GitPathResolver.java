package com.example.jylos.git;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Resolves repository-root and vault-relative paths for a Git operation.
 * Shared by every {@code Git*Service} that needs to translate between
 * repository-relative porcelain paths and vault-relative paths.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitPathResolver {

    /** Resolved once per operation to translate repository paths into vault paths. */
    record RepositoryLayout(Path root, Path vault) {
    }

    private final GitProcessRunner runner;

    GitPathResolver(GitProcessRunner runner) {
        this.runner = runner;
    }

    Path repositoryRoot(Path dir) {
        Proc root = runner.run(dir, "rev-parse", "--show-toplevel");
        if (!root.success() || root.out().trim().isEmpty()) {
            return null;
        }
        try {
            return Path.of(root.out().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Resolves the repository root and vault path once for a Git operation. */
    RepositoryLayout repositoryLayout(Path dir) {
        Path root = repositoryRoot(dir);
        if (root == null || dir == null) {
            return null;
        }
        try {
            return new RepositoryLayout(root.toRealPath(), dir.toRealPath());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    String repositoryRelativePrefix(Path dir) {
        Proc prefix = runner.run(dir, "rev-parse", "--show-prefix");
        if (!prefix.success()) {
            return "";
        }
        String value = prefix.out().trim().replace('\\', '/');
        return value.isEmpty() || value.endsWith("/") ? value : value + "/";
    }

    /**
     * Converts a repository-relative porcelain path into the vault-relative path
     * accepted by operations executed from the vault.
     */
    static String vaultRelativePath(RepositoryLayout layout, String repositoryPath) {
        if (layout == null || repositoryPath == null || repositoryPath.isBlank()) {
            return null;
        }
        try {
            Path document = layout.root().resolve(repositoryPath).normalize();
            if (!document.startsWith(layout.vault())) {
                return null;
            }
            String relative = layout.vault().relativize(document).toString().replace('\\', '/');
            return validVaultRelativePath(relative);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String validVaultRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            return null;
        }
        String value = normalized.toString().replace('\\', '/');
        return value.equals(".") ? null : value;
    }
}
