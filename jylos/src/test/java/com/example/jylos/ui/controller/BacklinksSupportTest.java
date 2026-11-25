package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.BacklinkService.LinkGroup;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * The links section of the right panel, fed by real services: it shows who links to the open
 * note and where it links, searches unlinked mentions only when that section is opened, turns a
 * mention into a link on request, and creates a destination that does not exist yet.
 */
class BacklinksSupportTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void showsLinksOpensNotesCreatesMissingOnesAndLinksMentions(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "Target.md", "[[Existe]] [[Falta]]\n");
        write(vault, "Existe.md", "# Existe\n");
        write(vault, "Fuente.md", "Aquí [[Target]] fin\n");
        write(vault, "Menciona.md", "Habla de Target aquí\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        List<String> events = new ArrayList<>();
        BacklinksSupport support = new BacklinksSupport();
        VBox content = new VBox();
        try {
            onFx(() -> {
                support.wire(links, notes, k -> k, n -> events.add("open:" + n.getId()), () -> true);
                content.getChildren().setAll(support.links(), support.links().outgoingView());
                support.setCreateNoteAction(title -> events.add("create:" + title));
                support.setMentionLinkedAction(n -> events.add("saved:" + n.getId()));
                support.refresh(notes.getNoteById("Target.md").orElseThrow());
                return null;
            });

            // Linked mentions and outgoing links arrive off the FX thread.
            waitFor(() -> texts(content, "links-group-name").equals(List.of("Fuente"))
                    && texts(content, "links-outgoing-name").equals(List.of("Existe", "Falta")));
            assertEquals(List.of("[[Target]]"), texts(content, "links-match-highlight"));

            onFx(() -> {
                for (Node row : content.lookupAll(".links-outgoing-row")) {
                    click(row);
                }
                click(content.lookup(".links-group-title"));
                return null;
            });
            assertEquals(List.of("open:Existe.md", "create:Falta", "open:Fuente.md"), events);

            // Unlinked mentions are searched only when the section is opened.
            assertEquals(List.of("Fuente"), texts(content, "links-group-name"),
                    "Menciona.md is not listed before the section is opened");
            onFx(() -> {
                for (Node title : content.lookupAll(".links-section-title")) {
                    if ("links.unlinked_mentions".equals(((Labeled) title).getText())) {
                        click(title.getParent());
                    }
                }
                return null;
            });
            waitFor(() -> texts(content, "links-group-name").contains("Menciona"));

            onFx(() -> {
                ((Button) content.lookup(".links-link-btn")).fire();
                return null;
            });
            waitFor(() -> events.contains("saved:Menciona.md"));
            assertEquals("Habla de [[Target]] aquí\n",
                    FrontmatterHandler.stripFrontmatter(Files.readString(vault.resolve("Menciona.md"))));
            // The mention is now a link: both notes are linked mentions of Target.
            waitFor(() -> texts(content, "links-group-name").containsAll(List.of("Fuente", "Menciona")));
        } finally {
            links.shutdown();
        }
    }

    /** Creating or deleting a note changes what every link points to: the open note's panel follows. */
    @Test
    void creatingAndDeletingNotesRefreshesThePanel(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "A.md", "[[Nueva]]\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        BacklinksSupport support = new BacklinksSupport();
        VBox content = new VBox();
        List<EventBus.Subscription> subscriptions = new ArrayList<>();
        try {
            onFx(() -> {
                support.wire(links, notes, k -> k, n -> { }, () -> true);
                content.getChildren().setAll(support.links(), support.links().outgoingView());
                subscriptions.addAll(support.subscribe(EventBus.getInstance()));
                support.refresh(notes.getNoteById("A.md").orElseThrow());
                return null;
            });
            waitFor(() -> texts(content, "links-outgoing-name").equals(List.of("Nueva"))
                    && missingDestinations(content) == 1);

            Note created = notes.createNote("Nueva", "");
            EventBus.getInstance().publish(new NoteEvents.NoteCreatedEvent(created));
            waitFor(() -> missingDestinations(content) == 0);

            notes.moveToTrash(created.getId());
            EventBus.getInstance().publish(new NoteEvents.NoteDeletedEvent(created.getId(), "Nueva"));
            waitFor(() -> missingDestinations(content) == 1);
        } finally {
            subscriptions.forEach(EventBus.Subscription::cancel);
            links.shutdown();
        }
    }

    private static int missingDestinations(VBox content) {
        try {
            return onFx(() -> content.lookupAll(".links-outgoing-missing").size());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** A slow answer for a note that is no longer open must not replace the open note's links. */
    @Test
    void aLateAnswerForAnotherNoteIsDiscarded(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "A.md", "# A\n");
        write(vault, "B.md", "# B\n");
        write(vault, "FromA.md", "[[A]]\n");
        write(vault, "FromB.md", "[[B]]\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        CountDownLatch releaseA = new CountDownLatch(1);
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance()) {
            @Override
            public List<LinkGroup> backlinkGroups(Note target) {
                if ("A.md".equals(target.getId())) {
                    try {
                        releaseA.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return super.backlinkGroups(target);
            }
        };
        BacklinksSupport support = new BacklinksSupport();
        VBox content = new VBox();
        try {
            onFx(() -> {
                support.wire(links, notes, k -> k, n -> { }, () -> true);
                content.getChildren().setAll(support.links(), support.links().outgoingView());
                support.refresh(notes.getNoteById("A.md").orElseThrow());   // slow: waits for the latch
                support.refresh(notes.getNoteById("B.md").orElseThrow());
                return null;
            });
            waitFor(() -> texts(content, "links-group-name").equals(List.of("FromB")));

            releaseA.countDown();
            Thread.sleep(500);   // time for the late answer to arrive (and be ignored)
            assertEquals(List.of("FromB"), texts(content, "links-group-name"));
        } finally {
            releaseA.countDown();
            links.shutdown();
        }
    }

    /**
     * A copy pinned to a note shows that note's links whatever note is open, and its links open
     * notes as the panel's do; the panel that follows the open note goes on following it.
     */
    @Test
    void aPinnedCopyKeepsShowingItsNoteWhileTheOpenNoteChanges(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "Target.md", "[[Existe]]\n");
        write(vault, "Existe.md", "# Existe\n");
        write(vault, "Fuente.md", "Aquí [[Target]] fin\n");
        write(vault, "B.md", "# B\n");
        write(vault, "FromB.md", "[[B]]\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance());
        List<String> events = new ArrayList<>();
        BacklinksSupport support = new BacklinksSupport();
        VBox main = new VBox();
        VBox pinnedBack = new VBox();
        VBox pinnedOut = new VBox();
        try {
            onFx(() -> {
                support.wire(links, notes, k -> k, n -> events.add("open:" + n.getId()), () -> true);
                support.setOnPinnedShown((copy, note) -> events.add(copy + " shows " + (note != null ? note.getId() : null)));
                main.getChildren().setAll(support.links(), support.links().outgoingView());
                pinnedBack.getChildren().setAll(
                        support.pinnedPanel("backlinks@1", RightPanelSupport.BACKLINKS, "Target.md", () -> true));
                pinnedOut.getChildren().setAll(support.pinnedPanel("outgoing-links@2", RightPanelSupport.OUTGOING,
                        "Target.md", () -> true).outgoingView());
                support.refresh(notes.getNoteById("Target.md").orElseThrow());
                support.refreshPinned("backlinks@1");
                support.refreshPinned("outgoing-links@2");
                return null;
            });
            waitFor(() -> texts(pinnedBack, "links-group-name").equals(List.of("Fuente"))
                    && texts(pinnedOut, "links-outgoing-name").equals(List.of("Existe"))
                    && texts(main, "links-group-name").equals(List.of("Fuente")));

            onFx(() -> {
                support.refresh(notes.getNoteById("B.md").orElseThrow());
                return null;
            });
            waitFor(() -> texts(main, "links-group-name").equals(List.of("FromB")));
            Thread.sleep(300);
            assertEquals(List.of("Fuente"), texts(pinnedBack, "links-group-name"));
            assertEquals(List.of("Existe"), texts(pinnedOut, "links-outgoing-name"));

            onFx(() -> {
                click(pinnedBack.lookup(".links-group-title"));
                return null;
            });
            assertEquals(List.of("backlinks@1 shows Target.md", "outgoing-links@2 shows Target.md", "open:Fuente.md"),
                    events);
        } finally {
            links.shutdown();
        }
    }

    /**
     * A pinned copy follows its note when it is renamed, and says so; one whose note is not there
     * shows nothing and tells it is missing; one out of sight works nothing out.
     */
    @Test
    void aPinnedCopyFollowsARenameTellsAMissingNoteAndWorksOnlyInSight(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "Nuevo.md", "# Nuevo\n");
        write(vault, "Fuente.md", "[[Nuevo]]\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> asked = new ArrayList<>();
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance()) {
            @Override
            public List<LinkGroup> backlinkGroups(Note target) {
                asked.add(target.getId());
                return super.backlinkGroups(target);
            }

            @Override
            public List<BacklinkService.OutgoingLink> outgoingLinks(Note source) {
                asked.add("outgoing of " + source.getId());
                return super.outgoingLinks(source);
            }
        };
        List<String> told = new ArrayList<>();
        boolean[] inSight = {false};
        BacklinksSupport support = new BacklinksSupport();
        VBox pinned = new VBox();
        List<EventBus.Subscription> subscriptions = new ArrayList<>();
        try {
            onFx(() -> {
                support.wire(links, notes, k -> k, n -> { }, () -> false);
                support.setOnPinnedShown((copy, note) -> told.add(copy + " shows " + (note != null ? note.getId() : null)));
                support.setOnPinnedRebound((copy, noteId) -> told.add(copy + " now " + noteId));
                subscriptions.addAll(support.subscribe(EventBus.getInstance()));
                pinned.getChildren().setAll(
                        support.pinnedPanel("backlinks@1", RightPanelSupport.BACKLINKS, "Viejo.md", () -> inSight[0]));
                support.refreshPinned("backlinks@1"); // out of sight: nothing
                inSight[0] = true;
                support.refreshPinned("backlinks@1"); // Viejo.md is not there
                return null;
            });
            assertEquals(List.of(), asked);
            assertEquals(List.of("backlinks@1 shows null"), told);

            // Viejo.md was renamed to Nuevo.md and saved.
            EventBus.getInstance().publish(
                    new NoteEvents.NoteSavedEvent(notes.getNoteById("Nuevo.md").orElseThrow(), "Viejo.md"));
            waitFor(() -> texts(pinned, "links-group-name").equals(List.of("Fuente")));
            assertEquals(List.of("backlinks@1 shows null", "backlinks@1 now Nuevo.md", "backlinks@1 shows Nuevo.md"),
                    told);
            assertEquals("Nuevo.md", support.pinnedNoteId("backlinks@1"));
            assertEquals(List.of("Nuevo.md"), asked, "a backlinks copy works out its backlinks, not where its note links");

            onFx(() -> {
                support.forget("backlinks@1");
                return null;
            });
            int before = asked.size();
            EventBus.getInstance().publish(new NoteEvents.NoteSavedEvent(notes.getNoteById("Fuente.md").orElseThrow()));
            Thread.sleep(300);
            assertEquals(before, asked.size(), "a copy forgotten works nothing out");
            assertEquals(null, support.pinnedNoteId("backlinks@1"));
        } finally {
            subscriptions.forEach(EventBus.Subscription::cancel);
            links.shutdown();
        }
    }

    /**
     * The notes loaded again (as when the vault has just been read, its links indexed): the
     * copies in sight work their links out again, as the start may have asked before the index
     * was there.
     */
    @Test
    void thePinnedCopiesInSightAreWorkedOutAgainWhenTheNotesAreLoadedAgain(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        write(vault, "Target.md", "# T\n");
        write(vault, "Fuente.md", "[[Target]]\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> asked = new ArrayList<>();
        BacklinkService links = new BacklinkService(notes, EventBus.getInstance()) {
            @Override
            public List<LinkGroup> backlinkGroups(Note target) {
                asked.add(target.getId());
                return super.backlinkGroups(target);
            }
        };
        BacklinksSupport support = new BacklinksSupport();
        List<EventBus.Subscription> subscriptions = new ArrayList<>();
        try {
            onFx(() -> {
                support.wire(links, notes, k -> k, n -> { }, () -> false);
                subscriptions.addAll(support.subscribe(EventBus.getInstance()));
                support.pinnedPanel("backlinks@1", RightPanelSupport.BACKLINKS, "Target.md", () -> true);
                support.pinnedPanel("backlinks@2", RightPanelSupport.BACKLINKS, "Fuente.md", () -> false);
                return null;
            });
            EventBus.getInstance().publish(new NoteEvents.NotesRefreshRequestedEvent());
            waitFor(() -> !asked.isEmpty());
            Thread.sleep(300);
            assertEquals(List.of("Target.md"), asked, "only the copy in sight");
        } finally {
            subscriptions.forEach(EventBus.Subscription::cancel);
            links.shutdown();
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<String> texts(VBox content, String styleClass) {
        try {
            return onFx(() -> {
                List<String> out = new ArrayList<>();
                for (Node node : content.lookupAll("." + styleClass)) {
                    if (node instanceof Labeled labeled) {
                        out.add(labeled.getText());
                    } else if (node instanceof Text text) {
                        out.add(text.getText());
                    }
                }
                return out;
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not reached in time");
            }
            Thread.sleep(50);
        }
    }

    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
