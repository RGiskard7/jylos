package com.example.jylos.search;

/**
 * One clause of a parsed search query: a {@link Field}, its raw value, and whether it
 * was negated with a leading {@code -}.
 *
 * <p>Free text and quoted phrases both become {@link Field#TEXT} filters; everything
 * else is an {@code operator:value} clause. The parser only emits filters it considers
 * valid — invalid operators are dropped with a warning on the {@link SearchQuery}.</p>
 *
 * @param field   which note attribute this clause constrains
 * @param value   the (unquoted, trimmed) value; semantics depend on {@code field}
 * @param negated true when the clause was prefixed with {@code -} (must NOT match)
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record SearchFilter(Field field, String value, boolean negated) {

    /** The note attribute a {@link SearchFilter} constrains. */
    public enum Field {
        /** Free text / quoted phrase — matched against title and body. */
        TEXT,
        /** {@code title:...} — substring of the note title. */
        TITLE,
        /** {@code body:...} — substring of the note content. */
        BODY,
        /** {@code tag:...} — the note carries this tag. */
        TAG,
        /** {@code folder:...} — the note lives in this folder. */
        FOLDER,
        /** {@code created:...} — creation date matches a date token. */
        CREATED,
        /** {@code modified:...} — modified date matches a date token. */
        MODIFIED,
        /** {@code favorite:true|false}. */
        FAVORITE,
        /** {@code private:true|false} / {@code encrypted:true|false}. */
        PRIVATE,
        /** {@code has:tag|links|backlinks}. */
        HAS,
        /** {@code is:orphan}. */
        IS
    }
}
