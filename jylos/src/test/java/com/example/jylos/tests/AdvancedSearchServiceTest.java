package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.search.AdvancedSearchService;

/**
 * Tests the advanced-search runtime filters that don't require backing services
 * (free text, title:, body:, favorite:, private:, dates, negation, combinations).
 * Tag/folder/graph operators are covered indirectly by the parser tests; here the
 * service is built with null services and only field filters that touch the note
 * itself are exercised.
 */
class AdvancedSearchServiceTest {

    private final AdvancedSearchService search = new AdvancedSearchService(null, null);

    private static Note note(String id, String title, String content) {
        Note n = new Note(id, title, content);
        n.setModifiedDate("2020-01-01T00:00:00Z");
        n.setCreatedDate("2020-01-01T00:00:00Z");
        return n;
    }

    private List<String> ids(String query, List<Note> notes) {
        return search.searchNotes(query, notes, null).stream().map(Note::getId).toList();
    }

    private List<Note> sample() {
        Note java = note("1", "Java Guide", "the JVM rocks");
        java.setFavorite(true);
        Note spring = note("2", "Spring", "beans and DI");
        spring.setPrivate(true);
        Note draft = note("3", "Draft", "todo later");
        return List.of(java, spring, draft);
    }

    @Test
    void freeTextMatchesTitleOrBody() {
        assertEquals(List.of("1"), ids("java", sample()), "matches title 'Java Guide'");
        assertEquals(List.of("1"), ids("jvm", sample()), "matches body");
        assertTrue(ids("nonexistent", sample()).isEmpty());
    }

    @Test
    void titleAndBodyOperators() {
        assertEquals(List.of("2"), ids("title:spring", sample()));
        assertEquals(List.of("2"), ids("body:beans", sample()));
        assertTrue(ids("title:beans", sample()).isEmpty(), "title: does not look at body");
    }

    @Test
    void quotedPhraseMatches() {
        assertEquals(List.of("1"), ids("\"jvm rocks\"", sample()));
        assertTrue(ids("\"rocks jvm\"", sample()).isEmpty(), "phrase order matters");
    }

    @Test
    void favoriteAndPrivateFilters() {
        assertEquals(List.of("1"), ids("favorite:true", sample()));
        assertEquals(List.of("2"), ids("private:true", sample()));
        assertEquals(List.of("2"), ids("encrypted:true", sample()), "encrypted is an alias of private");
    }

    @Test
    void negationExcludesMatches() {
        assertEquals(List.of("1", "2"), ids("-title:draft", sample()));
        assertEquals(List.of("2", "3"), ids("-favorite:true", sample()));
    }

    @Test
    void combinedFiltersAreAnded() {
        assertEquals(List.of("1"), ids("java favorite:true", sample()));
        assertTrue(ids("spring favorite:true", sample()).isEmpty(), "no note is both 'spring' and favorite");
    }

    @Test
    void simpleDateFiltersMatch() {
        Note recent = note("9", "Recent", "fresh");
        recent.setModifiedDate(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        List<Note> notes = List.of(recent, note("10", "Old", "stale"));

        assertEquals(List.of("9"), ids("modified:today", notes));
        assertEquals(List.of("10"), ids("modified:2020", notes));
    }

    @Test
    void malformedQueryNeverThrowsAndDegrades() {
        // Unknown operator → literal text, matches nothing here, no exception.
        assertTrue(ids("foo:bar", sample()).isEmpty());
        // Invalid date is dropped → query has no filters → returns everything.
        assertEquals(3, ids("modified:notadate", sample()).size());
    }
}
