package com.example.jylos.ui.theme;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.example.jylos.AppDataDirectory;

/**
 * Scans built-in and external theme descriptors.
 */
public final class ThemeCatalog {

    private static final Logger logger = Logger.getLogger(ThemeCatalog.class.getName());

    public record ThemeDescriptor(String id, String name, String source, String cssPath, boolean darkLike,
            String base) {

        public String resolveBaseVariant(String menuTheme, Supplier<String> systemDetector) {
            String configured = base != null ? base.trim().toLowerCase() : "";
            if ("system".equals(configured)) {
                return systemDetector.get();
            }
            if ("light".equals(configured) || "dark".equals(configured)) {
                return configured;
            }
            if ("system".equalsIgnoreCase(menuTheme)) {
                return systemDetector.get();
            }
            if ("dark".equalsIgnoreCase(menuTheme)) {
                return "dark";
            }
            if ("light".equalsIgnoreCase(menuTheme)) {
                return "light";
            }
            return darkLike ? "dark" : "light";
        }
    }

    public List<ThemeDescriptor> getAvailableThemes() {
        List<ThemeDescriptor> out = new ArrayList<>();
        out.add(new ThemeDescriptor("light", "Light", "builtin",
                "/com/example/jylos/ui/css/modern-theme.css", false, "light"));
        out.add(new ThemeDescriptor("dark", "Dark", "builtin",
                "/com/example/jylos/ui/css/dark-theme.css", true, "dark"));
        out.add(new ThemeDescriptor("system", "System", "builtin",
                "/com/example/jylos/ui/css/modern-theme.css", false, "system"));

        List<Path> baseDirs = List.of(
                Paths.get(AppDataDirectory.getBaseDirectory(), "themes"),
                Paths.get(System.getProperty("user.dir", "."), "themes"),
                Paths.get(System.getProperty("user.dir", "."), "..", "themes").normalize());

        Map<String, ThemeDescriptor> unique = new LinkedHashMap<>();
        for (Path baseDir : baseDirs) {
            if (baseDir == null || !Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
                continue;
            }
            try (var dirs = Files.list(baseDir).filter(Files::isDirectory)) {
                dirs.forEach(dir -> {
                    String dirName = dir.getFileName().toString();
                    if (dirName.startsWith("_") || dirName.startsWith(".")) {
                        return;
                    }
                    ThemeDescriptor descriptor = readThemeDescriptor(dir);
                    if (descriptor != null) {
                        unique.putIfAbsent(descriptor.id(), descriptor);
                    }
                });
            } catch (Exception e) {
                logger.warning("Could not read themes directory '" + baseDir + "': " + e.getMessage());
            }
        }

        List<ThemeDescriptor> external = new ArrayList<>(unique.values());
        external.sort(Comparator.comparing(ThemeDescriptor::name, String.CASE_INSENSITIVE_ORDER));
        out.addAll(external);
        return out;
    }

    public ThemeDescriptor findById(List<ThemeDescriptor> themes, String id) {
        if (themes == null || id == null || id.isBlank()) {
            return null;
        }
        for (ThemeDescriptor t : themes) {
            if (id.equals(t.id())) {
                return t;
            }
        }
        return null;
    }

    private ThemeDescriptor readThemeDescriptor(Path dir) {
        Path propsFile = dir.resolve("theme.properties");
        if (!Files.exists(propsFile)) {
            return null;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(propsFile.toFile())) {
            p.load(in);
        } catch (Exception e) {
            logger.warning("Invalid theme descriptor in " + dir + ": " + e.getMessage());
            return null;
        }

        String id = valueOrDefault(p.getProperty("id"), dir.getFileName().toString().trim());
        String name = valueOrDefault(p.getProperty("name"), id);
        String css = valueOrDefault(p.getProperty("css"), "theme.css");
        boolean darkLike = Boolean.parseBoolean(valueOrDefault(p.getProperty("darkLike"), "false"));
        String base = valueOrDefault(p.getProperty("base"), "auto");

        Path cssPath = dir.resolve(css).normalize();
        if (!Files.exists(cssPath) || !Files.isRegularFile(cssPath)) {
            logger.warning("Theme '" + id + "' ignored (css not found): " + cssPath);
            return null;
        }
        return new ThemeDescriptor(id, name, "external", cssPath.toUri().toString(), darkLike, base);
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
