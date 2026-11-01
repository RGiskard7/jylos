package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.util.MarkdownPreview;

/**
 * The preview inlines local images as {@code data:} URIs. A note's Markdown decides which
 * file gets read, so an image reference must not reach files outside the vault — otherwise
 * {@code ![](../../.ssh/id_rsa)} embeds that file in the preview page and in any HTML
 * export. Images elsewhere inside the vault ({@code ../attachments/x.png}) are legitimate.
 */
class PreviewImageContainmentTest {

    private static final byte[] INSIDE_BYTES = "inside-image-bytes".getBytes();
    private static final byte[] SECRET_BYTES = "outside-secret-bytes".getBytes();

    @TempDir
    Path sandbox;

    private Path vault;
    private Path noteDir;

    @BeforeEach
    void setUp() throws IOException {
        vault = Files.createDirectories(sandbox.resolve("vault"));
        noteDir = Files.createDirectories(vault.resolve("notes"));
        Files.createDirectories(vault.resolve("attachments"));
        Files.write(vault.resolve("attachments").resolve("ok.png"), INSIDE_BYTES);
        Files.write(sandbox.resolve("secret.png"), SECRET_BYTES);
    }

    @Test
    void inlinesAnImageElsewhereInsideTheVault() {
        String resolved = MarkdownPreview.resolveImageSource("../attachments/ok.png", noteDir, vault);
        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(INSIDE_BYTES), resolved);
    }

    @Test
    void refusesAnImageOutsideTheVault() {
        assertEquals("", MarkdownPreview.resolveImageSource("../../secret.png", noteDir, vault));
    }

    @Test
    void refusesAnAbsoluteImagePathOutsideTheVault() {
        String absolute = sandbox.resolve("secret.png").toAbsolutePath().toString();
        assertEquals("", MarkdownPreview.resolveImageSource(absolute, noteDir, vault));
    }

    @Test
    void withoutAVaultRootImagesAreContainedInTheNoteFolder() {
        assertEquals("", MarkdownPreview.resolveImageSource("../../secret.png", noteDir, null));
        assertEquals("", MarkdownPreview.resolveImageSource("../../secret.png", noteDir));
    }

    @Test
    void remoteAndDataSourcesAreLeftAsIs() {
        assertEquals("https://example.com/a.png",
                MarkdownPreview.resolveImageSource("https://example.com/a.png", noteDir, vault));
        assertEquals("data:image/png;base64,AAAA",
                MarkdownPreview.resolveImageSource("data:image/png;base64,AAAA", noteDir, vault));
    }

    @Test
    void renderedPreviewNeverEmbedsTheBytesOfAFileOutsideTheVault() {
        String html = MarkdownPreview.buildPreviewHtml(
                "![ok](../attachments/ok.png)\n\n![leak](../../secret.png)",
                false, null, noteDir, null, null, false, 0, vault);
        assertTrue(html.contains(Base64.getEncoder().encodeToString(INSIDE_BYTES)),
                "the image inside the vault must be inlined");
        assertFalse(html.contains(Base64.getEncoder().encodeToString(SECRET_BYTES)),
                "the file outside the vault must never be read into the page");
    }

    @Test
    void filesystemVaultExposesItsRootForContainment() {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        assertEquals(vault.toAbsolutePath().normalize(),
                dao.storageRoot().orElseThrow().toAbsolutePath().normalize());
    }
}
