package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
                false, null, new MarkdownPreview.PreviewOptions(noteDir, null, null, false, 0, vault, null));
        assertTrue(html.contains(Base64.getEncoder().encodeToString(INSIDE_BYTES)),
                "the image inside the vault must be inlined");
        assertFalse(html.contains(Base64.getEncoder().encodeToString(SECRET_BYTES)),
                "the file outside the vault must never be read into the page");
    }

    // ── Images found by name, as Obsidian finds them ─────────────────────────────────

    private final List<String> asked = new ArrayList<>();

    /** Finds "ok.png" in the attachments folder, as the vault's link index would. */
    private Optional<Path> byName(String name) {
        asked.add(name);
        return "ok.png".equals(name) ? Optional.of(vault.resolve("attachments").resolve("ok.png")) : Optional.empty();
    }

    @Test
    void anImageNotBesideTheNoteIsFoundByItsName() {
        String resolved = MarkdownPreview.resolveImageSource("ok.png", noteDir, vault, this::byName);

        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(INSIDE_BYTES), resolved);
    }

    @Test
    void anImageBesideTheNoteIsNotLookedForElsewhere() throws IOException {
        Files.write(noteDir.resolve("ok.png"), SECRET_BYTES);

        String resolved = MarkdownPreview.resolveImageSource("ok.png", noteDir, vault, this::byName);

        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(SECRET_BYTES), resolved);
        assertEquals(List.of(), asked);
    }

    @Test
    void anImageFoundByNameStillCannotBeOutsideTheVault() {
        String resolved = MarkdownPreview.resolveImageSource("ok.png", noteDir, vault,
                name -> Optional.of(sandbox.resolve("secret.png")));

        assertEquals("", resolved);
    }

    /**
     * The reading view draws {@code ![[image]]} as an image (not as an embedded note), finds it
     * by name, and takes Obsidian's sizes: {@code |300}, {@code |300x100} and
     * {@code ![text|200](image)}.
     */
    @Test
    void theReadingViewDrawsWikiEmbeddedImagesWithTheirSize() {
        String html = MarkdownPreview.buildPreviewHtml(
                "![[ok.png]]\n\n![[ok.png|300]]\n\n![[ok.png|300x100]]\n\n![texto|200](ok.png)",
                false, null, new MarkdownPreview.PreviewOptions(noteDir, null, null, false, 0, vault, null)
                        .withImageLocator(this::byName));
        Document page = Jsoup.parse(html);
        List<String> images = page.select("img:not(.emoji)").stream()
                .map(img -> img.attr("width") + "x" + img.attr("height") + " " + img.attr("alt") + " "
                        + img.attr("src").startsWith("data:image/png;base64,"))
                .toList();

        assertEquals(List.of("x  true", "300x  true", "300x100  true", "200x texto true"), images);
        assertTrue(page.select("div.embed, .embed-missing").isEmpty(), "no embedded-note box: " + html);
    }

    @Test
    void filesystemVaultExposesItsRootForContainment() {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        assertEquals(vault.toAbsolutePath().normalize(),
                dao.storageRoot().orElseThrow().toAbsolutePath().normalize());
    }
}
