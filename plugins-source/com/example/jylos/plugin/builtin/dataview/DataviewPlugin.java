package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.List;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;

/**
 * Dataview for Jylos: query your notes' metadata from inside a note.
 *
 * <p>Write a fenced <code>```dataview</code> block and it is replaced, in the note
 * preview, by the result of running the query over the vault.</p>
 *
 * <pre>
 * ```dataview
 * TABLE rating AS "Score", file.mtime AS "Updated"
 * FROM #book AND -"archive"
 * WHERE rating &gt;= 4
 * SORT rating DESC
 * LIMIT 10
 * ```
 * </pre>
 *
 * <h2>Supported</h2>
 * <ul>
 *   <li>{@code TABLE} / {@code LIST} / {@code TASK}, with {@code WITHOUT ID}</li>
 *   <li>{@code FROM} over {@code #tags}, {@code "folders"}, {@code [[links]]} and
 *       {@code outgoing([[…]])}, combined with {@code and}/{@code or}/{@code -}</li>
 *   <li>{@code WHERE}, {@code SORT}, {@code GROUP BY}, {@code FLATTEN}, {@code LIMIT}</li>
 *   <li>Metadata from YAML frontmatter, inline {@code key:: value} fields, tags and
 *       the implicit {@code file.*} attributes</li>
 *   <li>Inline expressions written as {@code `= expression`}</li>
 * </ul>
 *
 * <h2>Not supported</h2>
 * <ul>
 *   <li><b>{@code dataviewjs}</b> — arbitrary JavaScript queries would need a scripting
 *       surface the host does not expose; such blocks render an explanatory notice.</li>
 *   <li><b>Live Preview</b> — results appear in the reading-mode preview. Editing a note
 *       shows the query source, because the editor renders through CodeMirror and the
 *       plugin API reaches the preview only.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
public class DataviewPlugin implements Plugin {

    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    private PluginContext context;
    private DataviewIndex index;

    @Override
    public String getId() {
        return "dataview";
    }

    @Override
    public String getName() {
        return "Dataview";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Query note metadata with TABLE/LIST/TASK blocks rendered in the preview.";
    }

    @Override
    public String getAuthor() {
        return "Edu Díaz";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        this.index = new DataviewIndex(context.getNoteService(), context.getFolderService());

        context.registerPreviewEnhancer(new DataviewEnhancer(index));
        subscribeToInvalidationEvents(context);

        context.registerCommand("Dataview: Rebuild index",
                "Re-reads every note so queries pick up external changes",
                () -> {
                    index.clear();
                    context.showInfo("Dataview", "Index cleared",
                            "The next query re-reads the vault.");
                });

        context.registerCommand("Dataview: Query reference",
                "Shows the supported query syntax",
                () -> context.showInfo("Dataview", "Query syntax", REFERENCE));

        context.registerMenuItem("Dataview", "Query reference",
                () -> context.showInfo("Dataview", "Query syntax", REFERENCE));

        context.log("Dataview plugin initialized");
    }

    @Override
    public void shutdown() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (index != null) {
            index.clear();
        }
        if (context != null) {
            context.unregisterPreviewEnhancer();
            context.unregisterAllCommands();
        }
    }

    /**
     * Keeps the index current. Only the affected note is dropped on a save, so editing
     * one note does not force a full vault re-read on the next preview render.
     */
    private void subscribeToInvalidationEvents(PluginContext context) {
        subscriptions.add(context.subscribe(NoteEvents.NoteSavedEvent.class,
                event -> invalidateNote(event.getNote())));
        subscriptions.add(context.subscribe(NoteEvents.NoteCreatedEvent.class,
                event -> invalidateNote(event.getNote())));
        subscriptions.add(context.subscribe(NoteEvents.NoteUpdatedEvent.class,
                event -> invalidateNote(event.getNote())));
        subscriptions.add(context.subscribe(NoteEvents.NoteDeletedEvent.class,
                event -> index.invalidate(event.getNoteId())));
        subscriptions.add(context.subscribe(NoteEvents.NotesRefreshRequestedEvent.class,
                event -> index.clear()));
    }

    private void invalidateNote(com.example.jylos.data.models.Note note) {
        if (note != null) {
            index.invalidate(note.getId());
        } else {
            index.invalidate();
        }
    }

    private static final String REFERENCE = """
            Write a query in a fenced block:

              ```dataview
              TABLE rating AS "Score", file.mtime AS "Updated"
              FROM #book AND -"archive"
              WHERE rating >= 4
              SORT rating DESC
              LIMIT 10
              ```

            QUERY TYPES
              TABLE col, col AS "Name"   LIST [expression]   TASK
              Add WITHOUT ID to drop the implicit file link column.

            FROM
              #tag              pages with that tag (also matches #tag/child)
              "folder"          pages in that folder
              [[Note]]          pages that link to Note
              outgoing([[Note]]) pages Note links to
              Combine with AND / OR, negate with - or NOT.

            CLAUSES
              WHERE expression      SORT expression [ASC|DESC]
              GROUP BY expression [AS name]
              FLATTEN list AS name  LIMIT n

            FIELDS
              Frontmatter keys, inline "key:: value" fields, and:
              file.name  file.path  file.folder  file.link  file.size
              file.ctime file.cday  file.mtime   file.mday
              file.tags  file.outlinks file.inlinks file.tasks
              file.starred file.pinned
              Use "this" for the current note, e.g. this.file.name

            FUNCTIONS
              length contains icontains econtains typeof
              lower upper replace split join truncate startswith endswith
              regexmatch regexreplace number string round floor ceil abs
              min max sum average date dateformat striptime dur
              default choice nonnull link elink sort reverse unique flat
              first last any all none

            INLINE
              Write `= expression` in text to show a computed value,
              for example `= this.file.name`.

            NOT SUPPORTED
              dataviewjs blocks, and rendering inside the editor's Live
              Preview (results show in the reading-mode preview).
            """;
}
