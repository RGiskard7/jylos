package com.example.jylos.exceptions;

/**
 * Custom exception for data access errors.
 *
 * This exception is thrown when an error occurs while accessing the database
 * or performing data operations.
 *
 * It extends {@link RuntimeException}, allowing it to be thrown without explicit handling.
 *
 */
public class DataAccessException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
    /**
     * Constructs a new DataAccessException with the specified detail message and cause.
     *
     * @param message The error message describing the issue.
     * @param cause The original exception that caused this error.
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

}