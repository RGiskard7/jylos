package com.example.jylos.exceptions;

/**
 * Custom exception for invalid parameters.
 */
public class InvalidParameterException extends RuntimeException {
    private static final long serialVersionUID = 1L;

	/**
     * Constructs a new InvalidParameterException with the specified detail message.
     *
     * @param message the detail message
     */
    public InvalidParameterException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidParameterException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public InvalidParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}