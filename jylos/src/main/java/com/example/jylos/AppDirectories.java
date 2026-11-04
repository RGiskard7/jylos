package com.example.jylos;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Decides where Jylos keeps its files, following each platform's convention for an
 * installed application. Pure: everything it looks at comes in through {@link Environment},
 * so every case can be tested without touching the real machine.
 */
public final class AppDirectories {

    /** File next to the Windows portable launcher that keeps data beside the app. */
    public static final String PORTABLE_MARKER = "jylos.portable";

    public enum Os {
        WINDOWS, MAC, LINUX;

        static Os from(String osName) {
            String name = osName == null ? "" : osName.toLowerCase();
            if (name.contains("win")) {
                return WINDOWS;
            }
            if (name.contains("mac")) {
                return MAC;
            }
            return LINUX;
        }
    }

    public enum Mode {
        /** {@code -Djylos.data.dir} was given. */
        OVERRIDE,
        /** Run from Maven, the IDE or the uber-JAR (no jpackage launcher). */
        DEVELOPMENT,
        /** The Windows portable ZIP: data next to the launcher. */
        PORTABLE,
        /** An installed package (.exe, .msi, .dmg, .deb, .rpm, Arch). */
        INSTALLED
    }

    /**
     * Where each kind of file goes.
     *
     * @param dataRoot user data: data/, backups/, plugins/, themes/, snippets/, history/
     * @param logsDir  log files
     * @param cacheDir re-creatable files (downloaded updates)
     */
    public record Layout(Mode mode, Path dataRoot, Path logsDir, Path cacheDir) {
    }

    /**
     * What the resolution depends on.
     *
     * @param jpackageAppPath value of {@code jpackage.app-path}, set by jpackage's launcher; null otherwise
     * @param dataDirOverride value of {@code jylos.data.dir}; null when not given
     * @param canWriteDir     whether a directory exists and is writable, or can be created
     * @param exists          whether a path exists
     */
    public record Environment(Os os, String appName, Path userHome, Path userDir, Map<String, String> env,
            String jpackageAppPath, String dataDirOverride, Predicate<Path> canWriteDir, Predicate<Path> exists) {
    }

    private AppDirectories() {
    }

    public static Layout resolve(Environment env) {
        if (env.dataDirOverride() != null && !env.dataDirOverride().isBlank()) {
            return singleFolder(Mode.OVERRIDE, Path.of(env.dataDirOverride()));
        }
        if (env.jpackageAppPath() != null && !env.jpackageAppPath().isBlank()) {
            Path launcherDir = Path.of(env.jpackageAppPath()).toAbsolutePath().getParent();
            if (launcherDir != null && env.exists().test(launcherDir.resolve(PORTABLE_MARKER))) {
                return singleFolder(Mode.PORTABLE, launcherDir);
            }
            return platformFolders(Mode.INSTALLED, env);
        }
        // Development and the uber-JAR keep the historical behaviour: the working
        // directory (the repo's jylos/ folder when developing), unless it is read-only.
        if (env.canWriteDir().test(env.userDir().resolve("data"))) {
            return singleFolder(Mode.DEVELOPMENT, env.userDir());
        }
        return platformFolders(Mode.DEVELOPMENT, env);
    }

    /** Everything under one folder: data/, logs/ and updates/ side by side. */
    private static Layout singleFolder(Mode mode, Path root) {
        return new Layout(mode, root, root.resolve("logs"), root);
    }

    private static Layout platformFolders(Mode mode, Environment env) {
        Path home = env.userHome();
        String name = env.appName();
        return switch (env.os()) {
            case WINDOWS -> {
                // Roaming: the user's data, which follows them between machines in a
                // domain. Local: per-machine files such as logs and caches.
                Path roaming = envDir(env, "APPDATA", home.resolve("AppData").resolve("Roaming"));
                Path local = envDir(env, "LOCALAPPDATA", home.resolve("AppData").resolve("Local"));
                yield new Layout(mode, roaming.resolve(name), local.resolve(name).resolve("logs"),
                        local.resolve(name).resolve("cache"));
            }
            case MAC -> {
                Path library = home.resolve("Library");
                yield new Layout(mode, library.resolve("Application Support").resolve(name),
                        library.resolve("Logs").resolve(name), library.resolve("Caches").resolve(name));
            }
            case LINUX -> {
                // XDG Base Directory: application folders are conventionally lowercase.
                String dirName = name.toLowerCase();
                Path data = envDir(env, "XDG_DATA_HOME", home.resolve(".local").resolve("share"));
                Path state = envDir(env, "XDG_STATE_HOME", home.resolve(".local").resolve("state"));
                Path cache = envDir(env, "XDG_CACHE_HOME", home.resolve(".cache"));
                yield new Layout(mode, data.resolve(dirName), state.resolve(dirName).resolve("logs"),
                        cache.resolve(dirName));
            }
        };
    }

    /** An absolute directory from an environment variable, or {@code fallback}. */
    static Path envDir(Environment env, String variable, Path fallback) {
        String value = env.env().get(variable);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Path path = Path.of(value);
        // XDG: "If an implementation encounters a relative path in any of these
        // variables it should consider the path invalid and ignore it."
        return path.isAbsolute() ? path : fallback;
    }
}
