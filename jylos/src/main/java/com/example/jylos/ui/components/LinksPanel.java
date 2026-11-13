package com.example.jylos.ui.components;

import java.util.List;
import java.util.function.Function;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.BacklinkService.LinkGroup;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.ui.UiNodes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The links of the open note, in the right panel: the notes that link to it with the line of
 * each link, the notes that mention it without linking (loaded only when that section is
 * opened, with a button that turns each mention into a link), and the notes it links to.
 * Only draws what it is given; fetching and saving are the owner's job ({@link Actions}).
 */
public final class LinksPanel extends VBox {

    /** What the panel asks its owner to do. */
    public interface Actions {
        void openNote(Note note);

        void openOutgoing(OutgoingLink link);

        void linkMention(Note source, Occurrence mention);

        /** The unlinked-mentions section was opened for the first time for this note. */
        void unlinkedRequested();
    }

    /** A piece of a context line, highlighted when it is a link or a mention. */
    record Segment(String text, boolean match) {
    }

    private static final int CONTEXT_BEFORE = 50;
    private static final int CONTEXT_AFTER = 80;

    private final Function<String, String> i18n;
    private final Actions actions;
    private final Section linked;
    private final Section unlinked;
    private final Section outgoing;
    private boolean unlinkedRequested;

    public LinksPanel(Function<String, String> i18n, Actions actions) {
        this.i18n = i18n;
        this.actions = actions;
        getStyleClass().add("links-panel");
        setSpacing(2);
        linked = new Section(i18n.apply("links.linked_mentions"), true);
        unlinked = new Section(i18n.apply("links.unlinked_mentions"), false);
        outgoing = new Section(i18n.apply("links.outgoing"), true);
        getChildren().addAll(linked, unlinked, outgoing);
        unlinked.onExpand = this::requestUnlinked;
        reset();
    }

    /** Empties the three sections and folds the unlinked one, for a note that was just opened. */
    public void reset() {
        linked.clear();
        outgoing.clear();
        unlinked.clear();
        unlinked.setExpanded(false);
        unlinkedRequested = false;
    }

    public void showLinked(List<LinkGroup> groups) {
        fillGroups(linked, groups, false, i18n.apply("info.no_backlinks"));
    }

    public void showUnlinked(List<LinkGroup> groups) {
        fillGroups(unlinked, groups, true, i18n.apply("links.no_unlinked"));
    }

    public void showUnlinkedLoading() {
        unlinked.clear();
        Label loading = new Label(i18n.apply("links.loading"));
        loading.getStyleClass().addAll("info-value-small", "links-empty");
        unlinked.body.getChildren().add(loading);
    }

