package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.NoteService.SortOption;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;
import com.example.jylos.util.AttachmentType;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * One tree with the vault's folders and notes, as in Obsidian's file explorer.
 *
 * <p>Folders are read when they are first expanded, not up front: a large vault (or a cold
 * iCloud folder) only costs what the user actually opens. While a folder is being read it
 * shows a single "Loading…" row. Reads run on the {@code loader} executor and their results
 * are applied on the FX thread; a result that arrives after a newer read of the same folder
 * was started is discarded, so a slow first read cannot overwrite a refresh.</p>
 *
 * <p>Inside a folder, subfolders come first and then notes, in the order given by
 * {@link ExplorerOrdering}. A click on a note opens it in the current tab; Cmd/Ctrl+click
 * or a middle click opens it in a new one. A click on a folder row expands or collapses it.
 * Enter does the same for the selected row (Cmd/Ctrl+Enter opens a note in a new tab).</p>
 */
public final class ExplorerTree {

    private static final Logger logger = LoggerConfig.getLogger(ExplorerTree.class);

    /** Style class of the row showing the note open in the editor. */
    public static final String ACTIVE_NOTE_STYLE = "explorer-active-note";
    private static final String LOADING_STYLE = "explorer-loading-row";
    private static final String FOLDER_STYLE = "explorer-folder-row";
    private static final String NOTE_STYLE = "explorer-note-row";

    /** Where the tree reads a folder's contents from. Called off the FX thread. */
    public interface Source {
        List<Folder> subfolders(Folder folder);

        List<Note> notes(Folder folder);

        /** Reads through the app's services (both storage backends accept the vault root, id "ROOT"). */
        static Source of(FolderService folders, NoteService notes) {
            return new Source() {
                @Override
                public List<Folder> subfolders(Folder folder) {
                    return folders.getSubfolders(folder);
                }

                @Override
                public List<Note> notes(Folder folder) {
                    return folder == null || folder.getId() == null ? List.of() : notes.getNotesByFolder(folder);
                }
            };
        }
    }

    private final Source source;
    private final Executor loader;
    private final Function<String, String> i18n;
    private final TreeView<Component> view = new TreeView<>();

    private SortOption noteSort = SortOption.TITLE_ASC;
    private BiConsumer<Note, OpenTarget> onOpenNote = (note, target) -> {
    };
    private Function<Note, ContextMenu> noteMenu = note -> null;
    private Function<Folder, ContextMenu> folderMenu = folder -> null;
    private Consumer<TreeCell<Component>> cellDecorator = cell -> {
    };
    private String activeNoteId;
    /** The tree of the whole vault, read lazily; kept while a filter shows another root. */
    private FolderItem lazyRoot;
    private String filterQuery = "";
    private long filterGeneration;
    /** Whole-vault listing for the filter and reveal; dropped on refresh. */
    private List<Entry> index;
    private long indexGeneration;

