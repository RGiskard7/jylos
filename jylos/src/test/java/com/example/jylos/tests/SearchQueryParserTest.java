package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.search.SearchFilter;
import com.example.jylos.search.SearchFilter.Field;
import com.example.jylos.search.SearchQuery;
import com.example.jylos.search.SearchQueryParser;

/** Unit tests for the advanced-search query parser (pure, no services). */
class SearchQueryParserTest {

    private static List<SearchFilter> parse(String q) {
        return SearchQueryParser.parse(q).filters();
    }

    private static SearchFilter only(String q) {
        List<SearchFilter> f = parse(q);
        assertEquals(1, f.size(), "expected one filter for: " + q);
        return f.get(0);
    }

    @Test
    void blankQueryHasNoFilters() {
        assertTrue(SearchQueryParser.parse("").isEmpty());
        assertTrue(SearchQueryParser.parse(null).isEmpty());
        assertTrue(SearchQueryParser.parse("    ").isEmpty());
    }

    @Test
    void parsesFreeTextWords() {
        List<SearchFilter> f = parse("java spring");
        assertEquals(2, f.size());
        assertEquals(Field.TEXT, f.get(0).field());
        assertEquals("java", f.get(0).value());
        assertEquals("spring", f.get(1).value());
        assertFalse(f.get(0).negated());
    }

    @Test
    void parsesQuotedPhraseAsSingleText() {
        SearchFilter f = only("\"design patterns\"");
        assertEquals(Field.TEXT, f.field());
        assertEquals("design patterns", f.value());
    }

    @Test
    void parsesTagFolderTitleBody() {
        assertEquals(Field.TAG, only("tag:java").field());
        assertEquals("java", only("tag:java").value());
        assertEquals(Field.FOLDER, only("folder:backend").field());
        assertEquals(Field.TITLE, only("title:draft").field());

        SearchFilter body = only("body:\"java virtual machine\"");
        assertEquals(Field.BODY, body.field());
        assertEquals("java virtual machine", body.value(), "quoted operator value is unwrapped");
    }

    @Test
    void parsesNegation() {
        SearchFilter f = only("-tag:archive");
        assertEquals(Field.TAG, f.field());
        assertEquals("archive", f.value());
        assertTrue(f.negated());

        assertTrue(only("-title:draft").negated());
    }

    @Test
    void parsesSimpleDates() {
        assertEquals("2026", only("created:2026").value());
        assertEquals(Field.CREATED, only("created:2026").field());
        assertEquals("last-week", only("modified:last-week").value());
        assertEquals("2026-06-13", only("modified:2026-06-13").value());
    }

    @Test
    void parsesBooleanAndAliases() {
        assertEquals("true", only("favorite:true").value());
        assertEquals("true", only("private:yes").value());
        assertEquals(Field.PRIVATE, only("encrypted:true").field(), "encrypted is an alias of private");
        assertEquals("false", only("favorite:no").value());
    }

    @Test
    void parsesHasAndIs() {
        assertEquals("backlinks", only("has:backlinks").value());
        assertEquals("tag", only("has:tags").value(), "plural normalized to singular target");
        assertEquals("links", only("has:links").value());
        assertEquals(Field.IS, only("is:orphan").field());
    }

    @Test
    void combinesFreeTextAndOperators() {
        List<SearchFilter> f = parse("java tag:spring \"unit test\" -tag:archive");
        assertEquals(4, f.size());
        assertEquals(Field.TEXT, f.get(0).field());
        assertEquals(Field.TAG, f.get(1).field());
        assertEquals(Field.TEXT, f.get(2).field());
        assertEquals("unit test", f.get(2).value());
        assertTrue(f.get(3).negated());
    }

    @Test
    void ignoresExtraWhitespace() {
        assertEquals(2, parse("   tag:java     java   ").size());
    }

    @Test
    void unknownOperatorBecomesFreeTextWithWarning() {
        SearchQuery q = SearchQueryParser.parse("foo:bar");
        assertEquals(1, q.filters().size());
        assertEquals(Field.TEXT, q.filters().get(0).field());
        assertEquals("foo:bar", q.filters().get(0).value());
        assertFalse(q.warnings().isEmpty(), "unknown operator should warn");
    }

    @Test
    void invalidValuesAreDroppedWithWarningNotThrown() {
        // bad date, bad boolean, bad has/is targets → no filters, but warnings present.
        SearchQuery date = SearchQueryParser.parse("modified:notadate");
        assertTrue(date.isEmpty());
        assertFalse(date.warnings().isEmpty());

        assertTrue(SearchQueryParser.parse("favorite:maybe").isEmpty());
        assertTrue(SearchQueryParser.parse("has:stuff").isEmpty());
        assertTrue(SearchQueryParser.parse("is:banana").isEmpty());
    }

    @Test
    void malformedQueryDoesNotThrow() {
        // Unterminated quote and stray dash must not blow up.
        SearchQuery q = SearchQueryParser.parse("\"unterminated phrase");
        assertEquals(1, q.filters().size());
        assertEquals("unterminated phrase", q.filters().get(0).value());
        assertTrue(SearchQueryParser.parse("-").filters().isEmpty(), "lone dash is ignored");
    }
}
