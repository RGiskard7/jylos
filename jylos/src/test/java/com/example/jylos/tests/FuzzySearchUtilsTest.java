package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.FuzzySearchUtils;

class FuzzySearchUtilsTest {

    @Test
    void exactMatchScoresHighest() {
        int score = FuzzySearchUtils.fuzzyScore("hello", "hello world");
        assertTrue(score > 100, "Exact substring match should score > 100, got " + score);
    }

    @Test
    void prefixMatchScoresHigherThanSubstring() {
        int scorePrefix = FuzzySearchUtils.fuzzyScore("hel", "hello world");
        int scoreMid = FuzzySearchUtils.fuzzyScore("wor", "hello world");
        assertTrue(scorePrefix > scoreMid,
                "Prefix match should score higher (" + scorePrefix + " vs " + scoreMid + ")");
    }

    @Test
    void subsequenceMatchWorks() {
        // "hw" matches "hello world" via h...w subsequence
        assertTrue(FuzzySearchUtils.matches("hw", "hello world"));
    }

    @Test
    void noMatchReturnsZero() {
        assertEquals(0, FuzzySearchUtils.fuzzyScore("xyz", "hello world"));
    }

    @Test
    void caseInsensitive() {
        assertTrue(FuzzySearchUtils.matches("HELLO", "hello world"));
    }

    @Test
    void nullInputReturnsZero() {
        assertEquals(0, FuzzySearchUtils.fuzzyScore(null, "test"));
        assertEquals(0, FuzzySearchUtils.fuzzyScore("test", null));
    }

    @Test
    void emptyQueryReturnsZero() {
        assertEquals(0, FuzzySearchUtils.fuzzyScore("", "test"));
    }

    @Test
    void matchesConvenienceMethodWorks() {
        assertTrue(FuzzySearchUtils.matches("note", "My Important Note"));
        assertFalse(FuzzySearchUtils.matches("xyz", "My Important Note"));
    }
}
