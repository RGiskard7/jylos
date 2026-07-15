package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void detectsNewerSemanticVersionNumerically() {
        assertTrue(UpdateChecker.isNewerVersion("v1.10.0", "v1.9.9"));
        assertTrue(UpdateChecker.isNewerVersion("2.4.1", "2.4.0"));
        assertTrue(UpdateChecker.isNewerVersion("v2.5.0", "2.4.9"));
    }

    @Test
    void ignoresLeadingVAndRejectsSameOrOlderVersions() {
        assertFalse(UpdateChecker.isNewerVersion("v2.4.0", "2.4.0"));
        assertFalse(UpdateChecker.isNewerVersion("2.4.0", "v2.4.1"));
        assertFalse(UpdateChecker.isNewerVersion("v2.3.9", "2.4.0"));
    }
}
