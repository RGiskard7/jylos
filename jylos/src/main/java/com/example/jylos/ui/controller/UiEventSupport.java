package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.List;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.TagEvents;
import com.example.jylos.event.events.UIEvents;

/**
 * Wires the {@link EventBus} UI events to their handlers on {@link MainController}.
 *
 * <p>Holds the controller it serves and subscribes each event type to the matching
 * controller method, replacing the former 16-method callback interface.</p>
 */
class UiEventSupport {

    private final MainController controller;

    UiEventSupport(MainController controller) {
        this.controller = controller;
    }

    /**
     * Subscribes every UI event to its controller handler.
     *
     * @return the created subscriptions, so the caller can cancel them on shutdown
     */
    List<EventBus.Subscription> subscribe(EventBus eventBus) {
        if (eventBus == null) {
            return List.of();
        }
        List<EventBus.Subscription> subscriptions = new ArrayList<>();

        subscriptions.add(eventBus.subscribe(UIEvents.ThemeChangedEvent.class,
                event -> controller.applyThemeFromEvent(event.getTheme())));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteSelectedEvent.class, event -> {
            if (event.getNote() != null) {
                controller.loadNoteInEditor(event.getNote());
            }
        }));

        subscriptions.add(eventBus.subscribe(NoteEvents.NotesLoadedEvent.class, event -> {
            controller.handleUiNotesLoaded(event);
            controller.updateStatus(event.getStatusMessage());
        }));

        subscriptions.add(eventBus.subscribe(UIEvents.StatusUpdateEvent.class,
                event -> controller.updateStatus(event.getMessage())));

        subscriptions.add(eventBus.subscribe(UIEvents.ShowCommandPaletteEvent.class,
                event -> controller.showCommandPalette()));

        subscriptions.add(eventBus.subscribe(UIEvents.ShowQuickSwitcherEvent.class,
                event -> controller.showQuickSwitcher()));

        subscriptions.add(eventBus.subscribe(UIEvents.ShowKeyboardShortcutsEvent.class,
                event -> controller.showKeyboardShortcutsHelp()));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class,
                event -> controller.handleUiNoteDeleted(event.getNoteId())));

        subscriptions.add(eventBus.subscribe(FolderEvents.FolderDeletedEvent.class,
                event -> controller.handleUiFolderDeleted(event.getFolderId())));

        subscriptions.add(eventBus.subscribe(NoteEvents.TrashItemDeletedEvent.class,
                event -> controller.handleUiTrashItemDeleted()));

        subscriptions.add(eventBus.subscribe(FolderEvents.FolderSelectedEvent.class,
                event -> controller.handleUiFolderSelected(event.getFolder())));

        subscriptions.add(eventBus.subscribe(TagEvents.TagSelectedEvent.class,
                event -> controller.handleUiTagSelected(event.getTag())));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteOpenRequestEvent.class,
                event -> controller.handleUiNoteOpenRequest(event.getNote())));

        subscriptions.add(eventBus.subscribe(NoteEvents.TrashItemSelectedEvent.class,
                event -> controller.handleUiTrashItemSelected(event.getComponent())));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteModifiedEvent.class,
                event -> controller.handleUiNoteModified(event.getNote())));

        return subscriptions;
    }
}
