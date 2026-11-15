package com.example.jylos.service;

import java.util.ArrayList;
import java.util.List;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;

/** The vault's trash as a whole: emptying it. */
public final class TrashService {

    private final NoteService notes;
    private final FolderService folders;

    public TrashService(NoteService notes, FolderService folders) {
        this.notes = notes;
        this.folders = folders;
    }

    /**
     * Deletes for good every note in the trash (also those inside trashed folders), then every
     * trashed folder.
     *
     * @return what was deleted, in that order, for the views to follow
     */
    public List<Component> emptyTrash() {
        List<Component> deleted = new ArrayList<>();
        for (Note note : notes.getTrashNotes()) {
            notes.permanentlyDeleteNote(note.getId());
            deleted.add(note);
        }
        Folder root = folders.getTrashFolders();
        if (root != null) {
            for (Component child : root.getChildren()) {
                if (child instanceof Folder) {
                    folders.permanentlyDeleteFolder(child.getId());
                    deleted.add(child);
                }
            }
        }
        return deleted;
    }
}
