package com.example.jylos.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Shows a file or a folder in the system's file manager (Finder, Explorer, the desktop's own
 * on Linux).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class FileManager {

    private FileManager() {
    }

    /** Shows {@code file} selected inside its folder; on Linux, opens its folder. */
    public static void reveal(Path file) throws IOException {
        new ProcessBuilder(revealCommand(System.getProperty("os.name", ""), file.toAbsolutePath())).start();
    }

    /** Opens {@code folder}. */
    public static void open(Path folder) throws IOException {
        new ProcessBuilder(openCommand(System.getProperty("os.name", ""), folder.toAbsolutePath())).start();
    }

    static List<String> revealCommand(String osName, Path file) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return List.of("open", "-R", file.toString());
        }
        if (os.contains("win")) {
            return List.of("explorer.exe", "/select," + file);
        }
        Path folder = file.getParent();
        return List.of("xdg-open", (folder != null ? folder : file).toString());
    }

    static List<String> openCommand(String osName, Path folder) {
        String os = osName.toLowerCase(Locale.ROOT);
        String opener = os.contains("mac") ? "open" : os.contains("win") ? "explorer.exe" : "xdg-open";
        return List.of(opener, folder.toString());
    }
}
