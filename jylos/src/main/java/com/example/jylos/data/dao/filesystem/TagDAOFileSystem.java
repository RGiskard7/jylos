package com.example.jylos.data.dao.filesystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * File System implementation of TagDAO.
 * Derives tags from existing notes.
 */
public class TagDAOFileSystem implements TagDAO {

    private final NoteDAOFileSystem noteDAO;

    public TagDAOFileSystem(NoteDAOFileSystem noteDAO) {
        this.noteDAO = noteDAO;
    }

    @Override
    public String createTag(Tag tag) {
        // Tags are created implicitly when added to notes in this simple FS model
        // So just return ID (Title)
        if (tag.getId() == null) {
            tag.setId(tag.getTitle());
        }
        return tag.getId();
    }

    @Override
    public Tag getTagById(String id) {
        // Tag ID in this system is usually determining the tag object properties
        // We can't fetch metadata for tag unless we store it separately.
        // For now, assume ID = Title
        return new Tag(id, id);
    }

    @Override
    public List<Tag> fetchAllTags() {
        Set<Tag> tags = new HashSet<>();
        List<Note> notes = noteDAO.fetchAllNotes();
        for (Note note : notes) {
            for (Tag tag : note.getTags()) {
                // A tag parsed off a note (frontmatter or an inline #tag) carries no id —
                // `new Tag(title)` leaves it null. In this vault the id *is* the title, as
                // createTag and getTagById above both already assume, so fill it in here
                // too. Without it, everything keyed on the id silently fails on a tag that
                // was only ever typed into a note: updateTag returns early (making rename a
                // no-op) and TagService.updateTag rejects it outright.
                if (tag.getId() == null || tag.getId().isBlank()) {
                    tags.add(new Tag(tag.getTitle(), tag.getTitle()));
                } else {
                    tags.add(tag);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    @Override
    public void updateTag(Tag tag) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (tag == null || tag.getId() == null || tag.getId().isEmpty()) {
            return;
        }

        // In filesystem mode, tag ID and title are effectively the same logical key.
        // A rename means replacing occurrences across all notes.
        String oldKey = tag.getId();
        String newKey = tag.getTitle();

        if (newKey == null || newKey.trim().isEmpty() || oldKey.equals(newKey)) {
            return;
        }

        List<Note> notes = noteDAO.fetchAllNotes();
        for (Note note : notes) {
            List<Tag> noteTags = note.getTags();
            boolean changed = false;
            for (Tag existing : noteTags) {
                boolean sameById = existing.getId() != null && existing.getId().equals(oldKey);
                boolean sameByTitle = existing.getTitle() != null && existing.getTitle().equals(oldKey);
                if (sameById || sameByTitle) {
                    existing.setId(newKey);
                    existing.setTitle(newKey);
                    changed = true;
                }
            }

            if (changed) {
                note.setTags(noteTags);
                noteDAO.updateNote(note);
            }
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void deleteTag(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (id == null || id.isBlank()) {
            return;
        }
        // Remove tag from all notes
        List<Note> notes = noteDAO.fetchAllNotes();
        for (Note note : notes) {
            // note.getTags() is a snapshot copy, not the note's live tag set — removing
            // from the note here does not shrink `tags`. A previous index-based loop with
            // `i--` assumed it did, which left `i` stuck at the same index forever (size()
            // never dropped): an infinite loop on any tag matched by title instead of id,
            // which is the common case for a filesystem vault (inline #tags have no id).
            List<Tag> tags = note.getTags();
            boolean changed = false;
            for (Tag tag : tags) {
                boolean sameById = tag.getId() != null && tag.getId().equals(id);
                boolean sameByTitle = tag.getTitle() != null && tag.getTitle().equals(id);
                if (sameById || sameByTitle) {
                    note.removeTag(tag);
                    changed = true;
                }
            }
            if (changed) {
                noteDAO.updateNote(note);
            }
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public List<Note> fetchAllNotesWithTag(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return new ArrayList<>();
        }
        // Tag ID is title for now? Or UUID. Note objects store Tags.
        // We iterate all notes and check if they contain the tag.
        // Title match is case-insensitive, matching existsByTitle() below: an inline
        // #tag keeps whatever case the author typed it in, so "#Project" and "#project"
        // in different notes must resolve to the same tag rather than silently splitting
        // into two.
        List<Note> result = new ArrayList<>();
        List<Note> allNotes = noteDAO.fetchAllNotes();
        for (Note note : allNotes) {
            for (Tag t : note.getTags()) {
                if ((t.getId() != null && t.getId().equals(tagId))
                        || (t.getTitle() != null && t.getTitle().equalsIgnoreCase(tagId))) {
                    result.add(note);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean existsByTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        List<Tag> allTags = fetchAllTags();
        for (Tag t : allTags) {
            if (t.getTitle() != null && t.getTitle().equalsIgnoreCase(title)) {
                return true;
            }
        }
        return false;
    }
}
