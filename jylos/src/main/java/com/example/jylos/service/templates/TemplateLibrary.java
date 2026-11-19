package com.example.jylos.service.templates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which notes of the vault are templates, as Obsidian's Templates decides it: the Markdown
 * notes of the templates folder and of its subfolders, each named by its path in that folder
 * without {@code .md}. Obsidian refuses the vault's root as the folder, and a folder that is
 * not there. With no folder chosen, Jylos takes the folders named {@code Templates}, in any
 * case, as it always has.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class TemplateLibrary {

    /** Why there are no templates to list. */
    public enum Problem {
        /** No folder is chosen and none is named Templates. */
        NO_FOLDER,
        /** The folder chosen is not in the vault. */
        FOLDER_NOT_FOUND,
        /** The folder chosen is the vault's root. */
        ROOT_FOLDER
    }

    /**
     * @param path its path in the vault
     * @param name its path in the templates folder, without {@code .md}
     */
    public record Template(String path, String name) {
    }

    /**
     * @param folder    the templates folder, by its path ({@code ""} for several or none)
     * @param templates the templates, in the order of the notes given
     * @param problem   why there are none to list, or {@code null}
     */
    public record Listing(String folder, List<Template> templates, Problem problem) {

        /**
         * The template called {@code name}: its path in the folder, with or without {@code .md},
         * in any case, as Obsidian looks one up.
         */
        public Optional<Template> find(String name) {
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }
            String wanted = name.strip();
            String bare = wanted.toLowerCase(Locale.ROOT).endsWith(".md") ? wanted.substring(0, wanted.length() - 3)
                    : wanted;
            return templates.stream().filter(t -> t.name().equals(bare)).findFirst()
                    .or(() -> templates.stream().filter(t -> t.name().equalsIgnoreCase(bare)).findFirst());
        }
    }

    private TemplateLibrary() {
    }

    /**
     * The templates of a vault with these folders and notes, by path.
     *
     * @param folderSetting the templates folder chosen ({@code ""}: none)
     * @param folders       the vault's folders
     * @param notes         the vault's notes
     */
    public static Listing list(String folderSetting, Collection<String> folders, Collection<String> notes) {
        String chosen = normalize(folderSetting);
        List<String> roots = new ArrayList<>();
        if (chosen == null) {
            for (String folder : folders) {
                String name = folder.substring(folder.lastIndexOf('/') + 1);
                if ("templates".equalsIgnoreCase(name)) {
                    roots.add(folder);
                }
            }
            if (roots.isEmpty()) {
                return new Listing("", List.of(), Problem.NO_FOLDER);
            }
        } else if (chosen.isEmpty()) {
            return new Listing("", List.of(), Problem.ROOT_FOLDER);
        } else if (!folders.contains(chosen)) {
            return new Listing(chosen, List.of(), Problem.FOLDER_NOT_FOUND);
        } else {
            roots.add(chosen);
        }
        List<Template> templates = new ArrayList<>();
        for (String note : notes) {
            if (!note.toLowerCase(Locale.ROOT).endsWith(".md")) {
                continue;
            }
            for (String root : roots) {
                if (note.startsWith(root + "/")) {
                    templates.add(new Template(note, note.substring(root.length() + 1, note.length() - 3)));
                    break;
                }
            }
        }
        return new Listing(roots.size() == 1 ? roots.getFirst() : "", List.copyOf(templates), null);
    }

    /**
     * A folder setting as a path, as Obsidian normalizes it: {@code null} when none is chosen,
     * {@code ""} for the vault's root.
     */
    private static String normalize(String setting) {
        if (setting == null || setting.isBlank()) {
            return null;
        }
        String path = setting.strip().replace('\\', '/').replaceAll("/{2,}", "/");
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
