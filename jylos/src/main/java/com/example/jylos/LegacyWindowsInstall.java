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

    /**
     * The upgrade code of every installer up to 2.5.7 (scripts/package-windows.ps1 changed
     * it in 2.5.8). Windows Installer lists the products installed with it; the per-user
     * ones are not in the registry's Uninstall keys, so they are looked up through its API.
     */
    private static final String LEGACY_UPGRADE_CODE = "{6F1D2F7E-9B3A-4C5D-8E2F-3A7B1C9D4E60}";
    private static final String RELATED_PRODUCTS_QUERY = "$i = New-Object -ComObject WindowsInstaller.Installer; "
            + "foreach ($p in $i.RelatedProducts('" + LEGACY_UPGRADE_CODE + "')) { "
            + "$p + \"`t\" + $i.ProductInfo($p, 'AssignmentType') }";
    /** {@code <product code><TAB><assignment type>}; 0 = per-user, 1 = per-machine. */
    private static final Pattern RELATED_PRODUCT = Pattern.compile("(\\{[0-9A-Fa-f-]{36}\\})\\s+([01])");
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
            List<String> productCodes = perUserProductCodes(run(List.of("powershell", "-NoProfile",
                    "-NonInteractive", "-Command", RELATED_PRODUCTS_QUERY), 60));
            if (productCodes.isEmpty()) {
                messages.add("No old per-user install is registered with Windows Installer; leaving " + oldInstall);
            }
            for (String productCode : productCodes) {
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

    /** Product codes of the related products installed for this user (assignment type 0). */
    static List<String> perUserProductCodes(String relatedProducts) {
        List<String> codes = new ArrayList<>();
        for (String line : relatedProducts.split("\\R")) {
            Matcher matcher = RELATED_PRODUCT.matcher(line.trim());
            if (matcher.matches() && matcher.group(2).equals("0")) {
                codes.add(matcher.group(1));
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
