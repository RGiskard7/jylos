package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.service.NoteService;
import com.example.jylos.util.MarkdownPreview;

/**
 * The reading view resolves links like the backlinks and the graph: by path, from the note
 * being shown. A link to a path that does not exist is marked as such even when a note with
 * that name lives elsewhere; links and embeds inside code stay code; the link a click sends
 * carries its folder so the right note opens.
 */
class PreviewLinkResolutionTest {

    @Test
    void linksResolveByPathFromTheNoteShown(@TempDir Path vault) throws Exception {
        write(vault, "Carpeta/Nota.md", "# Nota\n");
        write(vault, "Fuente.md", "x\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        Note source = notes.getNoteById("Fuente.md").orElseThrow();

        String html = MarkdownPreview.buildPreviewHtml(
                "[[Carpeta/Nota]] [[Otra/Nota]] `[[Nota]]`\n\n```\n[[Nota]] ![[Nota]]\n```\n",
                false, List.of(), new MarkdownPreview.PreviewOptions(null, t -> "embebido", new PreviewContext(source, false), false, 0, null,
                        notes.linkIndex()));

        assertEquals(List.of("wikilink|Carpeta/Nota", "wikilink wikilink-new|Otra/Nota"), anchors(html));
        assertTrue(html.contains("<code>[[Nota]]</code>"), "inline code keeps the link as text");
        assertTrue(html.contains("[[Nota]] ![[Nota]]"), "a code block keeps links and embeds as text");
        assertFalse(html.contains("embebido"), "an embed inside code is not expanded");
    }

    /** class|data-target of each wiki-link anchor, in order. */
    private static List<String> anchors(String html) {
        Matcher m = Pattern.compile("<a class=\"(wikilink[^\"]*)\" href=\"[^\"]*\" data-target=\"([^\"]*)\"").matcher(html);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1) + "|" + m.group(2));
        }
        return out;
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
