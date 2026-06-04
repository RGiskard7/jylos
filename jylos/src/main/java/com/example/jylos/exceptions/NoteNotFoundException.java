package com.example.jylos.exceptions;

/**
 * Exception thrown when a requested note is not found.
 * 
 * This exception is a subclass of {@link RuntimeException}, meaning it is 
 * unchecked and does not need to be explicitly declared in method signatures.
 * It is used to indicate that a requested note does not exist in the system.
 */
public class NoteNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new NoteNotFoundException with the specified detail message.
     *
     * @param message The error message describing the missing note.
     */
	public NoteNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new NoteNotFoundException with the specified detail message and cause.
     *
     * @param message The error message describing the missing note.
     * @param cause The original exception that caused this error.
     */
    public NoteNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}