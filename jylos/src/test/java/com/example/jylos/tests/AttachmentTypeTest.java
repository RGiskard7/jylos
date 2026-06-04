package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.AttachmentType;

/** Classification of vault files into editable notes vs. native-viewer attachments. */
class AttachmentTypeTest {

    @Test
    void markdownAndTextAreNotAttachments() {
        assertEquals(AttachmentType.MARKDOWN, AttachmentType.fromName("Folder/Note.md"));
        assertEquals(AttachmentType.MARKDOWN, AttachmentType.fromName("notes.markdown"));
        assertEquals(AttachmentType.MARKDOWN, AttachmentType.fromName("readme.txt"));
        assertFalse(AttachmentType.isAttachment("Folder/Note.md"));
    }

    @Test
    void pdfIsClassifiedAndIsAttachment() {
        assertEquals(AttachmentType.PDF, AttachmentType.fromName("Docs/report.PDF"));
        assertTrue(AttachmentType.isAttachment("report.pdf"));
    }

    @Test
    void commonImageExtensionsAreImages() {
        for (String name : new String[] { "a.png", "b.JPG", "c.jpeg", "d.gif", "e.bmp" }) {
            assertEquals(AttachmentType.IMAGE, AttachmentType.fromName(name), name);
            assertTrue(AttachmentType.isAttachment(name), name);
        }
    }

    @Test
    void extensionParsingIgnoresDotsInFolders() {
        assertEquals("png", AttachmentType.extensionOf("my.folder/diagram.png"));
        assertEquals("", AttachmentType.extensionOf("folder.with.dots/noext"));
    }

    @Test
    void supportedVaultFileCoversNotesAndAttachments() {
        assertTrue(AttachmentType.isSupportedVaultFile("note.md"));
        assertTrue(AttachmentType.isSupportedVaultFile("image.png"));
        assertTrue(AttachmentType.isSupportedVaultFile("doc.pdf"));
        assertFalse(AttachmentType.isSupportedVaultFile("archive.zip"));
    }
}
