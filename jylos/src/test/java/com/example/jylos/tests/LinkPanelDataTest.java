package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.BacklinkService.LinkGroup;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.service.NoteService;

/**
 * What the links panel shows for the open note: who links to it with the line of each link,
 * who mentions it (or its aliases) without linking, where it links to, and turning a mention
 * into a link.
 */
class LinkPanelDataTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void backlinksAreGroupedByNoteWithTheLineOfEachLink(@TempDir Path vault) throws Exception {
        fixture(vault);
        NoteService notes = notes(vault);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        try {
            List<LinkGroup> groups = links.backlinkGroups(note(notes, "Target.md"));

            assertEquals(List.of("SourceA", "SourceB"), groups.stream().map(g -> g.note().getTitle()).toList());
            assertEquals(List.of("[[Target]]", "[[Target|alias]]", "[[Target#Target]]"),
                    groups.get(0).occurrences().stream().map(Occurrence::matched).toList());
            assertEquals(List.of(0, 0, 1),
                    groups.get(0).occurrences().stream().map(Occurrence::line).toList());
            assertEquals("Aquí va [[Target]] y [[Target|alias]]", groups.get(0).occurrences().get(0).lineText());
            assertEquals(List.of("[[Target]]"), groups.get(1).occurrences().stream().map(Occurrence::matched).toList(),
                    "SourceB's link to Out1 is not a backlink of Target");
        } finally {
            links.shutdown();
        }
    }

    @Test
    void mentionsWithoutALinkIncludeTheAliasesAndSkipTheNoteItself(@TempDir Path vault) throws Exception {
        fixture(vault);
        NoteService notes = notes(vault);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        try {
            List<LinkGroup> groups = links.unlinkedMentions(note(notes, "Target.md"), () -> false);

            assertEquals(List.of("Unlinked"), groups.stream().map(g -> g.note().getTitle()).toList(),
                    "SourceA/B only link it, and Target.md is the note itself");
            assertEquals(List.of("Target", "Alias Target", "Target"),
                    groups.get(0).occurrences().stream().map(Occurrence::matched).toList());
        } finally {
            links.shutdown();
        }
    }

    @Test
    void aCancelledSearchStopsEarly(@TempDir Path vault) throws Exception {
        fixture(vault);
        NoteService notes = notes(vault);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        try {
            assertEquals(List.of(), links.unlinkedMentions(note(notes, "Target.md"), () -> true));
        } finally {
            links.shutdown();
        }
    }

    @Test
    void outgoingLinksAreUniqueDestinationsWithTheirCountAndWhetherTheyExist(@TempDir Path vault) throws Exception {
        fixture(vault);
        NoteService notes = notes(vault);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        try {
            List<OutgoingLink> out = links.outgoingLinks(note(notes, "Target.md"));

            assertEquals(List.of("Out1", "Missing"), out.stream().map(OutgoingLink::name).toList());
            assertEquals(List.of(2, 1), out.stream().map(OutgoingLink::count).toList());
            assertEquals("Out1.md", out.get(0).note().getId());
            assertEquals(null, out.get(1).note());
            assertFalse(out.get(1).resolved());
        } finally {
            links.shutdown();
        }
    }

    @Test
    void linkingAMentionRewritesThatOccurrenceAndMakesItABacklink(@TempDir Path vault) throws Exception {
        fixture(vault);
        NoteService notes = notes(vault);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        try {
            Note target = note(notes, "Target.md");
            LinkGroup group = links.unlinkedMentions(target, () -> false).get(0);
            String sourceId = group.note().getId();

            assertTrue(links.linkMention(sourceId, group.occurrences().get(1), target));   // "Alias Target"
            assertEquals("Habla de Target aquí.\nY de [[Target|Alias Target]] también.\n",
                    body(vault.resolve("Unlinked.md")));
            assertEquals(List.of("SourceA", "SourceB", "Unlinked"),
                    links.backlinksFor(target).stream().map(Note::getTitle).toList());

            // The file changed since the mention was found: nothing is rewritten.
            Files.writeString(vault.resolve("Unlinked.md"), "Otra cosa\n", StandardCharsets.UTF_8);
            assertFalse(links.linkMention(sourceId, group.occurrences().get(0), target));
            assertEquals("Otra cosa\n", body(vault.resolve("Unlinked.md")));
        } finally {
            links.shutdown();
        }
    }

    @Test
    void aMentionOfASameNamedNoteInAnotherFolderIsLinkedByPath(@TempDir Path vault) throws Exception {
        write(vault, "A/Target.md", "# A\n");
        write(vault, "B/Target.md", "# B\n");
        write(vault, "A/Src.md", "Menciona Target aquí\n");
        NoteService notes = notes(vault);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        try {
            Note other = note(notes, "B/Target.md");
            LinkGroup group = links.unlinkedMentions(other, () -> false).get(0);

            assertTrue(links.linkMention("A/Src.md", group.occurrences().get(0), other));

            assertEquals("Menciona [[B/Target|Target]] aquí\n", body(vault.resolve("A/Src.md")),
                    "[[Target]] from A/ would have gone to A/Target");
            assertEquals(List.of("Src"), links.backlinksFor(other).stream().map(Note::getTitle).toList());
        } finally {
            links.shutdown();
        }
    }

    @Test
    void sqliteGivesTheSamePanelData(@TempDir Path dir) throws Exception {
        SQLiteTestSupport.configureFreshDatabase(dir.resolve("panel.sqlite"));
        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folders = new FolderDAOSQLite(connection);
            NoteDAOSQLite dao = new NoteDAOSQLite(connection);
            Folder folder = new Folder("Carpeta");
            folder.setId(folders.createFolder(folder));
            Note target = create(dao, folder, "Target", "[[Out1]] [[Falta]]");
            create(dao, folder, "Out1", "");
            Note linker = create(dao, null, "Linker", "Uno [[Carpeta/Target]]\nDos");
            Note mentioner = create(dao, null, "Mentioner", "Habla de Target aquí");
            NoteService notes = new NoteService(dao, folders);
            BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
            try {
                assertEquals(List.of("Linker"),
                        links.backlinkGroups(target).stream().map(g -> g.note().getTitle()).toList());
                assertEquals(List.of("Mentioner"),
                        links.unlinkedMentions(target, () -> false).stream().map(g -> g.note().getTitle()).toList());
                assertEquals(List.of("Out1", "Falta"),
                        links.outgoingLinks(target).stream().map(OutgoingLink::name).toList());

                LinkGroup group = links.unlinkedMentions(target, () -> false).get(0);
                assertTrue(links.linkMention(mentioner.getId(), group.occurrences().get(0), target));
                assertEquals("Habla de [[Target]] aquí",
                        notes.getNoteById(mentioner.getId()).orElseThrow().getContent());
                assertEquals(List.of("Linker", "Mentioner"),
                        links.backlinksFor(target).stream().map(Note::getTitle).toList());
                assertEquals(linker.getId(), links.backlinkGroups(target).get(0).note().getId());
            } finally {
                links.shutdown();
            }
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    private static void fixture(Path vault) throws Exception {
        write(vault, "Target.md", "---\naliases: [Alias Target]\n---\n[[Out1]] [[Missing]] [[Out1|alias]]\n");
        write(vault, "Out1.md", "# Out1\n");
        write(vault, "SourceA.md", "Aquí va [[Target]] y [[Target|alias]]\nOtra [[Target#Target]]\n");
        write(vault, "SourceB.md", "[[Target]] y [[Out1]]\n");
        write(vault, "Unlinked.md", "Habla de Target aquí.\nY de Alias Target también.\n");
    }

    private static NoteService notes(Path vault) {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    /** The note's text without the properties block Jylos writes when it saves a file. */
    private static String body(Path file) throws Exception {
        return com.example.jylos.data.dao.filesystem.FrontmatterHandler.stripFrontmatter(Files.readString(file));
    }

    private static Note note(NoteService notes, String id) {
        return notes.getNoteById(id).orElseThrow();
    }

    private static Note create(NoteDAOSQLite dao, Folder folder, String title, String content) {
        Note note = new Note(title, content);
        if (folder != null) {
            note.setParent(folder);
        }
        dao.createNote(note);
        return note;
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
