package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * WebP images in the vault are listed and open in the image viewer, as in Obsidian. SVG and
 * AVIF are not listed yet: the image viewer cannot draw them.
 */
class AttachmentTypeImagesTest {

    @Test
    void aWebpIsAnImageOfTheVault() {
        assertEquals(AttachmentType.IMAGE, AttachmentType.fromName("Adjuntos/foto.WEBP"));
        assertTrue(AttachmentType.isSupportedVaultFile("Adjuntos/foto.webp"));
    }

    @Test
    void whatTheViewerCannotDrawIsNotListed() {
        assertFalse(AttachmentType.isSupportedVaultFile("logo.svg"));
        assertFalse(AttachmentType.isSupportedVaultFile("foto.avif"));
    }
}
