package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThemeCatalogTest {

    @Test
    void resolveBaseVariantUsesMenuSystemWhenBaseAuto() {
        ThemeCatalog.ThemeDescriptor theme = new ThemeCatalog.ThemeDescriptor(
                "test", "Test", "external", "file:///x.css", true, "auto");
        assertEquals("dark", theme.resolveBaseVariant("system", () -> "dark"));
        assertEquals("light", theme.resolveBaseVariant("light", () -> "dark"));
    }

    @Test
    void resolveBaseVariantHonorsConfiguredBase() {
        ThemeCatalog.ThemeDescriptor theme = new ThemeCatalog.ThemeDescriptor(
                "test", "Test", "external", "file:///x.css", false, "system");
        assertEquals("light", theme.resolveBaseVariant("dark", () -> "light"));
    }
}
