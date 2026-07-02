package com.example.jylos.event.events;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.AppEvent;
import java.util.List;

/**
 * Note-related events for the application.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public final class NoteEvents {

    private NoteEvents() {
        // Prevent instantiation
    }

    /**
     * Event fired when a note is created.
     */
    public static class NoteCreatedEvent extends AppEvent {
        private final Note note;

        public NoteCreatedEvent(Note note) {
            this.note = note;
        }

        public Note getNote() {
            return note;
        }
    }

    /**
     * Event fired when a note is saved.
     */
    public static class NoteSavedEvent extends AppEvent {
        private final Note note;

        public NoteSavedEvent(Note note) {
            this.note = note;
        }

        public Note getNote() {
            return note;
        }
    }

    /**
     * Event fired when a note is deleted.
     */
    public static class NoteDeletedEvent extends AppEvent {
        private final String noteId;
        private final String noteTitle;

        public NoteDeletedEvent(String noteId, String noteTitle) {
            this.noteId = noteId;
            this.noteTitle = noteTitle;
        }

        public String getNoteId() {
            return noteId;
        }

        public String getNoteTitle() {
            return noteTitle;
        }
    }

    /**
     * Event fired when an item has been permanently deleted from trash.
     */
    public static class TrashItemDeletedEvent extends AppEvent {
        private final com.example.jylos.data.models.interfaces.Component component;

        public TrashItemDeletedEvent(com.example.jylos.data.models.interfaces.Component component) {
            super("TrashList");
            this.component = component;
        }

        public com.example.jylos.data.models.interfaces.Component getComponent() {
            return component;
        }
    }

    /**
     * Event fired when a note's favorite status changes.
     */
    public static class NoteFavoriteChangedEvent extends AppEvent {
        private final Note note;
        private final boolean isFavorite;

        public NoteFavoriteChangedEvent(Note note, boolean isFavorite) {
            this.note = note;
            this.isFavorite = isFavorite;
        }

        public Note getNote() {
            return note;
        }

        public boolean isFavorite() {
            return isFavorite;
        }
    }

    /**
     * Event fired when notes list should be refreshed.
     */
    public static class NotesRefreshRequestedEvent extends AppEvent {
        public NotesRefreshRequestedEvent() {
            super();
        }
    }

    /**
     * Event fired when note content changes.
     */
    public static class NoteContentChangedEvent extends AppEvent {
        private final Note note;
        private final String content;

        public NoteContentChangedEvent(Note note, String content) {
            this.note = note;
            this.content = content;
        }

        public Note getNote() {
            return note;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * Event fired when a plugin requests to open a note in the editor.
     */
    public static class NoteOpenRequestEvent extends AppEvent {
        private final Note note;

        public NoteOpenRequestEvent(Note note) {
            super("Plugin");
            this.note = note;
        }

        public Note getNote() {
            return note;
        }
    }

    /**
     * Event fired when the current note is modified in the editor but not yet
     * saved.
     */
    public static class NoteModifiedEvent extends AppEvent {
        private final Note note;

        public NoteModifiedEvent(Note note) {
            super("Editor");
            this.note = note;
        }

        public Note getNote() {
            return note;
        }
    }

    /**
     * Event fired when a note is updated (e.g., moved to a folder).
     */
    public static class NoteUpdatedEvent extends AppEvent {
        private final Note note;

        public NoteUpdatedEvent(Note note) {
            super("NotesList");
            this.note = note;
        }

        public Note getNote() {
            return note;
        }
    }
}
