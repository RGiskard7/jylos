package com.example.jylos.plugin.builtin.dataview;

/**
 * Raised for any query the plugin cannot lex, parse or evaluate.
 *
 * <p>Carries a reader-facing message: it is rendered into the note preview as an error
 * box, so it must explain what is wrong with the query rather than leak Java internals.</p>
 */
final class DqlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DqlException(String message) {
        super(message);
    }
}
