package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

import javafx.scene.control.Cell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

/**
 * Dragging notes and folders onto a folder row, shared by the classic folder tree and the
 * Explorer tree. The payload is a plain string: {@code note:<id>} or {@code folder:<id>}.
 *
 * <p>A folder cannot be dropped onto itself or into one of its own descendants, and nothing
 * can be dropped onto the tree's invisible root. After a successful move the folder tree,
 * the notes list and every other listener are told to reload.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class FolderDropSupport {

    private static final Logger logger = LoggerConfig.getLogger(FolderDropSupport.class);

    static final String NOTE_PREFIX = "note:";
    static final String FOLDER_PREFIX = "folder:";
    private static final String DROP_TARGET_STYLE = "drag-over-target";

    private final Supplier<NoteService> noteService;
    private final Supplier<FolderService> folderService;
    private final Supplier<EventBus> eventBus;
    private final Function<String, String> i18n;
    private final Consumer<String> status;
    private final Runnable afterMove;

    /**
     * @param afterMove runs after every successful move, before the refresh events are
     *                  published (the sidebar uses it to drop its note-count cache and
     *                  schedule its own reloads)
     */
    public FolderDropSupport(Supplier<NoteService> noteService, Supplier<FolderService> folderService,
            Supplier<EventBus> eventBus, Function<String, String> i18n, Consumer<String> status,
            Runnable afterMove) {
        this.noteService = noteService;
        this.folderService = folderService;
        this.eventBus = eventBus;
        this.i18n = i18n;
        this.status = status;
        this.afterMove = afterMove != null ? afterMove : () -> {
        };
    }

    /** Lets {@code cell} start a drag carrying {@code payload}; a blank payload disables it. */
    public static void installDragSource(Cell<?> cell, Supplier<String> payload) {
        cell.setOnDragDetected(event -> {
            String value = cell.isEmpty() ? null : payload.get();
            if (value == null || value.isBlank()) {
                return;
            }
            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(value);
            db.setContent(content);
            event.consume();
        });
    }

    /** The payload a note row carries while being dragged. */
    public static String notePayload(Note note) {
        return note == null || note.getId() == null ? null : NOTE_PREFIX + note.getId();
    }

    /** The payload a folder row carries while being dragged; the vault root cannot be dragged. */
    public static String folderPayload(Folder folder) {
        if (folder == null || folder.getId() == null) {
            return null;
        }
        String id = normalizeId(folder.getId());
        return "ROOT".equals(id) ? null : FOLDER_PREFIX + id;
    }

    /** Makes {@code cell} accept drops onto the folder {@code target} returns at drop time. */
    public void installDropTarget(Cell<?> cell, Supplier<Folder> target) {
        cell.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (!db.hasString()) {
                return;
            }
            if (canAccept(db.getString(), target.get())) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        cell.setOnDragEntered(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString() && canAccept(db.getString(), target.get())
                    && !cell.getStyleClass().contains(DROP_TARGET_STYLE)) {
                cell.getStyleClass().add(DROP_TARGET_STYLE);
            }
            event.consume();
        });
        cell.setOnDragExited(event -> {
            cell.getStyleClass().remove(DROP_TARGET_STYLE);
            event.consume();
        });
        cell.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = db.hasString() && drop(db.getString(), target.get());
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /** Whether {@code payload} may be dropped onto {@code targetFolder}. */
    public boolean canAccept(String payload, Folder targetFolder) {
        if (payload == null || targetFolder == null || targetFolder.getId() == null) {
            return false;
        }
        String targetId = normalizeId(targetFolder.getId());
        if ("INVISIBLE_ROOT".equals(targetId)) {
            return false;
        }
        if (payload.startsWith(NOTE_PREFIX)) {
            return noteService.get() != null && folderService.get() != null;
        }
        if (payload.startsWith(FOLDER_PREFIX)) {
            String sourceFolderId = normalizeId(payload.substring(FOLDER_PREFIX.length()));
            if (sourceFolderId.isBlank() || sourceFolderId.equals(targetId)) {
                return false;
            }
            if (targetId.startsWith(sourceFolderId + "/")) {
                return false;
            }
            FolderService folders = folderService.get();
            if (folders == null) {
                return false;
            }
            Optional<Folder> source = folders.getFolderById(sourceFolderId);
            return source.isPresent() && folders.canMoveFolder(source.get(), targetFolder);
        }
        return false;
    }

    /** Moves what {@code payload} names into {@code targetFolder}; false if nothing moved. */
    public boolean drop(String payload, Folder targetFolder) {
        if (!canAccept(payload, targetFolder)) {
            return false;
        }
        FolderService folders = folderService.get();
        EventBus bus = eventBus.get();
        try {
            if (payload.startsWith(NOTE_PREFIX)) {
                String noteId = payload.substring(NOTE_PREFIX.length());
                Optional<Note> noteOpt = noteService.get().getNoteById(noteId);
                if (noteOpt.isEmpty()) {
                    return false;
                }
                Note movedNote = noteOpt.get();
                String previousNoteId = movedNote.getId();
                folders.moveNoteToFolder(movedNote, targetFolder);
                if (bus != null) {
                    bus.publish(new NoteEvents.NoteSavedEvent(movedNote, previousNoteId));
                }
                publishStatus(MessageFormat.format(i18n.apply("status.note_moved_folder"), targetFolder.getTitle()));
            } else {
                String folderId = payload.substring(FOLDER_PREFIX.length());
                Optional<Folder> folderOpt = folders.getFolderById(folderId);
                if (folderOpt.isEmpty()) {
                    return false;
                }
                folders.moveFolderToFolder(folderOpt.get(), targetFolder);
                publishStatus(MessageFormat.format(i18n.apply("status.folder_moved"), targetFolder.getTitle()));
            }
            afterMove.run();
            if (bus != null) {
                bus.publish(new FolderEvents.FoldersRefreshRequestedEvent());
                bus.publish(new NoteEvents.NotesRefreshRequestedEvent());
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to process drag and drop payload: " + payload, e);
            publishStatus(i18n.apply("status.error"));
            return false;
        }
    }

    private void publishStatus(String message) {
        if (message != null && !message.isBlank()) {
            status.accept(message);
        }
    }

    static String normalizeId(String id) {
        return id == null ? "" : id.replace("\\", "/");
    }
}