    /**
     * @param source where folder contents are read from
     * @param loader runs the reads (off the FX thread in the app)
     * @param i18n   resolves message keys ({@code explorer.loading})
     */
    public ExplorerTree(Source source, Executor loader, Function<String, String> i18n) {
        this.source = source;
        this.loader = loader;
        this.i18n = i18n;
        view.setShowRoot(false);
        view.getStyleClass().add("explorer-tree");
        view.setCellFactory(tv -> new ExplorerCell());
        view.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey);
    }

    public TreeView<Component> getView() {
        return view;
    }

    /** Shows the contents of {@code root} (the vault root itself is not a row) and reads it. */
    public void setRoot(Folder root) {
        FolderItem item = new FolderItem(root);
        lazyRoot = item;
        index = null;
        indexGeneration++;
        filterQuery = "";
        filterGeneration++;
        view.setRoot(item);
        item.setExpanded(true);
    }

    /** Changes how notes are ordered and re-reads every folder already read. */
    public void setNoteSort(SortOption sort) {
        this.noteSort = sort != null ? sort : SortOption.TITLE_ASC;
        refresh();
    }

    public SortOption getNoteSort() {
        return noteSort;
    }

    /** What opening a note does; the target says whether the user asked for a new tab. */
    public void setOnOpenNote(BiConsumer<Note, OpenTarget> action) {
        this.onOpenNote = action != null ? action : (note, target) -> {
        };
    }

    /** The context menu of a note row; built again whenever the row shows a note. */
    public void setNoteMenu(Function<Note, ContextMenu> menu) {
        this.noteMenu = menu != null ? menu : note -> null;
        view.refresh();
    }

    /** The context menu of a folder row; built again whenever the row shows a folder. */
    public void setFolderMenu(Function<Folder, ContextMenu> menu) {
        this.folderMenu = menu != null ? menu : folder -> null;
        view.refresh();
    }

    /**
     * Runs once on every row the tree creates (rows are reused for different items, so read
     * the row's current item when an event fires, not now). Used to add drag and drop.
     */
    public void setCellDecorator(Consumer<TreeCell<Component>> decorator) {
        this.cellDecorator = decorator != null ? decorator : cell -> {
        };
        view.setCellFactory(tv -> new ExplorerCell());
    }

    /** Marks the row of the note open in the editor ({@code null}: none). */
    public void setActiveNoteId(String id) {
        this.activeNoteId = id;
        view.refresh();
    }

    /**
     * Reads again every folder that was already read (or is being read), keeping the rows of
     * folders and notes that are still there, so expansion and selection survive.
     */
    public void refresh() {
        index = null;
        indexGeneration++;
        if (lazyRoot != null) {
            refresh(lazyRoot);
        }
        if (!filterQuery.isEmpty()) {
            setFilter(filterQuery);
        }
    }

    /**
     * Shows only the notes and folders whose name contains {@code query} (ignoring case and
     * accents), each under its folders, which are shown open. A matching folder is shown
     * closed and can be opened to see everything in it. A blank query brings back the tree
     * exactly as it was, with the same folders open.
     *
     * <p>The first filter reads the whole vault once, off the FX thread; the result is kept
     * until the next {@link #refresh()}.</p>
     */
    public void setFilter(String query) {
        String normalized = normalize(query);
        filterQuery = normalized;
        long thisFilter = ++filterGeneration;
        if (normalized.isEmpty()) {
            if (lazyRoot != null && view.getRoot() != lazyRoot) {
                view.setRoot(lazyRoot);
            }
            return;
        }
        withIndex(entries -> {
            if (thisFilter == filterGeneration) {
                view.setRoot(buildFiltered(entries, normalized));
            }
        });
    }

    /** Whether a filter is active. */
    public boolean isFiltering() {
        return !filterQuery.isEmpty();
    }

    /**
     * Opens the folders that contain the note {@code noteId}, reading them if needed, then
     * selects the note and scrolls to it. Does nothing while a filter is active or if the
     * note is not in the vault.
     */
    public void reveal(String noteId) {
        if (noteId == null || lazyRoot == null || view.getRoot() != lazyRoot) {
            return;
        }
        Consumer<List<Entry>> revealIn = entries -> entries.stream()
                .filter(e -> e.item() instanceof Note note && noteId.equals(note.getId()))
                .findFirst()
                .ifPresent(e -> revealPath(lazyRoot, e.path(), 0, noteId));
        boolean cached = index != null;
        withIndex(entries -> {
            boolean found = entries.stream().anyMatch(e -> e.item() instanceof Note n && noteId.equals(n.getId()));
            if (!found && cached) {
                // A note created since the vault was last read: read it again once.
                index = null;
                withIndex(revealIn);
            } else {
                revealIn.accept(entries);
            }
        });
    }

    private void revealPath(FolderItem parent, List<Folder> path, int step, String noteId) {
        parent.whenLoaded(() -> {
            if (step == path.size()) {
                for (TreeItem<Component> child : parent.getChildren()) {
                    if (child.getValue() instanceof Note note && noteId.equals(note.getId())) {
                        view.getSelectionModel().select(child);
                        int row = view.getRow(child);
                        if (row >= 0) {
                            view.scrollTo(Math.max(0, row - 3));
                        }
                        return;
                    }
                }
                return;
            }
            String wanted = key(path.get(step));
            for (TreeItem<Component> child : parent.getChildren()) {
                if (child instanceof FolderItem folder && child.getValue() != null
                        && wanted.equals(key(child.getValue()))) {
                    folder.setExpanded(true);
                    revealPath(folder, path, step + 1, noteId);
                    return;
                }
            }
        });
    }

    /** One item of the vault with the folders above it (outermost first, the root excluded). */
    private record Entry(Component item, List<Folder> path) {
    }

    /** Runs {@code onFx} with the whole-vault listing, reading it first if it is not cached. */
    private void withIndex(Consumer<List<Entry>> onFx) {
        if (index != null) {
            onFx.accept(index);
            return;
        }
        Folder root = lazyRoot != null ? (Folder) lazyRoot.getValue() : null;
        if (root == null) {
            return;
        }
        SortOption sort = noteSort;
        long thisRead = ++indexGeneration;
        try {
            loader.execute(() -> {
                List<Entry> entries = new ArrayList<>();
                try {
                    walk(root, new ArrayList<>(), entries, sort, new java.util.HashSet<>());
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Failed to read the vault for the Explorer filter", e);
                }
                Platform.runLater(() -> {
                    if (thisRead == indexGeneration) {
                        index = List.copyOf(entries);
                    }
                    onFx.accept(entries);
                });
            });
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Explorer loader is shut down", e);
        }
    }

    private void walk(Folder folder, List<Folder> path, List<Entry> out, SortOption sort,
            java.util.Set<String> seen) {
        List<Component> ordered = ExplorerOrdering.order(source.subfolders(folder), source.notes(folder), sort);
        for (Component child : ordered) {
            out.add(new Entry(child, List.copyOf(path)));
            if (child instanceof Folder sub && seen.add(key(sub))) {
                path.add(sub);
                walk(sub, path, out, sort, seen);
                path.remove(path.size() - 1);
            }
        }
    }

    /** The filtered tree: each match under its folders (shown open), in vault order. */
    private TreeItem<Component> buildFiltered(List<Entry> entries, String query) {
        TreeItem<Component> root = new TreeItem<>(lazyRoot != null ? lazyRoot.getValue() : null);
        root.setExpanded(true);
        Map<String, TreeItem<Component>> folders = new HashMap<>();
        for (Entry entry : entries) {
            Component item = entry.item();
            if (!normalize(item.getTitle()).contains(query)) {
                continue;
            }
            TreeItem<Component> parent = root;
            for (Folder ancestor : entry.path()) {
                TreeItem<Component> existing = folders.get(key(ancestor));
                if (existing == null) {
                    existing = new TreeItem<>(ancestor);
                    folders.put(key(ancestor), existing);
                    parent.getChildren().add(existing);
                }
                existing.setExpanded(true);
                parent = existing;
            }
            if (item instanceof Folder folder) {
                if (!folders.containsKey(key(folder))) {
                    FolderItem match = new FolderItem(folder);
                    folders.put(key(folder), match);
                    parent.getChildren().add(match);
                }
            } else {
                parent.getChildren().add(new TreeItem<>(item));
            }
        }
        return root;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .trim();
    }

    private void refresh(FolderItem item) {
        if (!item.requested) {
            return;
        }
        item.load();
        for (TreeItem<Component> child : List.copyOf(item.getChildren())) {
            if (child instanceof FolderItem folder) {
                refresh(folder);
            }
        }
    }

    private void handleKey(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) {
            return;
        }
        TreeItem<Component> selected = view.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (selected.getValue() instanceof Note note) {
            onOpenNote.accept(note, event.isShortcutDown() ? OpenTarget.NEW_TAB : OpenTarget.CURRENT_TAB);
            event.consume();
        } else if (selected.getValue() instanceof Folder) {
            selected.setExpanded(!selected.isExpanded());
            event.consume();
        }
    }

    private static String key(Component component) {
        String id = component.getId() != null ? component.getId().replace('\\', '/') : "";
        return (component instanceof Folder ? "folder:" : "note:") + id;
    }

    /** A folder row: reads its contents the first time it is expanded. */
    private final class FolderItem extends TreeItem<Component> {
        /** A read was started (and may have finished). */
        private boolean requested;
        /** A read finished, so the children are the folder's real contents. */
        private boolean loaded;
        /** Bumped on every read; only the newest read may apply its result. */
        private long generation;
        /** Run once the folder's contents are read (see {@link #whenLoaded}). */
        private final List<Runnable> afterLoad = new ArrayList<>();

        FolderItem(Folder folder) {
            super(folder);
            expandedProperty().addListener((obs, was, now) -> {
                if (now && !requested) {
                    load();
                }
            });
        }

        /** Unread folders show an arrow; a read folder shows one only if it has contents. */
        @Override
        public boolean isLeaf() {
            return loaded && getChildren().isEmpty();
        }

        /** Runs {@code action} now if the folder was read, otherwise once it is (starting the read). */
        void whenLoaded(Runnable action) {
            if (loaded) {
                action.run();
                return;
            }
            afterLoad.add(action);
            if (!requested) {
                load();
            }
        }

        void load() {
            requested = true;
            long thisRead = ++generation;
            if (!loaded && getChildren().isEmpty()) {
                getChildren().setAll(List.of(new TreeItem<Component>()));
            }
            Folder folder = (Folder) getValue();
            SortOption sort = noteSort;
            try {
                loader.execute(() -> {
                    List<Component> ordered;
                    try {
                        ordered = ExplorerOrdering.order(source.subfolders(folder), source.notes(folder), sort);
                    } catch (RuntimeException e) {
                        logger.log(Level.WARNING, "Failed to read folder " + folder.getId(), e);
                        Platform.runLater(() -> failed(thisRead));
                        return;
                    }
                    Platform.runLater(() -> apply(thisRead, ordered));
                });
            } catch (RejectedExecutionException e) {
                failed(thisRead);
            }
        }

        private void apply(long read, List<Component> ordered) {
            if (read != generation) {
                return;
            }
            Map<String, TreeItem<Component>> existing = new HashMap<>();
            for (TreeItem<Component> child : getChildren()) {
                if (child.getValue() != null) {
                    existing.put(key(child.getValue()), child);
                }
            }
            List<TreeItem<Component>> next = new ArrayList<>(ordered.size());
            for (Component component : ordered) {
                TreeItem<Component> reused = existing.remove(key(component));
                if (reused != null) {
                    reused.setValue(component);
                    next.add(reused);
                } else if (component instanceof Folder folder) {
                    next.add(new FolderItem(folder));
                } else {
                    next.add(new TreeItem<>(component));
                }
            }
            loaded = true;
            getChildren().setAll(next);
            List<Runnable> pending = List.copyOf(afterLoad);
            afterLoad.clear();
            pending.forEach(Runnable::run);
        }

        private void failed(long read) {
            if (read != generation) {
                return;
            }
            requested = false;
            if (!loaded) {
                getChildren().clear();
            }
        }
    }

    /** A row: a folder (name only, the arrow is its affordance) or a note (type icon and name). */
    private final class ExplorerCell extends TreeCell<Component> {
        private final StackPane arrow = TreeDisclosureArrow.create();
        private final ChangeListener<Boolean> expandedListener = (obs, was, now) -> rotateArrow();
        private final FontIcon noteIcon = new FontIcon("fth-file-text");
        /** Folders get an icon too, so folder and note names start at the same column. */
        private final FontIcon folderIcon = new FontIcon("fth-folder");
        private final Label name = new Label();
        private final HBox content = new HBox(6);
        private TreeItem<Component> observed;

        ExplorerCell() {
            setDisclosureNode(arrow);
            getStyleClass().add("explorer-cell");
            noteIcon.setIconSize(13);
            noteIcon.getStyleClass().addAll("feather-icon", "explorer-note-icon");
            folderIcon.setIconSize(13);
            folderIcon.getStyleClass().addAll("feather-icon", "explorer-folder-icon");
            name.getStyleClass().add("explorer-cell-name");
            // Let the name shrink instead of widening the row (see SidebarController's folder cells).
            name.setMinWidth(0);
            name.setPrefWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            content.setAlignment(Pos.CENTER_LEFT);

            // Consumed so the press does not also select the row.
            addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (getItem() instanceof Note note && NewTabGesture.opensInNewTab(event)) {
                    event.consume();
                    onOpenNote.accept(note, OpenTarget.NEW_TAB);
                }
            });
            setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || event.isShortcutDown()
                        || event.getClickCount() != 1 || !event.isStillSincePress() || isEmpty()) {
                    return;
                }
                if (getItem() instanceof Note note) {
                    onOpenNote.accept(note, OpenTarget.CURRENT_TAB);
                } else if (getItem() instanceof Folder && getTreeItem() != null && !onArrow(event)) {
                    // The arrow already toggles on press; toggling here too would undo it.
                    getTreeItem().setExpanded(!getTreeItem().isExpanded());
                }
            });
            cellDecorator.accept(this);
        }

        private boolean onArrow(MouseEvent event) {
            Node node = event.getPickResult() != null ? event.getPickResult().getIntersectedNode() : null;
            while (node != null && node != this) {
                if (node == arrow) {
                    return true;
                }
                node = node.getParent();
            }
            return arrow.getBoundsInParent().contains(event.getX(), event.getY());
        }

        @Override
        protected void updateItem(Component item, boolean empty) {
            super.updateItem(item, empty);
            observe(empty ? null : getTreeItem());
            getStyleClass().removeAll(ACTIVE_NOTE_STYLE, LOADING_STYLE, FOLDER_STYLE, NOTE_STYLE);
            if (empty) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            if (item == null) {
                getStyleClass().add(LOADING_STYLE);
                setGraphic(null);
                setText(i18n.apply("explorer.loading"));
                setContextMenu(null);
                return;
            }
            name.setText(item.getTitle() != null ? item.getTitle() : "");
            if (item instanceof Note note) {
                noteIcon.setIconLiteral(switch (AttachmentType.fromName(note.getId())) {
                    case IMAGE -> "fth-image";
                    case PDF -> "fth-file";
                    case CANVAS -> "fth-grid";
                    default -> "fth-file-text";
                });
                content.getChildren().setAll(noteIcon, name);
                getStyleClass().add(NOTE_STYLE);
                if (activeNoteId != null && activeNoteId.equals(note.getId())) {
                    getStyleClass().add(ACTIVE_NOTE_STYLE);
                }
                setContextMenu(noteMenu.apply(note));
            } else {
                content.getChildren().setAll(folderIcon, name);
                getStyleClass().add(FOLDER_STYLE);
                setContextMenu(item instanceof Folder folder ? folderMenu.apply(folder) : null);
            }
            setText(null);
            setGraphic(content);
            rotateArrow();
        }

        private void observe(TreeItem<Component> item) {
            if (observed == item) {
                return;
            }
            if (observed != null) {
                observed.expandedProperty().removeListener(expandedListener);
            }
            observed = item;
            if (observed != null) {
                observed.expandedProperty().addListener(expandedListener);
            }
        }

        private void rotateArrow() {
            arrow.setRotate(observed != null && observed.isExpanded() ? 90 : 0);
        }
    }
}
