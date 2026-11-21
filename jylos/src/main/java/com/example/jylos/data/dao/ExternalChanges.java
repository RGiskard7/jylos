package com.example.jylos.data.dao;

import java.util.Set;

/**
 * What changed in a folder vault outside the app, as a watcher reported it and the storage
 * applied it: the notes (and attachments) by id.
 *
 * @param created               ids new to the vault
 * @param changed               ids whose file changed
 * @param deleted               ids whose file is gone
 * @param foldersMayHaveChanged a folder was added, or something that may have been a folder
 *                              was removed: the folder list has to be read again
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public record ExternalChanges(Set<String> created, Set<String> changed, Set<String> deleted,
        boolean foldersMayHaveChanged) {

    /** Nothing changed. */
    public static final ExternalChanges NONE = new ExternalChanges(Set.of(), Set.of(), Set.of(), false);

    public ExternalChanges {
        created = Set.copyOf(created);
        changed = Set.copyOf(changed);
        deleted = Set.copyOf(deleted);
    }

    public boolean isEmpty() {
        return created.isEmpty() && changed.isEmpty() && deleted.isEmpty() && !foldersMayHaveChanged;
    }

    /** Whether the note {@code id} was created, changed or deleted. */
    public boolean touches(String id) {
        return id != null && (created.contains(id) || changed.contains(id) || deleted.contains(id));
    }

    /** This with the folders flag set: the folder list was read again and changed. */
    public ExternalChanges withFolders(boolean foldersChanged) {
        return new ExternalChanges(created, changed, deleted, foldersChanged);
    }
}
