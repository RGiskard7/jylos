package com.example.jylos.ui.controller;

import javafx.application.Platform;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.templates.DailyNotePlanner;
import com.example.jylos.service.templates.TemplateEngine;
import com.example.jylos.service.templates.TemplateLibrary;
import com.example.jylos.ui.components.QuickSwitcher;
import com.example.jylos.util.KanbanModel;
import com.example.jylos.util.UntitledNames;

import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * UI-owned note creation workflows: regular note creation, daily notes and
 * note-from-template flows.
 *
 * <p>The underlying persistence remains in {@link NoteOperations}; this support
 * owns only the shell-level coordination required to open the created note,
 * refresh dependent views and prompt the user when a template must be chosen.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.3.0
 */
final class NoteCreationSupport {

    private static final String EMPTY_CANVAS_JSON = "{\n\t\"nodes\":[],\n\t\"edges\":[]\n}";

    private NoteService noteService;
    private NoteOperations noteOperations;
    private BooleanSupplier fileSystemStorage;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Window> windowSupplier;
    private Supplier<Folder> currentFolderSupplier;
    private Consumer<Note> openNote;
    private Consumer<Note> publishCreatedNote;
    private Consumer<String> requestSelectAfterRefresh;
    private Runnable refreshNotes;
    private Runnable refreshSidebar;
    private Runnable beginTitleRename = () -> {
    };
    private TemplatesSupport templates;
    private Supplier<ZonedDateTime> clock = ZonedDateTime::now;
    private Supplier<EditorController> editor = () -> null;
    private Supplier<Stage> owner = () -> null;
    private Supplier<ResourceBundle> bundle = () -> null;
    private QuickSwitcher templatePicker;

    void wire(NoteService noteService, NoteOperations noteOperations, BooleanSupplier fileSystemStorage,
            Function<String, String> i18n, Consumer<String> status, Supplier<Window> windowSupplier,
            Supplier<Folder> currentFolderSupplier, Consumer<Note> openNote,
            Consumer<Note> publishCreatedNote, Consumer<String> requestSelectAfterRefresh,
            Runnable refreshNotes, Runnable refreshSidebar) {
        this.noteService = noteService;
        this.noteOperations = noteOperations;
        this.fileSystemStorage = fileSystemStorage;
        this.i18n = i18n;
        this.status = status;
        this.windowSupplier = windowSupplier;
        this.currentFolderSupplier = currentFolderSupplier;
        this.openNote = openNote;
        this.publishCreatedNote = publishCreatedNote;
        this.requestSelectAfterRefresh = requestSelectAfterRefresh;
        this.refreshNotes = refreshNotes;
        this.refreshSidebar = refreshSidebar;
    }

    /**
     * The vault's templates and where to insert them: its {@code settings}, the {@code editor}
     * of the open note, and the window and translations of the template picker.
     */
    void useVault(Supplier<VaultSettingsStore> settings, Supplier<EditorController> editor, Supplier<Stage> owner,
            Supplier<ResourceBundle> bundle) {
        this.editor = editor;
        this.owner = owner;
        this.bundle = bundle;
        this.templates = new TemplatesSupport(() -> noteService, settings, this::language, () -> clock.get());
    }

    /** The clock dates are written with (tests). */
    void useClock(Supplier<ZonedDateTime> clock) {
        this.clock = clock;
    }

    /** Answers the commands for daily notes, templates and boards. */
    void register(Map<ActionType, Runnable> handlers) {
        handlers.put(ActionType.DAILY_NOTE, this::openDailyNote);
        handlers.put(ActionType.NEW_FROM_TEMPLATE, this::createFromTemplate);
        handlers.put(ActionType.NEW_KANBAN_BOARD, this::createNewBoard);
        handlers.put(ActionType.INSERT_TEMPLATE, this::insertTemplate);
        handlers.put(ActionType.INSERT_CURRENT_DATE, () -> insertInEditor(templates().date()));
        handlers.put(ActionType.INSERT_CURRENT_TIME, () -> insertInEditor(templates().time()));
    }

    /** The vault's templates. */
    TemplatesSupport templates() {
        if (templates == null) {
            templates = new TemplatesSupport(() -> noteService, () -> null, this::language, () -> clock.get());
        }
        return templates;
    }

    /** Obsidian's "Insert template": picks one of the vault's templates and inserts it in the open note. */
    void insertTemplate() {
        if (openForEditing() == null) {
            updateStatus(getString("templates.status.no_note"));
            return;
        }
        pickTemplate(this::insertTemplate);
    }

    /** Inserts {@code template}, written for the open note, where its cursor is. */
    void insertTemplate(TemplateLibrary.Template template) {
        EditorController open = openForEditing();
        if (open == null) {
            updateStatus(getString("templates.status.no_note"));
            return;
        }
        String text = templates().render(templates().text(template), open.getCurrentNote().getTitle());
        if (open.insertTemplate(text) == EditorTemplateSupport.Outcome.INVALID_PROPERTIES) {
            updateStatus(getString("templates.status.invalid_properties"));
        }
    }

