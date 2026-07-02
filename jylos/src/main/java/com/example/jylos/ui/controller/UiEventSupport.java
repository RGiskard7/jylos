package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.List;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
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

        subscriptions.add(eventBus.subscribe(UIEvents.StatusUpdateEvent.class,
                event -> controller.updateStatus(event.getMessage())));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class,
                event -> controller.handleUiNoteDeleted(event.getNoteId())));

        subscriptions.add(eventBus.subscribe(com.example.jylos.event.events.FolderEvents.FolderDeletedEvent.class,
                event -> controller.handleUiFolderDeleted(event.getFolderId())));

        subscriptions.add(eventBus.subscribe(NoteEvents.TrashItemDeletedEvent.class,
                event -> controller.handleUiTrashItemDeleted()));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteOpenRequestEvent.class,
                event -> controller.handleUiNoteOpenRequest(event.getNote())));

        subscriptions.add(eventBus.subscribe(NoteEvents.NoteModifiedEvent.class,
                event -> controller.handleUiNoteModified(event.getNote())));

        return subscriptions;
    }
}
