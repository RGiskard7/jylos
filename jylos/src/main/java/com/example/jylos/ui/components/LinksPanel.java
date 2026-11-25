package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.BacklinkService.LinkGroup;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.ui.UiNodes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The links of the open note, in two views of the right panel, as Obsidian's: this one, the
 * backlinks (the notes that link to it with the line of each link, and the notes that mention
 * it without linking, loaded only when that section is opened, with a button that turns each
 * mention into a link), and {@link #outgoingView()}, the notes it links to. Only draws what it
 * is given; fetching and saving are the owner's job ({@link Actions}).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
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
    private final VBox outgoingView = new VBox(2);
    private boolean unlinkedRequested;

    /** How the notes of the backlinks view are sorted (A to Z by default, as Obsidian's). */
    public enum Sort {
        NAME_ASC("links.sort.name_asc"),
        NAME_DESC("links.sort.name_desc"),
        MODIFIED_NEWEST("links.sort.modified_newest"),
        MODIFIED_OLDEST("links.sort.modified_oldest"),
        CREATED_NEWEST("links.sort.created_newest"),
        CREATED_OLDEST("links.sort.created_oldest");

        private final String key;

        Sort(String key) {
            this.key = key;
        }
    }

    // What was last given, kept to draw it again as the actions change (null: nothing yet).
    private List<LinkGroup> linkedGroups;
    private List<LinkGroup> unlinkedGroups;
    private List<OutgoingLink> outgoingLinks;
    private boolean collapsed;
    private boolean moreContext;
    private Sort sort = Sort.NAME_ASC;
    private Sort outgoingSort = Sort.NAME_ASC;
    private final TextField filter = new TextField();
    private final TextField outgoingFilter = new TextField();
    private final VBox backlinksActions;
    private final VBox outgoingActions;

    public LinksPanel(Function<String, String> i18n, Actions actions) {
        this.i18n = i18n;
        this.actions = actions;
        getStyleClass().add("links-panel");
        setSpacing(2);
        linked = new Section(i18n.apply("links.linked_mentions"), true);
        unlinked = new Section(i18n.apply("links.unlinked_mentions"), false);
        outgoing = new Section(i18n.apply("links.links"), true);
        getChildren().addAll(linked, unlinked);
        outgoingView.getStyleClass().add("links-panel");
        outgoingView.getChildren().add(outgoing);
        unlinked.onExpand = this::requestUnlinked;
        backlinksActions = actionsBar(filter, this::drawBacklinks,
                toggle("fth-chevrons-up", "links.collapse_results", "links-action-collapse", on -> {
                    collapsed = on;
                    drawBacklinks();
                }),
                toggle("fth-align-left", "links.more_context", "links-action-context", on -> {
                    moreContext = on;
                    drawBacklinks();
                }),
                sortButton(this::sortMenu));
        outgoingActions = actionsBar(outgoingFilter, this::drawOutgoing, sortButton(this::outgoingSortMenu));
        reset();
    }

    /** The backlinks view's own actions: collapse results, more context, sort, filter. */
    public VBox backlinksActions() {
        return backlinksActions;
    }

    /** The outgoing links view's own actions: sort, filter. */
    public VBox outgoingActions() {
        return outgoingActions;
    }

    /** The other view: the notes the open note links to. */
    public VBox outgoingView() {
        return outgoingView;
    }

    /** Empties the three sections and folds the unlinked one, for a note that was just opened. */
    public void reset() {
        linkedGroups = null;
        unlinkedGroups = null;
        outgoingLinks = null;
        linked.clear();
        outgoing.clear();
        unlinked.clear();
        unlinked.setExpanded(false);
        unlinkedRequested = false;
    }

    public void showLinked(List<LinkGroup> groups) {
        linkedGroups = groups != null ? groups : List.of();
        drawBacklinks();
    }

    public void showUnlinked(List<LinkGroup> groups) {
        unlinkedGroups = groups != null ? groups : List.of();
        drawBacklinks();
    }

    /** Draws the backlinks given, as the actions say (what is not here yet stays as it is). */
    private void drawBacklinks() {
        if (linkedGroups != null) {
            fillGroups(linked, arrange(linkedGroups), false, i18n.apply("info.no_backlinks"));
        }
        if (unlinkedGroups != null) {
            fillGroups(unlinked, arrange(unlinkedGroups), true, i18n.apply("links.no_unlinked"));
        }
    }

    /** The groups the filter keeps (by the note's name or its lines), in the order chosen. */
    private List<LinkGroup> arrange(List<LinkGroup> groups) {
        String wanted = filter.getText() != null ? filter.getText().strip().toLowerCase(Locale.ROOT) : "";
        return groups.stream()
                .filter(group -> wanted.isEmpty() || nameOf(group).toLowerCase(Locale.ROOT).contains(wanted)
                        || group.occurrences().stream()
                                .anyMatch(o -> o.lineText().toLowerCase(Locale.ROOT).contains(wanted)))
                .sorted(order(sort))
                .toList();
    }

    private Comparator<LinkGroup> order(Sort by) {
        Comparator<String> newestFirst = Comparator.nullsLast(Comparator.<String>reverseOrder());
        Comparator<String> oldestFirst = Comparator.nullsLast(Comparator.<String>naturalOrder());
        return switch (by) {
            case NAME_ASC -> Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER);
            case NAME_DESC -> Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER.reversed());
            case MODIFIED_NEWEST -> Comparator.comparing(g -> g.note().getModifiedDate(), newestFirst);
            case MODIFIED_OLDEST -> Comparator.comparing(g -> g.note().getModifiedDate(), oldestFirst);
            case CREATED_NEWEST -> Comparator.comparing(g -> g.note().getCreatedDate(), newestFirst);
            case CREATED_OLDEST -> Comparator.comparing(g -> g.note().getCreatedDate(), oldestFirst);
        };
    }

    private String nameOf(LinkGroup group) {
        String title = group.note().getTitle();
        return title != null && !title.isBlank() ? title : i18n.apply("app.untitled");
    }

    /** The backlinks' sort menu: by name, or by when the notes were modified or created. */
    ContextMenu sortMenu() {
        return sortMenu(List.of(Sort.values()), sort, chosen -> {
            sort = chosen;
            drawBacklinks();
        });
    }

    /** The outgoing links' sort menu: by name. */
    ContextMenu outgoingSortMenu() {
        return sortMenu(List.of(Sort.NAME_ASC, Sort.NAME_DESC), outgoingSort, chosen -> {
            outgoingSort = chosen;
            drawOutgoing();
        });
    }

    private ContextMenu sortMenu(List<Sort> sorts, Sort current, Consumer<Sort> choose) {
        ContextMenu menu = new ContextMenu();
        for (Sort option : sorts) {
            CheckMenuItem item = new CheckMenuItem(i18n.apply(option.key));
            item.setSelected(option == current);
            item.setOnAction(e -> choose.accept(option));
            menu.getItems().add(item);
        }
        return menu;
    }

    /** A view's actions: its buttons in a row, and under them its filter, shown by the last button. */
    private VBox actionsBar(TextField field, Runnable redraw, ButtonBase... buttons) {
        HBox row = new HBox(2);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("links-actions");
        row.getChildren().addAll(buttons);
        field.getStyleClass().addAll("search-field", "small-search", "links-filter");
        field.setPromptText(i18n.apply("links.filter_prompt"));
        field.textProperty().addListener((o, was, now) -> redraw.run());
        UiNodes.show(field, false);
        row.getChildren().add(toggle("fth-search", "links.filter", "links-action-filter", on -> {
            UiNodes.show(field, on);
            if (on) {
                field.requestFocus();
            } else {
                field.clear(); // hiding the filter shows everything again
            }
        }));
        VBox bar = new VBox(4, row, field);
        bar.getStyleClass().add("links-actions-bar");
        bar.setPadding(new Insets(6, 12, 2, 12));
        return bar;
    }

    private ToggleButton toggle(String icon, String tooltip, String styleClass, Consumer<Boolean> onChange) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().addAll("icon-btn", styleClass);
        button.setTooltip(new Tooltip(i18n.apply(tooltip)));
        button.setFocusTraversable(false);
        button.selectedProperty().addListener((o, was, now) -> onChange.accept(now));
        return button;
    }

    private Button sortButton(Supplier<ContextMenu> menu) {
        Button button = new Button();
        button.setGraphic(new FontIcon("fth-filter")); // the Explorer's sort button
        button.getStyleClass().addAll("icon-btn", "links-action-sort");
        button.setTooltip(new Tooltip(i18n.apply("links.sort")));
        button.setFocusTraversable(false);
        button.setOnAction(e -> menu.get().show(button, Side.BOTTOM, 0, 0));
        return button;
    }

    public void showUnlinkedLoading() {
        unlinked.clear();
        Label loading = new Label(i18n.apply("links.loading"));
        loading.getStyleClass().addAll("info-value-small", "links-empty");
        unlinked.body.getChildren().add(loading);
    }

    public void showOutgoing(List<OutgoingLink> links) {
        outgoingLinks = links != null ? links : List.of();
        drawOutgoing();
    }

    private void drawOutgoing() {
        if (outgoingLinks == null) {
            return;
        }
        String wanted = outgoingFilter.getText() != null
                ? outgoingFilter.getText().strip().toLowerCase(Locale.ROOT) : "";
        Comparator<OutgoingLink> byName = Comparator.comparing(OutgoingLink::name, String.CASE_INSENSITIVE_ORDER);
        List<OutgoingLink> links = outgoingLinks.stream()
                .filter(link -> wanted.isEmpty() || link.name().toLowerCase(Locale.ROOT).contains(wanted))
                .sorted(outgoingSort == Sort.NAME_DESC ? byName.reversed() : byName)
                .toList();
        outgoing.clear();
        if (links.isEmpty()) {
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
        if (collapsed) {
            return box; // "Collapse results": the notes only
        }

        // Links share a row when they are on the same line; each mention has its own row
        // because each one has its own button.
        List<List<Occurrence>> rows = new ArrayList<>();
        for (Occurrence occurrence : group.occurrences()) {
            List<Occurrence> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (!withLinkButton && last != null && last.get(0).line() == occurrence.line()) {
                last.add(occurrence);
            } else {
                rows.add(new ArrayList<>(List.of(occurrence)));
            }
        }
        for (List<Occurrence> rowOccurrences : rows) {
            Occurrence occurrence = rowOccurrences.get(0);
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("links-match-row");
            TextFlow flow = new TextFlow();
            for (Segment segment : segments(occurrence.lineText(), rowOccurrences, moreContext)) {
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
        return segments(line, matches, false);
    }

    /** As {@link #segments(String, List)}, or the whole line ("Show more context"). */
    static List<Segment> segments(String line, List<Occurrence> matches, boolean wholeLine) {
        int first = matches.get(0).start();
        int last = matches.get(matches.size() - 1).end();
        int from = wholeLine ? 0 : Math.max(0, first - CONTEXT_BEFORE);
        int to = wholeLine ? line.length() : Math.min(line.length(), last + CONTEXT_AFTER);
        List<Segment> out = new ArrayList<>();
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