    private void insertInEditor(String text) {
        EditorController open = openForEditing();
        if (open == null || !open.insertText(text)) {
            updateStatus(getString("templates.status.no_note"));
        }
    }

    private EditorController openForEditing() {
        EditorController open = editor.get();
        return open != null && open.getCurrentNote() != null ? open : null;
    }

    /** Lets the user pick one of the vault's templates for {@code then}, or says why there is none. */
    private void pickTemplate(Consumer<TemplateLibrary.Template> then) {
        TemplateLibrary.Listing listing = templates().listing();
        String problem = templates().problem(listing, this::getString);
        if (problem != null) {
            updateStatus(problem);
            return;
        }
        Map<String, TemplateLibrary.Template> byNote = new LinkedHashMap<>();
        List<Note> choices = new ArrayList<>();
        for (TemplateLibrary.Template template : listing.templates()) {
            templates().note(template).ifPresent(note -> {
                byNote.put(note.getId(), template);
                choices.add(note);
            });
        }
        if (templatePicker == null) {
            templatePicker = new QuickSwitcher(owner.get());
            templatePicker.setBundle(bundle.get());
            templatePicker.setPlaceholder(getString("templates.search_placeholder"));
        }
        templatePicker.setOnNoteSelected(note -> {
            TemplateLibrary.Template picked = byNote.get(note.getId());
            if (picked != null) {
                then.accept(picked);
            }
        });
        templatePicker.setNotes(choices);
        templatePicker.show();
    }

    private Locale language() {
        ResourceBundle texts = bundle.get();
        return texts != null ? texts.getLocale() : Locale.getDefault();
    }

    /** What leaves a new untitled note's title ready to rename (the editor focuses it). */
    void setBeginTitleRename(Runnable action) {
        beginTitleRename = action != null ? action : () -> {
        };
    }

    /** A new note, as in Obsidian: "Sin título" (or the first free "Sin título N"), its title ready to rename. */
    void createNewNote() {
        createNewNoteInFolder(newNoteFolder());
    }

    /**
     * Where a new note goes, as the vault says: the folder in view, the vault's root, or a
     * folder chosen ({@code null}, the root, when that one is not there, as Obsidian does).
     */
    private Folder newNoteFolder() {
        VaultSettings.NewNotes where = templates().settings().newNotes();
        return switch (where.location()) {
            case CURRENT -> currentFolder();
            case ROOT -> null;
            case FOLDER -> noteOperations != null ? noteOperations.folderAt(where.folder()).orElse(null) : null;
        };
    }

    /** Creates a regular note in the explicit folder chosen by a contextual action. */
    void createNewNoteInFolder(Folder folder) {
        if (createAndOpenNote(untitledTitle(folder), "", folder, "status.note_created")) {
            Platform.runLater(beginTitleRename);
        }
    }

    /** "Sin título", or the first "Sin título N" that no note in {@code folder} has. */
    private String untitledTitle(Folder folder) {
        return untitledTitle(folder, getString("app.untitled"));
    }

    /** {@code base}, or the first "{@code base} N" that no note in {@code folder} has. */
    private String untitledTitle(Folder folder, String base) {
        if (noteService == null) {
            return base;
        }
        boolean inFolder = folder != null && folder.getId() != null && !folder.getId().isBlank()
                && !"ROOT".equals(folder.getId());
        List<String> taken = new ArrayList<>();
        try {
            if (isFileSystem()) {
                // A note's folder is the path of its id ("Folder/Note.md").
                String dir = inFolder ? folder.getId().replace('\\', '/') : "";
                for (Note note : noteService.getAllNotes()) {
                    String id = note.getId() != null ? note.getId().replace('\\', '/') : "";
                    int slash = id.lastIndexOf('/');
                    if ((slash < 0 ? "" : id.substring(0, slash)).equals(dir)) {
                        taken.add(note.getTitle());
                    }
                }
            } else {
                List<Note> siblings = inFolder ? noteService.getNotesByFolder(folder)
                        : noteService.getAllNotes().stream().filter(n -> n.getParent() == null).toList();
                siblings.forEach(n -> taken.add(n.getTitle()));
            }
        } catch (RuntimeException e) {
            // Without the list, the storage still avoids overwriting: it numbers the file.
        }
        return UntitledNames.next(base, taken);
    }

    /** Creates an empty note named {@code title} (a link to it that did not exist yet) and opens it. */
    void createNoteWithTitle(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        createAndOpenNote(title.strip(), "", newNoteFolder(), "status.note_created");
    }

    void createNewCanvas() {
        createNewCanvasInFolder(currentFolder());
    }

    /** A new Kanban board, as a new note: in the current folder, its title ready to rename. */
    void createNewBoard() {
        createNewBoardInFolder(currentFolder());
    }

