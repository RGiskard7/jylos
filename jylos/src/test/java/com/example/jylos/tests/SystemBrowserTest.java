package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.SystemBrowser;

/**
 * Only the refusal path is exercised here — opening a real {@code http(s)} URL
 * would launch the system browser, which is not something a test should do.
 */
class SystemBrowserTest {

    @Test
    void refusesNullAndBlank() {
        assertFalse(SystemBrowser.open(null));
        assertFalse(SystemBrowser.open("   "));
    }

    @Test
    void refusesNonHttpSchemes() {
        assertFalse(SystemBrowser.open("ftp://example.com"));
        assertFalse(SystemBrowser.open("file:///etc/passwd"));
        assertFalse(SystemBrowser.open("javascript:alert(1)"));
        assertFalse(SystemBrowser.open("mailto:a@b.com"));
    }
}
