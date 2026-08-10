package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for the query language.
 *
 * <p>Wiki-links and tags are lexed as single tokens rather than being assembled from
 * punctuation by the parser: {@code [[Some Note]]} and {@code #project/active} both
 * contain characters that are operators everywhere else, and treating them atomically
 * here keeps that ambiguity out of the grammar.</p>
 */
final class DqlLexer {

    enum Type {
        IDENT, NUMBER, STRING, LINK, TAG, OPERATOR, EOF
    }

    record Token(Type type, String text, int position) {

        boolean is(Type expected, String expectedText) {
            return type == expected && text.equalsIgnoreCase(expectedText);
        }

        boolean isOperator(String expectedText) {
            return type == Type.OPERATOR && text.equals(expectedText);
        }

        boolean isKeyword(String expectedText) {
            return type == Type.IDENT && text.equalsIgnoreCase(expectedText);
        }
    }

    /** Multi-character operators, longest first so {@code >=} never lexes as {@code >}. */
    private static final String[] OPERATORS = {
            ">=", "<=", "!=", "<>", "==", "&&", "||", "=", "<", ">", "+", "-", "*", "/", "%",
            "(", ")", "[", "]", "{", "}", ",", ".", ":", "!"
    };

    private final String source;
    private int position;

    private DqlLexer(String source) {
        this.source = source;
    }

    static List<Token> tokenize(String source) {
        return new DqlLexer(source == null ? "" : source).run();
    }

    private List<Token> run() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespaceAndComments();
            if (position >= source.length()) {
                tokens.add(new Token(Type.EOF, "", position));
                return tokens;
            }
            tokens.add(nextToken());
        }
    }

    private void skipWhitespaceAndComments() {
        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isWhitespace(c)) {
                position++;
                continue;
            }
            // Line comments let users annotate long queries without breaking parsing.
            if (c == '/' && position + 1 < source.length() && source.charAt(position + 1) == '/') {
                while (position < source.length() && source.charAt(position) != '\n') {
                    position++;
                }
                continue;
            }
            return;
        }
    }

    private Token nextToken() {
        int start = position;
        char c = source.charAt(position);

        if (c == '"' || c == '\'') {
            return new Token(Type.STRING, readQuoted(c), start);
        }
        if (c == '[' && peekIs(position + 1, '[')) {
            return new Token(Type.LINK, readLink(), start);
        }
        if (c == '#') {
            return new Token(Type.TAG, readTag(), start);
        }
        if (Character.isDigit(c)) {
            return new Token(Type.NUMBER, readNumber(), start);
        }
        if (Character.isLetter(c) || c == '_') {
            return new Token(Type.IDENT, readIdentifier(), start);
        }

        for (String operator : OPERATORS) {
            if (source.startsWith(operator, position)) {
                position += operator.length();
                return new Token(Type.OPERATOR, operator, start);
            }
        }
        throw new DqlException("Unexpected character '" + c + "' at position " + position);
    }

    private boolean peekIs(int at, char expected) {
        return at < source.length() && source.charAt(at) == expected;
    }

    private String readQuoted(char quote) {
        position++;
        StringBuilder text = new StringBuilder();
        while (position < source.length() && source.charAt(position) != quote) {
            char c = source.charAt(position);
            if (c == '\\' && position + 1 < source.length()) {
                position++;
                char escaped = source.charAt(position);
                text.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    default -> escaped;
                });
            } else {
                text.append(c);
            }
            position++;
        }
        if (position >= source.length()) {
            throw new DqlException("Unterminated string literal");
        }
        position++;
        return text.toString();
    }

    private String readLink() {
        int close = source.indexOf("]]", position + 2);
        if (close < 0) {
            throw new DqlException("Unterminated link literal");
        }
        String inner = source.substring(position + 2, close);
        position = close + 2;
        return inner;
    }

    private String readTag() {
        int start = position;
        position++;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/') {
                position++;
            } else {
                break;
            }
        }
        if (position == start + 1) {
            throw new DqlException("Empty tag at position " + start);
        }
        return source.substring(start, position);
    }

    private String readNumber() {
        int start = position;
        while (position < source.length()
                && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
            // A '.' only continues the number when a digit follows, so "3.name" still
            // lexes as number, dot, identifier.
            if (source.charAt(position) == '.'
                    && !(position + 1 < source.length() && Character.isDigit(source.charAt(position + 1)))) {
                break;
            }
            position++;
        }
        return source.substring(start, position);
    }

    private String readIdentifier() {
        int start = position;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isLetterOrDigit(c) || c == '_') {
                position++;
            } else {
                break;
            }
        }
        return source.substring(start, position);
    }
}