    /**
     * Creates a Kanban board in {@code folder} ("Untitled board", or the first free "Untitled
     * board N") with three columns (to do, doing, done) and opens it, its title ready to rename.
     */
    void createNewBoardInFolder(Folder folder) {
        String board = KanbanModel.withDefaults(getString("kanban.col_todo"), getString("kanban.col_doing"),
                getString("kanban.col_done")).toMarkdown();
        if (createAndOpenNote(untitledTitle(folder, getString("kanban.untitled_board")), board, folder,
                "status.board_created")) {
            Platform.runLater(beginTitleRename);
        }
    }

    /** Creates an empty Obsidian-compatible canvas in the supplied destination folder. */
    void createNewCanvasInFolder(Folder folder) {
        if (!isFileSystem()) {
            updateStatus(getString("status.canvas_requires_vault"));
            return;
        }
        createAndOpenNote(getString("canvas.new_filename") + ".canvas", EMPTY_CANVAS_JSON,
                folder, "status.canvas_created");
    }

    /**
     * Opens today's daily note, or makes it, as Obsidian's Daily notes ({@link DailyNotePlanner}):
     * at its path, with its template written for it ({@code {{title}}} its name, {@code {{date}}}
     * and {@code {{time}}} in Obsidian's fixed formats), or says why it cannot.
     */
    void openDailyNote() {
        if (noteService == null) {
            return;
        }
        TemplatesSupport vault = templates();
        VaultSettings settings = vault.settings();
        ZonedDateTime now = vault.now();
        String legacy = settings.daily().template().isBlank()
                ? vault.listing().find("daily").map(TemplateLibrary.Template::path).orElse(null) : null;
        DailyNotePlanner.Plan plan = DailyNotePlanner.plan(settings.daily(), settings.newNotes(), now, vault.language(),
                noteService.folderPaths(), vault.notePaths(), legacy);
        if (plan.problem() != null) {
            updateStatus(MessageFormat.format(getString(plan.problem() == DailyNotePlanner.Problem.FOLDER_NOT_FOUND
                    ? "daily.status.folder_not_found" : "daily.status.template_not_found"), plan.problemPath()));
            return;
        }
        if (plan.existing() != null) {
            // Opening the note clears the status bar (see MainController), so no "loaded" message.
            vault.noteAt(plan.existing()).ifPresent(note ->
                    publishOpen(noteService.getNoteById(note.getId()).orElse(note)));
            return;
        }
        String content = plan.template() == null ? ""
                : TemplateEngine.render(vault.textAt(plan.template()), TemplateEngine.variables(plan.name(), now),
                        VaultSettings.DEFAULT_DATE_FORMAT, VaultSettings.DEFAULT_TIME_FORMAT, now, vault.language());
        Folder folder = plan.folder().isEmpty() ? null : noteOperations.folderMaking(plan.folder());
        createAndOpenNote(plan.fileName(), content, folder, "status.note_created");
    }

    /** "New from template": picks one of the vault's templates and creates a note from it. */
    void createFromTemplate() {
        if (noteService != null) {
            pickTemplate(this::createFromTemplate);
        }
    }

    /**
     * Creates a note named as {@code template}, in the folder in view, with the template
     * written for it, and opens it.
     */
    void createFromTemplate(TemplateLibrary.Template template) {
        String name = template.name().substring(template.name().lastIndexOf('/') + 1);
        String content = templates().render(templates().text(template), name);
        createAndOpenNote(name, content, newNoteFolder(), "status.note_created");
    }

    private boolean createAndOpenNote(String title, String content, Folder folder, String successStatusKey) {
        if (noteOperations == null) {
            updateStatus(getString("status.error_creating_note"));
            return false;
        }
        NoteOperations.NoteCreationResult creation =
                noteOperations.createNewNote(title, content, folder, isFileSystem());
        if (!creation.success() || creation.note() == null) {
            updateStatus(getString("status.error_creating_note"));
            return false;
        }
        handleCreatedNote(creation.note(), successStatusKey);
        return true;
    }

    private void handleCreatedNote(Note note, String successStatusKey) {
        publishOpen(note);
        if (publishCreatedNote != null) {
            publishCreatedNote.accept(note);
        }
        if (requestSelectAfterRefresh != null && note.getId() != null) {
            requestSelectAfterRefresh.accept(note.getId());
        }
        if (refreshNotes != null) {
            refreshNotes.run();
        }
        if (refreshSidebar != null) {
            refreshSidebar.run();
        }
        updateStatus(getString(successStatusKey));
    }

    private boolean isFileSystem() {
        return fileSystemStorage != null && fileSystemStorage.getAsBoolean();
    }

    private Folder currentFolder() {
        return currentFolderSupplier != null ? currentFolderSupplier.get() : null;
    }

    private void publishOpen(Note note) {
        if (openNote != null && note != null) {
            openNote.accept(note);
        }
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
