package com.example.jylos.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

import javafx.application.Platform;

/**
 * A simple event bus for decoupled communication between application components.
 * 
 * <p>The EventBus follows the publish-subscribe pattern, allowing components to
 * communicate without direct dependencies. Events are dispatched asynchronously
 * on the JavaFX Application Thread to ensure thread safety for UI updates.</p>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Subscribe to an event
 * EventBus.getInstance().subscribe(NoteSavedEvent.class, event ->
 *     logger.fine("Note saved: " + event.getNote().getTitle()));
 * 
 * // Publish an event
 * EventBus.getInstance().publish(new NoteSavedEvent(note));
 * }</pre>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public class EventBus {
    
    private static final Logger logger = LoggerConfig.getLogger(EventBus.class);
    
    // Singleton instance
    private static EventBus instance;
    
    // Map of event types to their subscribers
    private final Map<Class<? extends AppEvent>, List<Consumer<? extends AppEvent>>> subscribers;
    
    /**
     * Private constructor for singleton pattern.
     */
    private EventBus() {
        this.subscribers = new ConcurrentHashMap<>();
        logger.info("EventBus initialized");
    }
    
    /**
     * Gets the singleton instance of the EventBus.
     * 
     * @return The EventBus instance
     */
    public static synchronized EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
    }
    
    /**
     * Subscribes to a specific event type.
     * 
     * @param <T>       The event type
     * @param eventType The class of the event to subscribe to
     * @param handler   The handler to call when the event is published
     * @return A Subscription object that can be used to unsubscribe
     */
    public <T extends AppEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        @SuppressWarnings("unchecked")
        Consumer<? extends AppEvent> typedHandler = (Consumer<? extends AppEvent>) handler;
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                    .add(typedHandler);
        logger.fine("Subscribed to event: " + eventType.getSimpleName());
        
        return new Subscription(() -> unsubscribe(eventType, handler));
    }
    
    /**
     * Unsubscribes from a specific event type.
     * 
     * @param <T>       The event type
     * @param eventType The class of the event
     * @param handler   The handler to remove
     */
    public <T extends AppEvent> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<? extends AppEvent>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
            logger.fine("Unsubscribed from event: " + eventType.getSimpleName());
        }
    }
    
    /**
     * Publishes an event to all subscribers.
     * The event is dispatched on the JavaFX Application Thread.
     * 
     * @param <T>   The event type
     * @param event The event to publish
     */
    public <T extends AppEvent> void publish(T event) {
        if (event == null) {
            logger.warning("Attempted to publish null event");
            return;
        }
        
        Class<?> eventClass = event.getClass();
        List<Consumer<? extends AppEvent>> handlers = subscribers.get(eventClass);
        
        if (handlers != null && !handlers.isEmpty()) {
            logger.fine("Publishing event: " + eventClass.getSimpleName() + " to " + handlers.size() + " subscribers");
            
            // Ensure events are processed on the JavaFX Application Thread
            if (Platform.isFxApplicationThread()) {
                dispatchEvent(event, handlers);
            } else {
                Platform.runLater(() -> dispatchEvent(event, handlers));
            }
        }
    }
    
    /**
     * Publishes an event synchronously (blocks until all handlers complete).
     * Use with caution - only for cases where synchronous execution is required.
     * 
     * @param <T>   The event type
     * @param event The event to publish
     */
    public <T extends AppEvent> void publishSync(T event) {
        if (event == null) {
            return;
        }
        
        List<Consumer<? extends AppEvent>> handlers = subscribers.get(event.getClass());
        if (handlers != null) {
            dispatchEvent(event, handlers);
        }
    }
    
    /**
     * Dispatches an event to all handlers.
     */
    @SuppressWarnings("unchecked")
    private <T extends AppEvent> void dispatchEvent(T event, List<Consumer<? extends AppEvent>> handlers) {
        for (Consumer<? extends AppEvent> handler : handlers) {
            try {
                ((Consumer<T>) handler).accept(event);
            } catch (Exception e) {
                logger.log(Level.SEVERE,
                        "Error handling event " + event.getClass().getSimpleName(),
                        e);
            }
        }
    }
    
    /**
     * Clears all subscribers. Useful for testing or reset.
     */
    public void clear() {
        subscribers.clear();
        logger.info("EventBus cleared");
    }
    
    /**
     * Gets the count of subscribers for a specific event type.
     * 
     * @param eventType The event type
     * @return The subscriber count
     */
    public int getSubscriberCount(Class<? extends AppEvent> eventType) {
        List<Consumer<? extends AppEvent>> handlers = subscribers.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }
    
    /**
     * Represents a subscription that can be cancelled.
     */
    public static class Subscription {
        /**
         * No-op subscription used when a real subscription cannot be created.
         * This avoids returning null and keeps caller code null-safe.
         */
        public static final Subscription NO_OP = new Subscription(() -> {
        }) {
            @Override
            public void cancel() {
                // intentionally no-op
            }

            @Override
            public boolean isCancelled() {
                // NO_OP is a null-object: its cancel/isCancelled state is permanently inert.
                return false;
            }
        };

        private final Runnable unsubscribe;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        
        Subscription(Runnable unsubscribe) {
            this.unsubscribe = unsubscribe;
        }
        
        /**
         * Cancels this subscription.
         */
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                unsubscribe.run();
            }
        }
        
        /**
         * Checks if this subscription is cancelled.
         * 
         * @return true if cancelled
         */
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
