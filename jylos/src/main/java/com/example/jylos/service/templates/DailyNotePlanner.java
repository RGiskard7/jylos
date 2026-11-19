package com.example.jylos.service.templates;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Locale;

import com.example.jylos.data.models.VaultSettings.DailyNotes;
import com.example.jylos.data.models.VaultSettings.NewNoteLocation;
import com.example.jylos.data.models.VaultSettings.NewNotes;
import com.example.jylos.util.MomentFormat;

/**
 * Today's daily note, as Obsidian's Daily notes finds or makes it: its name is today in the
 * daily note's date format, and it goes in the daily note's folder, which has to be there, or,
 * with none chosen, in the folder new notes go to when that is a folder of its own (and is
 * there), else in the vault's root. A format with slashes ({@code YYYY/MM/DD}) puts it in folders
 * under that one. The note at that path, in any case, is today's; its template is a path, with
 * or without {@code .md}, that has to be there.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class DailyNotePlanner {

    /** Why today's note can be neither opened nor made. */
    public enum Problem {
        /** The daily note's folder is not in the vault. */
        FOLDER_NOT_FOUND,
        /** The daily note's template is not in the vault. */
        TEMPLATE_NOT_FOUND
    }

    /**
     * @param name        its name: today in its date format, slashes included
     * @param path        its path in the vault
     * @param existing    the path of the note already there, in its own case, or {@code null}
     * @param template    the path of the template to make it with, or {@code null} for none
     * @param problem     why it can be neither opened nor made, or {@code null}
     * @param problemPath the folder or template that is not there
     */
    public record Plan(String name, String path, String existing, String template, Problem problem,
            String problemPath) {

        /** The folder it goes in, by its path ({@code ""} for the root). */
        public String folder() {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? "" : path.substring(0, slash);
        }

        /** Its name without the folders a format with slashes adds. */
        public String fileName() {
            return name.substring(name.lastIndexOf('/') + 1);
        }
    }

    private DailyNotePlanner() {
    }

    /**
     * Today's note in a vault with these folders and notes, by path.
     *
     * @param legacyTemplate the template to use when none is chosen ({@code null} for none)
     */
    public static Plan plan(DailyNotes settings, NewNotes newNotes, ZonedDateTime now, Locale language,
            Collection<String> folders, Collection<String> notes, String legacyTemplate) {
        String name = MomentFormat.format(now, settings.format(), language).strip();
        String chosen = normalize(settings.folder());
        String folder;
        if (!chosen.isEmpty()) {
            if (!folders.contains(chosen)) {
                return new Plan(name, name + ".md", null, null, Problem.FOLDER_NOT_FOUND, chosen);
            }
            folder = chosen;
        } else if (newNotes.location() == NewNoteLocation.FOLDER && folders.contains(normalize(newNotes.folder()))) {
            folder = normalize(newNotes.folder());
        } else {
            folder = "";
        }
        String path = normalize((folder.isEmpty() ? "" : folder + "/") + name) + ".md";
        String existing = notes.stream().filter(note -> note.equalsIgnoreCase(path)).findFirst().orElse(null);
        String template = null;
        String setting = normalize(settings.template());
        if (!setting.isEmpty()) {
            template = hasExtension(setting) ? setting : setting + ".md";
            if (existing == null && !notes.contains(template)) {
                return new Plan(name, path, null, null, Problem.TEMPLATE_NOT_FOUND, template);
            }
        } else if (legacyTemplate != null && !legacyTemplate.isBlank()) {
            template = legacyTemplate;
        }
        return new Plan(name, path, existing, template, null, null);
    }

    private static boolean hasExtension(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.lastIndexOf('.') > 0;
    }

    /** A path as Obsidian normalizes it: no slashes at either end, none doubled. */
    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String clean = path.strip().replace('\\', '/').replaceAll("/{2,}", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
