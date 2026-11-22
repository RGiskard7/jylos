package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.BacklinkService.LinkGroup;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.LinkOccurrences;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/** The links panel draws the three sections, folds them, and reports clicks to its owner. */
class LinksPanelTest {

    @Test
    void segmentsKeepShortLinesWholeAndCutLongOnesAroundTheMatches() {
        String line = "Aquí va [[Target]] y [[Target|alias]] hoy";
        List<Occurrence> both = LinkOccurrences.links(line);

        assertEquals(List.of("Aquí va |", "[[Target]]*", " y |", "[[Target|alias]]*", " hoy|"),
                LinksPanel.segments(line, both).stream()
                        .map(seg -> seg.text() + (seg.match() ? "*" : "|")).toList());

        String longLine = "a".repeat(200) + "[[Target]]" + "b".repeat(200);
        List<LinksPanel.Segment> cut = LinksPanel.segments(longLine, LinkOccurrences.links(longLine));
        assertEquals(3, cut.size());
        assertTrue(cut.get(0).text().startsWith("…") && cut.get(0).text().length() <= 51, cut.get(0).text());
        assertEquals("[[Target]]", cut.get(1).text());
        assertTrue(cut.get(1).match());
        assertTrue(cut.get(2).text().endsWith("…") && cut.get(2).text().length() <= 81, cut.get(2).text());
    }

    @Test
    void severalLinksOnOneLineShareARowButEachMentionHasItsOwn() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<List<String>> rows = onFx(() -> {
            LinksPanel panel = new LinksPanel(k -> k, new Recorder());
            Note source = note("a.md", "Fuente");
            String text = "Aquí [[Target]] y [[Target|alias]]\nOtra [[Target#S]]";
            panel.showLinked(List.of(new LinkGroup(source, LinkOccurrences.links(text))));
            List<String> linked = new ArrayList<>();
            for (Node row : panel.lookupAll(".links-match-row")) {
                linked.add(String.join("+", texts((Parent) row, "links-match-highlight")));
            }
            String mentions = "Target y Target";
            panel.showUnlinked(List.of(new LinkGroup(source, LinkOccurrences.mentions(mentions, List.of("Target")))));
            return List.of(linked, texts(panel, "links-link-btn").isEmpty() ? List.of() : List.of("buttons"),
                    List.of(String.valueOf(panel.lookupAll(".links-link-btn").size())));
        });

