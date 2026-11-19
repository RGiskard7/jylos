package com.example.jylos.data.models;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A vault's own settings: where its templates are and how they write dates, its daily note,
 * where new notes go, and the templates of Templater. Folders and notes are named by their
 * path in the vault ({@code Plantillas/diaria.md}). Missing values read as the defaults, which
 * are Obsidian's: dates {@code YYYY-MM-DD}, times {@code HH:mm}.
 *
 * @param templates           the templates folder and its date and time formats
 * @param daily               the daily note's folder, name format and template
 * @param newNotes            where a new note is created
 * @param templater           Templater's syntax and its templates by folder
 * @param obsidianImportAsked whether importing Obsidian's settings has already been offered
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public record VaultSettings(Templates templates, DailyNotes daily, NewNotes newNotes, Templater templater,
        boolean obsidianImportAsked) {

    /** The date format of templates and daily notes when none is set: Obsidian's. */
    public static final String DEFAULT_DATE_FORMAT = "YYYY-MM-DD";
    /** The time format of templates when none is set: Obsidian's. */
    public static final String DEFAULT_TIME_FORMAT = "HH:mm";

    public VaultSettings {
        templates = templates != null ? templates : new Templates("", null, null);
        daily = daily != null ? daily : new DailyNotes("", null, "");
        newNotes = newNotes != null ? newNotes : new NewNotes(null, "");
        templater = templater != null ? templater : new Templater(false, false, null);
    }

    /** No setting chosen. */
    public static VaultSettings defaults() {
        return new VaultSettings(null, null, null, null, false);
    }

    public VaultSettings withTemplates(Templates value) {
        return new VaultSettings(value, daily, newNotes, templater, obsidianImportAsked);
    }

    public VaultSettings withDaily(DailyNotes value) {
        return new VaultSettings(templates, value, newNotes, templater, obsidianImportAsked);
    }

    public VaultSettings withNewNotes(NewNotes value) {
        return new VaultSettings(templates, daily, value, templater, obsidianImportAsked);
    }

    public VaultSettings withTemplater(Templater value) {
        return new VaultSettings(templates, daily, newNotes, value, obsidianImportAsked);
    }

    public VaultSettings withObsidianImportAsked(boolean value) {
        return new VaultSettings(templates, daily, newNotes, templater, value);
    }

    /** These settings with every path under the folder {@code from} now under {@code to}. */
    public VaultSettings movePath(String from, String to) {
        UnaryOperator<String> move = path -> moved(path, from, to);
        return new VaultSettings(
                new Templates(move.apply(templates.folder()), templates.dateFormat(), templates.timeFormat()),
                new DailyNotes(move.apply(daily.folder()), daily.format(), move.apply(daily.template())),
                new NewNotes(newNotes.location(), move.apply(newNotes.folder())),
                new Templater(templater.enabled(), templater.triggerOnCreate(), templater.folderTemplates().stream()
                        .map(t -> new FolderTemplate(move.apply(t.folder()), move.apply(t.template()))).toList()),
                obsidianImportAsked);
    }

    private static String moved(String path, String from, String to) {
        if (path == null || from == null || to == null || from.isEmpty()) {
            return path;
        }
        if (path.equals(from)) {
            return to;
        }
        return path.startsWith(from + "/") ? to + path.substring(from.length()) : path;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    /**
     * @param folder     the templates folder ({@code ""}: none chosen)
     * @param dateFormat the format of {@code {{date}}}
     * @param timeFormat the format of {@code {{time}}}
     */
    public record Templates(String folder, String dateFormat, String timeFormat) {
        public Templates {
            folder = orEmpty(folder);
            dateFormat = orDefault(dateFormat, DEFAULT_DATE_FORMAT);
            timeFormat = orDefault(timeFormat, DEFAULT_TIME_FORMAT);
        }
    }

    /**
     * @param folder   the daily notes' folder ({@code ""}: where new notes go)
     * @param format   the format of a daily note's name
     * @param template the daily note's template ({@code ""}: none)
     */
    public record DailyNotes(String folder, String format, String template) {
        public DailyNotes {
            folder = orEmpty(folder);
            format = orDefault(format, DEFAULT_DATE_FORMAT);
            template = orEmpty(template);
        }
    }

    /** Where a new note is created: the folder in view, the vault's root, or a given folder. */
    public enum NewNoteLocation {
        CURRENT, ROOT, FOLDER
    }

    /**
     * @param location where a new note is created
     * @param folder   the folder for {@link NewNoteLocation#FOLDER}
     */
    public record NewNotes(NewNoteLocation location, String folder) {
        public NewNotes {
            location = location != null ? location : NewNoteLocation.CURRENT;
            folder = orEmpty(folder);
        }
    }

    /**
     * @param enabled         whether templates understand Templater's {@code <% %>}
     * @param triggerOnCreate whether a note created in a folder with a template gets it
     * @param folderTemplates the template of each folder
     */
    public record Templater(boolean enabled, boolean triggerOnCreate, List<FolderTemplate> folderTemplates) {
        public Templater {
            folderTemplates = folderTemplates != null
                    ? folderTemplates.stream().filter(t -> t != null).toList() : List.of();
        }
    }

    /**
     * @param folder   the folder ({@code ""}: the whole vault)
     * @param template the template of the notes created in it
     */
    public record FolderTemplate(String folder, String template) {
        public FolderTemplate {
            folder = orEmpty(folder);
            template = orEmpty(template);
        }
    }
}
