package com.example.jylos.ui.controller;

import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * Trash/restore decision logic for notes, free of controller/UI state.
 *
 * <p>Confirmation dialogs, selection clearing and event publishing stay in the
 * controller; this class owns only the business rule (private notes are
 * protected from deletion) and the actual service call, so that rule is
 * covered by a direct unit test instead of only being reachable through a
 * JavaFX {@code Alert}.</p>
 */
class NoteTrashOperations {

    private static final Logger logger = LoggerConfig.getLogger(NoteTrashOperations.class);

    private final NoteService noteService;

    NoteTrashOperations(NoteService noteService) {
        this.noteService = noteService;
    }

    enum FailureReason {
        NOTE_SERVICE_UNAVAILABLE, PRIVATE_NOTE, DELETE_FAILED
    }

    record TrashResult(boolean success, FailureReason failureReason) {
        static TrashResult ok() {
            return new TrashResult(true, null);
        }

        static TrashResult failed(FailureReason reason) {
            return new TrashResult(false, reason);
        }
    }

    /**
     * Moves {@code note} to the trash, unless it is private — a private note must be
     * turned back to normal first, so a forgotten/locked note can never be lost by an
     * accidental delete.
     */
    TrashResult moveToTrash(Note note) {
        if (noteService == null) {
            logger.warning("Cannot delete note: noteService is null");
            return TrashResult.failed(FailureReason.NOTE_SERVICE_UNAVAILABLE);
        }
        if (note == null) {
            return TrashResult.failed(FailureReason.DELETE_FAILED);
        }
        if (note.isPrivate()) {
            return TrashResult.failed(FailureReason.PRIVATE_NOTE);
        }
        try {
            noteService.moveToTrash(note.getId());
            return TrashResult.ok();
        } catch (Exception e) {
            logger.warning("Failed to move note to trash: " + e.getMessage());
            return TrashResult.failed(FailureReason.DELETE_FAILED);
        }
    }

    TrashResult restore(Note note) {
        if (noteService == null) {
            return TrashResult.failed(FailureReason.NOTE_SERVICE_UNAVAILABLE);
        }
        if (note == null) {
            return TrashResult.failed(FailureReason.DELETE_FAILED);
        }
        try {
            noteService.restoreNote(note.getId());
            return TrashResult.ok();
        } catch (Exception e) {
            logger.warning("Failed to restore note: " + e.getMessage());
            return TrashResult.failed(FailureReason.DELETE_FAILED);
        }
    }

    TrashResult permanentlyDelete(Note note) {
        if (noteService == null) {
            return TrashResult.failed(FailureReason.NOTE_SERVICE_UNAVAILABLE);
        }
        if (note == null) {
            return TrashResult.failed(FailureReason.DELETE_FAILED);
        }
        try {
            noteService.permanentlyDeleteNote(note.getId());
            return TrashResult.ok();
        } catch (Exception e) {
            logger.warning("Failed to permanently delete note: " + e.getMessage());
            return TrashResult.failed(FailureReason.DELETE_FAILED);
        }
    }
}
