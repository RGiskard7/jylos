package com.example.jylos.compat.obsidian;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.FolderTemplate;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.data.models.VaultSettings.Templater;
import com.example.jylos.data.models.VaultSettings.Templates;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Obsidian's settings of a vault, read from its {@code .obsidian} folder, which is never
 * written, as Jylos' own ({@link VaultSettings}): the templates folder and its formats
 * ({@code templates.json}), the daily note ({@code daily-notes.json}), where new notes go
 * ({@code app.json}) and Templater's ({@code plugins/templater-obsidian/data.json}). Each is
 * taken as Obsidian has it, its defaults where a file or a key is missing, and only for the
 * plugins Obsidian has on ({@code core-plugins.json}, {@code community-plugins.json}); the rest
 * of Jylos' settings stay as they are.
 *
 * <p>The files are Obsidian's own, not documented: one not in the shape Obsidian writes is
 * reported as not understood rather than read as having no settings.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ObsidianSettings {

    /**
     * One setting that would change.
     *
     * @param setting  which, as {@code templates.folder}, {@code newNotes.location}…
     * @param current  its value in Jylos now
     * @param proposed its value in Obsidian
     */
    public record Change(String setting, Object current, Object proposed) {
    }

    /**
     * @param settings Jylos' settings with Obsidian's
     * @param changes  what differs from Jylos' settings now, in the order of Preferences
     */
    public record Proposal(VaultSettings settings, List<Change> changes) {
    }

    private static final String TEMPLATER = "templater-obsidian";

    private ObsidianSettings() {
    }

    /**
     * Obsidian's settings of the vault at {@code vaultRoot} over {@code current}; empty when
     * the vault has no {@code .obsidian} folder.
     *
     * @throws IllegalArgumentException when one of its files is not in the shape Obsidian writes
     */
    public static Optional<Proposal> propose(Path vaultRoot, VaultSettings current) {
        Path config = vaultRoot.resolve(".obsidian");
        if (!Files.isDirectory(config)) {
            return Optional.empty();
        }
        Set<String> core = corePlugins(config.resolve("core-plugins.json"));
        Set<String> community = new LinkedHashSet<>();
        JsonArray enabled = arrayFile(config.resolve("community-plugins.json"));
        if (enabled != null) {
            enabled.forEach(id -> community.add(id.isJsonPrimitive() ? id.getAsString() : ""));
        }
        JsonObject templater = community.contains(TEMPLATER)
                ? objectFile(config.resolve("plugins").resolve(TEMPLATER).resolve("data.json")) : null;

        VaultSettings settings = current;
        if (core.contains("templates")) {
            JsonObject templates = objectFile(config.resolve("templates.json"));
            String folder = text(templates, "folder");
            if (folder.isEmpty() && templater != null) {
                folder = text(templater, "templates_folder");
            }
            settings = settings.withTemplates(new Templates(path(folder), text(templates, "dateFormat"),
                    text(templates, "timeFormat")));
        }
        if (core.contains("daily-notes")) {
            JsonObject daily = objectFile(config.resolve("daily-notes.json"));
            settings = settings.withDaily(new DailyNotes(path(text(daily, "folder")), text(daily, "format"),
                    path(text(daily, "template"))));
        }
        JsonObject app = objectFile(config.resolve("app.json"));
        settings = settings.withNewNotes(switch (text(app, "newFileLocation")) {
            case "current" -> new NewNotes(NewNoteLocation.CURRENT, "");
            case "folder" -> new NewNotes(NewNoteLocation.FOLDER, path(text(app, "newFileFolderPath")));
            default -> new NewNotes(NewNoteLocation.ROOT, "");
        });
        if (templater != null) {
            List<FolderTemplate> byFolder = new ArrayList<>();
            JsonArray folders = templater.get("folder_templates") instanceof JsonArray list ? list : new JsonArray();
            if (flag(templater, "enable_folder_templates")) {
                for (JsonElement entry : folders) {
                    if (entry.isJsonObject() && !text(entry.getAsJsonObject(), "template").isBlank()) {
                        byFolder.add(new FolderTemplate(path(text(entry.getAsJsonObject(), "folder")),
                                path(text(entry.getAsJsonObject(), "template"))));
                    }
                }
            }
            settings = settings.withTemplater(new Templater(true, flag(templater, "trigger_on_file_creation"), byFolder));
        }
        return Optional.of(new Proposal(settings, changes(current, settings)));
    }

    private static List<Change> changes(VaultSettings before, VaultSettings after) {
        List<Change> changes = new ArrayList<>();
        add(changes, "templates.folder", before.templates().folder(), after.templates().folder());
        add(changes, "templates.dateFormat", before.templates().dateFormat(), after.templates().dateFormat());
        add(changes, "templates.timeFormat", before.templates().timeFormat(), after.templates().timeFormat());
        add(changes, "daily.folder", before.daily().folder(), after.daily().folder());
        add(changes, "daily.format", before.daily().format(), after.daily().format());
        add(changes, "daily.template", before.daily().template(), after.daily().template());
        add(changes, "newNotes.location", before.newNotes().location(), after.newNotes().location());
        add(changes, "newNotes.folder", before.newNotes().folder(), after.newNotes().folder());
        add(changes, "templater.enabled", before.templater().enabled(), after.templater().enabled());
        add(changes, "templater.triggerOnCreate", before.templater().triggerOnCreate(), after.templater().triggerOnCreate());
        add(changes, "templater.folderTemplates", before.templater().folderTemplates(), after.templater().folderTemplates());
        return List.copyOf(changes);
    }

    private static void add(List<Change> changes, String setting, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new Change(setting, before, after));
        }
    }

    /** The core plugins on: an object of {@code id: true} or, as older versions write it, a list of ids. */
    private static Set<String> corePlugins(Path file) {
        Set<String> on = new LinkedHashSet<>();
        if (!Files.isRegularFile(file)) {
            on.add("templates"); // Obsidian's defaults
            on.add("daily-notes");
            return on;
        }
        JsonElement root = read(file);
        if (root.isJsonArray()) {
            root.getAsJsonArray().forEach(id -> on.add(id.isJsonPrimitive() ? id.getAsString() : ""));
        } else if (root.isJsonObject()) {
            root.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()
                        && entry.getValue().getAsBoolean()) {
                    on.add(entry.getKey());
                }
            });
        } else {
            throw new IllegalArgumentException("Not Obsidian's core plugins: " + file);
        }
        return on;
    }

    /** The object a file holds; an empty one when there is no file. */
    private static JsonObject objectFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }
        JsonElement root = read(file);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Not Obsidian's settings: " + file);
        }
        return root.getAsJsonObject();
    }

    private static JsonArray arrayFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JsonElement root = read(file);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("Not Obsidian's plugin list: " + file);
        }
        return root.getAsJsonArray();
    }

    private static JsonElement read(Path file) {
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || root.isJsonNull()) {
                throw new IllegalArgumentException("Empty: " + file);
            }
            return root;
        } catch (IOException | JsonParseException e) {
            throw new IllegalArgumentException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    /** A path as Obsidian normalizes it: no slashes at either end ({@code "/"} is the root, {@code ""}). */
    private static String path(String value) {
        String clean = value.strip().replace('\\', '/').replaceAll("/{2,}", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
