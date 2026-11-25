package com.example.jylos.event.events;

import java.util.Map;
import java.util.Set;

import com.example.jylos.data.dao.ExternalChanges;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.event.AppEvent;

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
        private final String previousNoteId;

        public NoteSavedEvent(Note note) {
            this(note, note != null ? note.getId() : null);
        }

        public NoteSavedEvent(Note note, String previousNoteId) {
            this.note = note;
            this.previousNoteId = previousNoteId;
        }

        public Note getNote() {
            return note;
        }

        public String getPreviousNoteId() {
            return previousNoteId;
        }
    }

    /**
     * Event fired when the shell selects/opens a note and fan-out observers such as
     * plugins need to react to the active note change. Its note is {@code null} when no note
     * is in sight any more: the note was closed, or the graph or a new tab is in front.
     */
    public static class NoteSelectedEvent extends AppEvent {
        private final Note note;

        public NoteSelectedEvent(Note note) {
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
        private final Component component;

        public TrashItemDeletedEvent(Component component) {
            super("TrashList");
            this.component = component;
        }

        public Component getComponent() {
            return component;
        }
    }

    /**
     * Event fired when a note's favorite status changes.
     */
    /**
     * @deprecated never published; a favourite change is announced as a
     *             {@link NoteSavedEvent} of the note.
     */
    @Deprecated
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
     * Notes were created, changed, moved or deleted in storage by something other than the
     * app's own views, such as an MCP client: the views and the open note follow them as they
     * follow a change made outside the app. Publishing it is enough; it is shown on the JavaFX
     * thread. Ids are the ones the note tools give.
     *
     * @since 2.7.0
     */
    public static class ExternalNoteChangesEvent extends AppEvent {
        private final ExternalChanges changes;

        /**
         * @param created        notes new to the vault
         * @param changed        notes changed where they are (content, tags)
         * @param deleted        notes gone (to the trash, or from where a move took them)
         * @param foldersChanged a folder was created or deleted
         * @param renamed        notes renamed or moved, old id to new id (also among
         *                       {@code deleted} and {@code created})
         */
        public ExternalNoteChangesEvent(Set<String> created, Set<String> changed, Set<String> deleted,
                boolean foldersChanged, Map<String, String> renamed) {
            super();
            this.changes = new ExternalChanges(created, changed, deleted, foldersChanged, renamed);
        }

        /** What changed, for the app itself: its type is not part of the plugin API. */
        public ExternalChanges getChanges() {
            return changes;
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
