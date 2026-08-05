package com.example.jylos.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

/**
 * Ensures a repository has a local author identity and a default
 * {@code .gitignore} before Jylos creates commits in it. Shared by
 * {@link GitRemoteSyncService#init} and {@link GitCommitService#commit}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
final class GitConfigHelper {

    private static final Logger logger = LoggerConfig.getLogger(GitConfigHelper.class);
    private static final String DEFAULT_GITIGNORE = String.join("\n",
            "# Jylos / OS metadata",
            ".DS_Store",
            "Thumbs.db",
            ".trash/",
            "*.tmp",
            "*.bak");

    private final GitProcessRunner runner;

    GitConfigHelper(GitProcessRunner runner) {
        this.runner = runner;
    }

    void ensureAuthor(Path dir) {
        ensureConfig(dir, "user.name", "Jylos");
        ensureConfig(dir, "user.email", "vault@jylos.local");
    }

    private void ensureConfig(Path dir, String key, String fallback) {
        Proc existing = runner.run(dir, "config", "--local", key);
        if (existing.success() && !existing.out().trim().isEmpty()) {
            return;
        }
        runner.run(dir, "config", "--local", key, fallback);
    }

    void ensureGitignore(Path dir) {
        Path gitignore = dir.resolve(".gitignore");
        if (Files.exists(gitignore)) {
            return;
        }
        try {
            Files.writeString(gitignore, DEFAULT_GITIGNORE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not write .gitignore", e);
        }
    }
}
