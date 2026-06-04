package com.example.jylos.event.events;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.event.AppEvent;

/**
 * Tag-related events for the application.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public final class TagEvents {

    private TagEvents() {
        // Prevent instantiation
    }

    /**
     * Event fired when a tag is selected for filtering.
     */
    public static class TagSelectedEvent extends AppEvent {
        private final Tag tag;

        public TagSelectedEvent(Tag tag) {
            super("TagList");
            this.tag = tag;
        }

        public Tag getTag() {
            return tag;
        }
    }

    /**
     * Event fired when a tag is created.
     */
    public static class TagCreatedEvent extends AppEvent {
        private final Tag tag;

        public TagCreatedEvent(Tag tag) {
            this.tag = tag;
        }

        public Tag getTag() {
            return tag;
        }
    }

    /**
     * Event fired when a tag is deleted.
     */
    public static class TagDeletedEvent extends AppEvent {
        private final String tagId;
        private final String tagTitle;

        public TagDeletedEvent(String tagId, String tagTitle) {
            this.tagId = tagId;
            this.tagTitle = tagTitle;
        }

        public String getTagId() {
            return tagId;
        }

        public String getTagTitle() {
            return tagTitle;
        }
    }

    /**
     * Event fired when a tag is added to a note.
     */
    public static class TagAddedToNoteEvent extends AppEvent {
        private final Note note;
        private final Tag tag;

        public TagAddedToNoteEvent(Note note, Tag tag) {
            this.note = note;
            this.tag = tag;
        }

        public Note getNote() {
            return note;
        }

        public Tag getTag() {
            return tag;
        }
    }

    /**
     * Event fired when a tag is removed from a note.
     */
    public static class TagRemovedFromNoteEvent extends AppEvent {
        private final Note note;
        private final Tag tag;

        public TagRemovedFromNoteEvent(Note note, Tag tag) {
            this.note = note;
            this.tag = tag;
        }

        public Note getNote() {
            return note;
        }

        public Tag getTag() {
            return tag;
        }
    }

    /**
     * Event fired when tags should be refreshed.
     */
    public static class TagsRefreshRequestedEvent extends AppEvent {
        public TagsRefreshRequestedEvent() {
            super();
        }
    }
}