    public void showOutgoing(List<OutgoingLink> links) {
        outgoing.clear();
        if (links == null || links.isEmpty()) {
            outgoing.body.getChildren().add(empty(i18n.apply("links.no_outgoing")));
            return;
        }
        outgoing.setFlair(String.valueOf(links.size()));
        for (OutgoingLink link : links) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("links-outgoing-row");
            FontIcon icon = new FontIcon(link.resolved() ? "fth-link" : "fth-file-plus");
            icon.getStyleClass().add("links-outgoing-icon");
            Label name = new Label(link.name());
            name.getStyleClass().add("links-outgoing-name");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            row.getChildren().addAll(icon, name);
            if (!link.resolved()) {
                row.getStyleClass().add("links-outgoing-missing");
                Tooltip.install(row, new Tooltip(i18n.apply("links.not_created")));
            }
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    actions.openOutgoing(link);
                }
            });
            outgoing.body.getChildren().add(row);
        }
    }

    public boolean isUnlinkedExpanded() {
        return unlinked.expanded;
    }

    private void requestUnlinked() {
        if (!unlinkedRequested) {
            unlinkedRequested = true;
            showUnlinkedLoading();
            actions.unlinkedRequested();
        }
    }

    private void fillGroups(Section section, List<LinkGroup> groups, boolean withLinkButton, String emptyText) {
        section.clear();
        if (groups == null || groups.isEmpty()) {
            section.body.getChildren().add(empty(emptyText));
            if (withLinkButton) {
                section.setFlair("0");
            }
            return;
        }
        int total = 0;
        for (LinkGroup group : groups) {
            total += group.occurrences().size();
            section.body.getChildren().add(groupView(group, withLinkButton));
        }
        section.setFlair(String.valueOf(total));
    }

    private VBox groupView(LinkGroup group, boolean withLinkButton) {
        VBox box = new VBox(1);
        box.getStyleClass().add("links-group");

        HBox title = new HBox(6);
        title.setAlignment(Pos.CENTER_LEFT);
        title.getStyleClass().add("links-group-title");
        String name = group.note().getTitle() != null && !group.note().getTitle().isBlank()
                ? group.note().getTitle() : i18n.apply("app.untitled");
        Label label = new Label(name);
        label.getStyleClass().add("links-group-name");
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        Label count = new Label(String.valueOf(group.occurrences().size()));
        count.getStyleClass().add("links-flair");
        title.getChildren().addAll(label, count);
        title.setOnMouseClicked(e -> openOnPrimary(e.getButton(), group.note()));
        box.getChildren().add(title);

        // Links share a row when they are on the same line; each mention has its own row
        // because each one has its own button.
        List<List<Occurrence>> rows = new java.util.ArrayList<>();
        for (Occurrence occurrence : group.occurrences()) {
            List<Occurrence> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (!withLinkButton && last != null && last.get(0).line() == occurrence.line()) {
                last.add(occurrence);
            } else {
                rows.add(new java.util.ArrayList<>(List.of(occurrence)));
            }
        }
        for (List<Occurrence> rowOccurrences : rows) {
            Occurrence occurrence = rowOccurrences.get(0);
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("links-match-row");
            TextFlow flow = new TextFlow();
            for (Segment segment : segments(occurrence.lineText(), rowOccurrences)) {
                Text text = new Text(segment.text());
                text.getStyleClass().add(segment.match() ? "links-match-highlight" : "links-match-text");
                flow.getChildren().add(text);
            }
            flow.getStyleClass().add("links-match-flow");
            HBox.setHgrow(flow, Priority.ALWAYS);
            row.getChildren().add(flow);
            row.setOnMouseClicked(e -> openOnPrimary(e.getButton(), group.note()));
            if (withLinkButton) {
                Button link = new Button(i18n.apply("links.link_button"));
                link.getStyleClass().add("links-link-btn");
                // The text wraps; the button never shrinks to an ellipsis.
                link.setMinWidth(Region.USE_PREF_SIZE);
                link.setOnAction(e -> {
                    e.consume();
                    actions.linkMention(group.note(), occurrence);
                });
                row.getChildren().add(link);
            }
            box.getChildren().add(row);
        }
        return box;
    }

    private void openOnPrimary(MouseButton button, Note note) {
        if (button == MouseButton.PRIMARY) {
            actions.openNote(note);
        }
    }

    private Label empty(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("info-value-small", "links-empty");
        return label;
    }

    /**
     * The part of {@code line} to show for {@code matches} (all on that line, in order): a
     * little before the first and after the last, with "…" where the line goes on, so a long
     * line stays a short row. Matches are the highlighted segments.
     */
    static List<Segment> segments(String line, List<Occurrence> matches) {
        int first = matches.get(0).start();
        int last = matches.get(matches.size() - 1).end();
        int from = Math.max(0, first - CONTEXT_BEFORE);
        int to = Math.min(line.length(), last + CONTEXT_AFTER);
        List<Segment> out = new java.util.ArrayList<>();
        String before = line.substring(from, first);
        out.add(new Segment(from > 0 ? "…" + before.stripLeading() : before, false));
        int position = first;
        for (Occurrence match : matches) {
            if (match.start() > position) {
                out.add(new Segment(line.substring(position, match.start()), false));
            }
            out.add(new Segment(line.substring(match.start(), match.end()), true));
            position = match.end();
        }
        String after = line.substring(last, to);
        out.add(new Segment(to < line.length() ? after.stripTrailing() + "…" : after, false));
        return out;
    }

    /** A collapsible block of the panel: arrow, title, count, and a body that folds away. */
    private static final class Section extends VBox {
        private final VBox body = new VBox(2);
        private final FontIcon arrow = new FontIcon();
        private final Label flair = new Label();
        private boolean expanded;
        private Runnable onExpand;

        Section(String title, boolean expanded) {
            getStyleClass().add("links-section");
            HBox header = new HBox(4);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("links-section-header");
            arrow.getStyleClass().add("links-section-arrow");
            Label name = new Label(title);
            name.getStyleClass().add("links-section-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            flair.getStyleClass().add("links-flair");
            header.getChildren().addAll(arrow, name, spacer, flair);
            header.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    setExpanded(!this.expanded);
                }
            });
            body.getStyleClass().add("links-section-body");
            body.setPadding(new Insets(0, 0, 4, 12));
            getChildren().addAll(header, body);
            UiNodes.show(flair, false);
            setExpanded(expanded);
        }

        void setExpanded(boolean value) {
            boolean opened = value && !this.expanded;
            this.expanded = value;
            arrow.setIconLiteral(value ? "fth-chevron-down" : "fth-chevron-right");
            UiNodes.show(body, value);
            if (opened && onExpand != null) {
                onExpand.run();
            }
        }

        void setFlair(String text) {
            flair.setText(text);
            UiNodes.show(flair, true);
        }

        void clear() {
            body.getChildren().clear();
            UiNodes.show(flair, false);
        }
    }
}
