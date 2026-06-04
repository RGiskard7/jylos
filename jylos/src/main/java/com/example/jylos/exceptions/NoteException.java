package com.example.jylos.exceptions;

/**
 * Custom exception for handling note-related errors.
 * 
 * This exception is thrown when an error occurs related to note operations,
 * such as invalid note data, missing notes, or failed note manipulations.
 * 
 * It extends {@link Exception}, making it a checked exception that must be
 * either caught or declared in method signatures.
 * 
 */
public class NoteException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new NoteException with the specified detail message.
     *
     * @param message The error message describing the issue.
     */
	public NoteException(String message) {
        super(message);
    }

    /**
     * Constructs a new NoteException with the specified detail message and cause.
     *
     * @param message The error message describing the issue.
     * @param cause The original exception that caused this error.
     */
    public NoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