        assertEquals(List.of("[[Target]]+[[Target|alias]]", "[[Target#S]]"), rows.get(0),
                "two rows for three links: the first line holds two");
        assertEquals(List.of("2"), rows.get(2), "one Link button per unlinked mention");
    }

    @Test
    void drawsGroupsWithContextAndReportsClicks() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Recorder rec = new Recorder();

        List<String> seen = onFx(() -> {
            LinksPanel panel = new LinksPanel(k -> k, rec);
            Note source = note("a.md", "Fuente");
            Occurrence link = LinkOccurrences.links("Aquí [[Target]] y más").get(0);
            panel.showLinked(List.of(new LinkGroup(source, List.of(link))));
            panel.showOutgoing(List.of(new OutgoingLink("Existe", note("e.md", "Existe"), 2),
                    new OutgoingLink("Falta", null, 1)));
            // The backlinks and the outgoing links are two views, each in its own tab.
            Parent both = new VBox(panel, panel.outgoingView());

            List<String> out = new ArrayList<>();
            out.add(String.join("|", texts(panel, "links-section-title")) + " / "
                    + String.join("|", texts(panel.outgoingView(), "links-section-title")));
            out.add(String.join("|", texts(both, "links-flair")));          // 1 link, 1 group, 2 destinations
            out.add(String.join("|", texts(both, "links-group-name")));
            out.add(String.join("|", texts(both, "links-match-highlight")));
            out.add(String.join("|", texts(both, "links-outgoing-name")));

            click(both.lookup(".links-group-title"));
            click(both.lookup(".links-match-row"));
            for (Node row : both.lookupAll(".links-outgoing-row")) {
                click(row);
            }
            return out;
        });

        assertEquals("links.linked_mentions|links.unlinked_mentions / links.links", seen.get(0));
        assertEquals("1|1|2", seen.get(1), "linked mentions flair, group flair, outgoing flair");
        assertEquals("Fuente", seen.get(2));
        assertEquals("[[Target]]", seen.get(3));
        assertEquals("Existe|Falta", seen.get(4));
        assertEquals(List.of("open:a.md", "open:a.md", "out:Existe:true", "out:Falta:false"), rec.events);
    }

    @Test
    void theUnlinkedSectionLoadsOnlyWhenOpenedAndOffersToLinkEachMention() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Recorder rec = new Recorder();

        List<Object> seen = onFx(() -> {
            LinksPanel panel = new LinksPanel(k -> k, rec);
            List<Object> out = new ArrayList<>();
            out.add(panel.isUnlinkedExpanded());

            click(headerOf(panel, "links.unlinked_mentions"));               // open: asks once
            out.add(panel.isUnlinkedExpanded());
            click(headerOf(panel, "links.unlinked_mentions"));               // close
            click(headerOf(panel, "links.unlinked_mentions"));               // open again: no new request

            Note source = note("u.md", "Unlinked");
            Occurrence mention = LinkOccurrences.mentions("Habla de Target aquí", List.of("Target")).get(0);
            panel.showUnlinked(List.of(new LinkGroup(source, List.of(mention))));
            out.add(String.join("|", texts(panel, "links-flair")));
            Button link = (Button) panel.lookup(".links-link-btn");
            out.add(link.getText());
            link.fire();
            return out;
        });

        assertEquals(false, seen.get(0));
        assertEquals(true, seen.get(1));
        assertEquals("1|1", seen.get(2), "section flair and group flair");
        assertEquals("links.link_button", seen.get(3));
        assertEquals(List.of("unlinked", "link:u.md:Target"), rec.events);
    }

    @Test
    void resetEmptiesEverythingAndFoldsTheUnlinkedSection() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Recorder rec = new Recorder();

        List<Object> seen = onFx(() -> {
            LinksPanel panel = new LinksPanel(k -> k, rec);
            click(headerOf(panel, "links.unlinked_mentions"));
            panel.showLinked(List.of(new LinkGroup(note("a.md", "A"),
                    List.of(LinkOccurrences.links("[[X]]").get(0)))));
            panel.reset();

            List<Object> out = new ArrayList<>();
            out.add(panel.isUnlinkedExpanded());
            out.add(panel.lookup(".links-group-title") == null);
            out.add(texts(panel, "links-flair").isEmpty());
            click(headerOf(panel, "links.unlinked_mentions"));               // a new note: asks again
            return out;
        });

        assertEquals(List.of(false, true, true), seen);
        assertEquals(List.of("unlinked", "unlinked"), rec.events);
    }

    /** In a narrow panel a long line wraps; the Link button must keep its full text, not shrink to "…". */
    @Test
    void theLinkButtonKeepsItsWidthWhenTheLineIsLong() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Double> shortfalls = onFx(() -> {
            LinksPanel panel = new LinksPanel(k -> k, new Recorder());
            String text = "Y de Alias Target también, y de target en minúsculas y mucho más texto hasta que no quepa";
            panel.showUnlinked(List.of(new LinkGroup(note("m.md", "Menciona"),
                    LinkOccurrences.mentions(text, List.of("Target")))));
            Stage stage = new Stage();
            stage.setScene(new Scene(panel, 230, 400));
            stage.show();
            panel.applyCss();
            panel.layout();
            List<Double> out = new ArrayList<>();
            for (Node button : panel.lookupAll(".links-link-btn")) {
                Button b = (Button) button;
                out.add(b.prefWidth(-1) - b.getWidth());
            }
            stage.close();
            return out;
        });

        assertEquals(2, shortfalls.size());
        for (double shortfall : shortfalls) {
            assertTrue(shortfall < 1, "button narrower than its text by " + shortfall);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static final class Recorder implements LinksPanel.Actions {
        final List<String> events = new ArrayList<>();

        @Override public void openNote(Note note) { events.add("open:" + note.getId()); }
        @Override public void openOutgoing(OutgoingLink link) { events.add("out:" + link.name() + ":" + link.resolved()); }
        @Override public void linkMention(Note source, Occurrence mention) {
            events.add("link:" + source.getId() + ":" + mention.matched());
        }
        @Override public void unlinkedRequested() { events.add("unlinked"); }
    }

    private static Note note(String id, String title) {
        Note note = new Note(title, "");
        note.setId(id);
        return note;
    }

    private static Node headerOf(LinksPanel panel, String title) {
        for (Node label : panel.lookupAll(".links-section-title")) {
            if (title.equals(((Labeled) label).getText())) {
                return label.getParent();
            }
        }
        throw new AssertionError("no section " + title);
    }

    private static List<String> texts(Parent panel, String styleClass) {
        List<String> out = new ArrayList<>();
        for (Node node : panel.lookupAll("." + styleClass)) {
            if (node instanceof Labeled labeled) {
                if (labeled.isVisible() && labeled.isManaged()) {
                    out.add(labeled.getText());
                }
            } else if (node instanceof Text text) {
                out.add(text.getText());
            }
        }
        return out;
    }

    private static void click(Node node) {
        MouseEvent event = new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null);
        node.fireEvent(event);
    }
}
