package com.example.jylos.git;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cheap, foundational checks used by every {@code Git*Service}: whether the
 * {@code git} executable exists, and whether a directory is inside a Git
 * working tree.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitRepositoryProbe {

    private final GitProcessRunner runner;

    GitRepositoryProbe(GitProcessRunner runner) {
        this.runner = runner;
    }

    /** True if the {@code git} executable is available on PATH. */
    boolean isGitAvailable() {
        try {
            return runner.run(null, "--version").success();
        } catch (Exception e) {
            return false;
        }
    }

    /** True if {@code dir} is inside a Git working tree. */
    boolean isRepository(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        if (Files.isDirectory(dir.resolve(".git"))) {
            return true;
        }
        Proc p = runner.run(dir, "rev-parse", "--is-inside-work-tree");
        return p.success() && "true".equals(p.out().trim());
    }
}
