package com.example.jylos;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.example.jylos.AppDirectories.Environment;
import com.example.jylos.AppDirectories.Layout;
import com.example.jylos.AppDirectories.Mode;
import com.example.jylos.AppDirectories.Os;

/**
 * Removes the per-user install older Windows builds made in {@code %LOCALAPPDATA%\Jylos},
 * once its data has been migrated out of it.
 *
 * <p>Those builds kept their data inside the install folder, and the uninstaller jpackage
 * generates for per-user installs deletes that whole folder. Installing a newer per-user
 * build over one first runs that uninstaller, so the data was gone before the new version
 * could move it. Newer builds install for the whole machine, which Windows Installer does
 * not treat as an upgrade of a per-user install: the old one stays, the new build migrates
 * its data on first start ({@link LegacyDataMigration}) and only then uninstalls it here,
 * provided nothing of the user's is left in the folder.</p>
 */
public final class LegacyWindowsInstall {

    private static final String UNINSTALL_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall";
    private static final Pattern PRODUCT_CODE = Pattern.compile("\\\\(\\{[0-9A-Fa-f-]{36}\\})\\s*$");
    private static final Set<String> PROGRAM_ENTRIES = Set.of("app", "runtime", "logs", "cache", "plugins");

    private LegacyWindowsInstall() {
    }

    /**
     * On an installed Windows build that is not itself in {@code %LOCALAPPDATA%\Jylos}:
     * uninstalls the old per-user install there, if only the program is left in it.
     *
     * @return what happened, for the log
     */
    static List<String> removeIfMigrated(Environment env, Layout layout, Set<String> bundledPluginJars) {
        if (env.os() != Os.WINDOWS || layout.mode() != Mode.INSTALLED) {
            return List.of();
        }
        Path local = AppDirectories.envDir(env, "LOCALAPPDATA", env.userHome().resolve("AppData").resolve("Local"));
        Path oldInstall = local.resolve(env.appName());
        Path launcherDir = Path.of(env.jpackageAppPath()).toAbsolutePath().getParent();
        if (oldInstall.equals(launcherDir) || !Files.isRegularFile(oldInstall.resolve(env.appName() + ".exe"))) {
            return List.of();
        }
        if (!onlyProgramLeft(oldInstall, env.appName(), bundledPluginJars)) {
            return List.of("Old install in " + oldInstall + " still holds files that were not migrated;"
                    + " not uninstalling it, as its uninstaller deletes the whole folder");
        }
        List<String> messages = new ArrayList<>();
        try {
            for (String productCode : productCodes(run(List.of("reg", "query", UNINSTALL_KEY, "/s", "/f",
                    env.appName(), "/d", "/e"), 30), env.appName())) {
                run(List.of("msiexec", "/x", productCode, "/qn", "/norestart"), 300);
                messages.add("Uninstalled the old per-user install " + productCode + " in " + oldInstall);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            messages.add("Could not uninstall the old install in " + oldInstall + ": " + e.getMessage());
        }
        return messages;
    }

    /**
     * Product codes of the uninstall entries named exactly {@code appName} in the output of
     * {@code reg query HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall /s /f <appName> /d /e}.
     */
    static List<String> productCodes(String regQueryOutput, String appName) {
        List<String> codes = new ArrayList<>();
        String currentCode = null;
        for (String line : regQueryOutput.split("\\R")) {
            if (line.startsWith("HKEY_")) {
                Matcher matcher = PRODUCT_CODE.matcher(line);
                // Only MSI installs are keyed by their product code.
                currentCode = matcher.find() ? matcher.group(1) : null;
                continue;
            }
            String[] fields = line.trim().split("\\s{2,}");
            if (currentCode != null && fields.length == 3 && fields[0].equals("DisplayName")
                    && fields[2].equals(appName)) {
                codes.add(currentCode);
                currentCode = null;
            }
        }
        return codes;
    }

    /**
     * Whether {@code installDir} holds an old install and nothing of the user's: its
     * uninstaller deletes the whole folder.
     */
    static boolean onlyProgramLeft(Path installDir, String appName, Set<String> bundledPluginJars) {
        if (!Files.isRegularFile(installDir.resolve(appName + ".exe"))) {
            return false;
        }
        try (Stream<Path> entries = Files.list(installDir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                String name = entry.getFileName().toString();
                if (name.equals(appName + ".exe") || name.equals(appName + ".ico")
                        || name.equals(LegacyDataMigration.MIGRATED_MARKER)) {
                    continue;
                }
                if (!Files.isDirectory(entry) || !PROGRAM_ENTRIES.contains(name)) {
                    return false;
                }
                if (name.equals("plugins") && !onlyBundledPlugins(entry, bundledPluginJars)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean onlyBundledPlugins(Path plugins, Set<String> bundledPluginJars) throws IOException {
        try (Stream<Path> files = Files.list(plugins)) {
            return files.map(f -> f.getFileName().toString())
                    .allMatch(n -> bundledPluginJars.contains(n) || n.equals(".bundled-plugins-copied"));
        }
    }

    private static String run(List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException(String.join(" ", command) + " did not finish");
        }
        return output;
    }
}
