package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * A note of a folder vault keeps its favorite and pinned marks through everything done to its
 * file from Jylos: renaming it, moving it in and out of folders, renaming or moving its folder,
 * the trash and back, and a folder going to the trash and back. A note deleted for good is
 * gone, and a new note at its path starts unmarked. Every check reads the vault again from
 * disk, as the next start would.
 */
class VaultNoteFlagsTest {

    @TempDir
    Path vault;

    private NoteService notes() {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private FolderService folders() {
        return new FolderService(new FolderDAOFileSystem(vault.toString()), new NoteDAOFileSystem(vault.toString()));
    }

    /** A new note, marked favorite and pinned. */
    private Note marked(NoteService notes, String title) {
        Note note = notes.createNote(title, "Texto de " + title);
        notes.toggleFavorite(note);
        notes.togglePinned(note);
        return note;
    }

    /** How the note with {@code title} reads from disk now: where it is and its marks. */
    private String onDisk(String title) {
        Optional<Note> note = notes().getAllNotes().stream().filter(n -> title.equals(n.getTitle())).findFirst();
        return note.map(n -> n.getId() + " favorite " + n.isFavorite() + " pinned " + n.isPinned())
                .orElse(title + " missing");
    }

    @Test
    void aRenamedNoteKeepsItsMarks() {
        NoteService notes = notes();
        Note note = marked(notes, "Plan");

        note.setTitle("Plan nuevo");
        notes.updateNote(note);

        assertEquals("Plan nuevo.md favorite true pinned true", onDisk("Plan nuevo"));
        assertEquals("Plan missing", onDisk("Plan"));
    }

    @Test
    void aNoteMovedIntoAFolderAndBackKeepsItsMarks() {
        NoteService notes = notes();
        FolderService folders = folders();
        Folder work = folders.createFolder("Trabajo");
        Note note = marked(notes, "Plan");

        folders.moveNoteToFolder(note, work);
        String inFolder = onDisk("Plan");
        folders.moveNoteToFolder(notes.getNoteById(note.getId()).orElseThrow(), null);

        assertEquals(List.of("Trabajo/Plan.md favorite true pinned true", "Plan.md favorite true pinned true"),
                List.of(inFolder, onDisk("Plan")));
    }

    @Test
    void aNoteKeepsItsMarksWhenItsFolderIsRenamedOrMoved() {
        NoteService notes = notes();
        FolderService folders = folders();
        Folder work = folders.createFolder("Trabajo");
        Folder archive = folders.createFolder("Archivo");
        Note note = marked(notes, "Plan");
        folders.moveNoteToFolder(note, work);

        Folder renamed = folders.renameFolder(work, "Proyectos");
        String afterRename = onDisk("Plan");
        folders.moveFolderToFolder(renamed, archive);

        assertEquals(List.of("Proyectos/Plan.md favorite true pinned true",
                "Archivo/Proyectos/Plan.md favorite true pinned true"), List.of(afterRename, onDisk("Plan")));
    }

    @Test
    void aNoteKeepsItsMarksThroughTheTrash() {
        NoteService notes = notes();
        Note note = marked(notes, "Plan");

        notes.moveToTrash(note.getId());
        List<String> trash = notes().getTrashNotes().stream()
                .map(n -> n.getId() + " favorite " + n.isFavorite() + " pinned " + n.isPinned()).toList();
        notes().restoreNote(".trash/Plan.md");

        assertEquals(List.of(".trash/Plan.md favorite true pinned true"), trash);
        assertEquals("Plan.md favorite true pinned true", onDisk("Plan"));
    }

    @Test
    void aNoteKeepsItsMarksWhenItsFolderGoesToTheTrashAndBack() {
        NoteService notes = notes();
        FolderService folders = folders();
        Folder work = folders.createFolder("Trabajo");
        Note note = marked(notes, "Plan");
        folders.moveNoteToFolder(note, work);

        folders.deleteFolder(work.getId());
        folders().restoreFolder(".trash/Trabajo");

        assertEquals("Trabajo/Plan.md favorite true pinned true", onDisk("Plan"));
    }

    @Test
    void aNoteDeletedForGoodIsGoneAndANewOneAtItsPathStartsUnmarked() {
        NoteService notes = notes();
        Note note = marked(notes, "Plan");
        notes.moveToTrash(note.getId());
        notes().permanentlyDeleteNote(".trash/Plan.md");

        String gone = onDisk("Plan");
        notes().createNote("Plan", "Otra nota");

        assertEquals(List.of("Plan missing", "Plan.md favorite false pinned false"), List.of(gone, onDisk("Plan")));
    }
}
