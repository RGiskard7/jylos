package com.example.jylos.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and serialises a Kanban board stored as Markdown inside a note, in the
 * spirit of Obsidian's Kanban plugin: a board is a note whose body is a list of
 * columns ({@code ## Heading}) each containing text cards ({@code - card}).
 *
 * <p>Boards are identified by a hidden marker comment on the first line
 * ({@link #MARKER}), so a note is flagged as a board without any schema change and
 * the marker stays invisible in the Markdown preview. Cards are free text and may
 * contain {@code [[wiki-links]]} (the UI makes those open the linked note).</p>
 *
 * <pre>
 * %% jylos-kanban %%
 * ## To do
 * - Write the summary
 * - [[Reference notes]]
 * ## Doing
 * - Review
 * ## Done
 * </pre>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class KanbanModel {

    /** Hidden first-line marker identifying a note as a Kanban board. */
    public static final String MARKER = "%% jylos-kanban %%";

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*\\S)\\s*$");
    private static final Pattern CARD = Pattern.compile("^\\s*[-*]\\s+(.*\\S)\\s*$");

    /** Optional column annotations carried in the heading line. */
    private static final Pattern WIP_ANNOTATION = Pattern.compile("\\s*\\[wip=(\\d+)\\]");
    private static final Pattern COLOR_ANNOTATION = Pattern.compile("\\s*\\[color=(#[0-9a-fA-F]{6})\\]");

    /**
     * A single board column with its ordered text cards plus optional metadata:
     * a WIP limit ({@code [wip=N]}, 0 = none) and a color ({@code [color=#rrggbb]},
     * null = none). Both round-trip through the heading line.
     */
    public static final class Column {
        private String title;
        private int wipLimit;
        private String color;
        private final List<String> cards = new ArrayList<>();

        public Column(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        /** WIP limit; 0 means "no limit". */
        public int getWipLimit() {
            return wipLimit;
        }

        public void setWipLimit(int wipLimit) {
            this.wipLimit = Math.max(0, wipLimit);
        }

        /** Column color as {@code #rrggbb}, or null for the theme default. */
        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = (color != null && color.matches("#[0-9a-fA-F]{6}")) ? color : null;
        }

        public List<String> getCards() {
            return cards;
        }
    }

    private final List<Column> columns = new ArrayList<>();

    public List<Column> getColumns() {
        return columns;
    }

    public Column addColumn(String title) {
        Column c = new Column(title);
        columns.add(c);
        return c;
    }

    /** Returns true if the given note content is a Kanban board. */
    public static boolean isBoard(String content) {
        return content != null && content.stripLeading().startsWith(MARKER);
    }

    /** Parses board Markdown into columns/cards. Lines before the first heading are ignored. */
    public static KanbanModel parse(String content) {
        KanbanModel model = new KanbanModel();
        if (content == null) {
            return model;
        }
        Column current = null;
        for (String raw : content.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.equals(MARKER)) {
                continue;
            }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                current = parseColumnHeading(h.group(1).strip());
                model.columns.add(current);
                continue;
            }
            Matcher c = CARD.matcher(line);
            if (c.matches() && current != null) {
                current.cards.add(c.group(1).strip());
            }
        }
        return model;
    }

    /** Extracts {@code [wip=N]} / {@code [color=#rrggbb]} annotations from a heading. */
    private static Column parseColumnHeading(String heading) {
        String title = heading;
        int wip = 0;
        String color = null;

        Matcher w = WIP_ANNOTATION.matcher(title);
        if (w.find()) {
            try {
                wip = Integer.parseInt(w.group(1));
            } catch (NumberFormatException ignored) {
                // malformed limit — treat as none
            }
            title = w.replaceAll("");
        }
        Matcher c = COLOR_ANNOTATION.matcher(title);
        if (c.find()) {
            color = c.group(1);
            title = c.replaceAll("");
        }

        Column column = new Column(title.strip());
        column.setWipLimit(wip);
        column.setColor(color);
        return column;
    }

    /** Serialises the board back to Markdown, including the hidden marker. */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKER).append("\n\n");
        for (Column col : columns) {
            sb.append("## ").append(col.title == null ? "" : col.title);
            if (col.wipLimit > 0) {
                sb.append(" [wip=").append(col.wipLimit).append(']');
            }
            if (col.color != null) {
                sb.append(" [color=").append(col.color).append(']');
            }
            sb.append('\n');
            for (String card : col.cards) {
                sb.append("- ").append(card).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing() + "\n";
    }

    /** Builds a fresh board with the default workflow columns. */
    public static KanbanModel withDefaults(String todo, String doing, String done) {
        KanbanModel m = new KanbanModel();
        m.addColumn(todo);
        m.addColumn(doing);
        m.addColumn(done);
        return m;
    }
}
