package com.example.jylos.event;

import java.time.Instant;

/**
 * Base class for all application events.
 * 
 * <p>Events are immutable objects that carry information about something that happened
 * in the application. They are used for decoupled communication between components.</p>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public abstract class AppEvent {
    
    private final Instant timestamp;
    private final String source;
    
    /**
     * Creates a new event with the current timestamp.
     */
    protected AppEvent() {
        this.timestamp = Instant.now();
        this.source = null;
    }
    
    /**
     * Creates a new event with a specified source.
     * 
     * @param source The source component that created the event
     */
    protected AppEvent(String source) {
        this.timestamp = Instant.now();
        this.source = source;
    }
    
    /**
     * Gets the timestamp when this event was created.
     * 
     * @return The event timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the source component that created this event.
     * 
     * @return The source identifier, or null if not specified
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Gets the event type name (class simple name).
     * 
     * @return The event type name
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
    
    @Override
    public String toString() {
        return getEventType() + "{timestamp=" + timestamp + ", source=" + source + "}";
    }
}
