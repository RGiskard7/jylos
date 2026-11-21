package com.example.jylos.ui.controller;

import com.example.jylos.data.dao.ExternalChanges;

/**
 * What a change made outside the app does to the note open in the editor. Only a change that
 * touched it makes it react, so a sync of other notes never moves what is being read or written.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
final class ExternalChangeSupport {

    /** What happens to the note shown in the editor. */
    enum OpenNote {
        /** Untouched by the change. */
        KEEP,
        /** Changed outside and without unsaved edits: shown again as it is now. */
        RELOAD,
        /**
         * Changed outside while it has unsaved edits: kept as it is, with a warning; saving it
         * then asks (or the autosave pauses), as for any change made outside.
         */
        WARN,
        /** Deleted outside: handled as a missing note (kept if it has unsaved edits). */
        MISSING,
        /** Moved or renamed outside: the editor follows it to its new path, unsaved edits and all. */
        FOLLOW
    }

    private ExternalChangeSupport() {
    }

    /**
     * @param currentId the note shown in the editor, or null
     * @param modified  whether it has unsaved edits
     */
    static OpenNote react(ExternalChanges changes, String currentId, boolean modified) {
        if (currentId == null) {
            return OpenNote.KEEP;
        }
        if (changes.renamed().containsKey(currentId)) {
            return OpenNote.FOLLOW;
        }
        if (changes.deleted().contains(currentId)) {
            return OpenNote.MISSING;
        }
        if (changes.changed().contains(currentId) || changes.created().contains(currentId)) {
            return modified ? OpenNote.WARN : OpenNote.RELOAD;
        }
        return OpenNote.KEEP;
    }
}
