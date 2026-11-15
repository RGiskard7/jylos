package com.example.jylos.ui.controller;

import javafx.fxml.FXML;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.util.Duration;
import java.util.*;
import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.service.TrashService;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.EditorTabsModel;
import com.example.jylos.ui.components.ExplorerOrdering;
import com.example.jylos.ui.components.ExplorerTree;
import com.example.jylos.ui.components.TreeDisclosureArrow;
import com.example.jylos.ui.components.TreeIndentGuides;
import com.example.jylos.ui.components.NewTabGesture;
import com.example.jylos.util.FileManager;

/**
 * Sidebar: folders, tags, recent, favorites, and trash.
 * Uses explicit callbacks for single-owner selections and typed events where fan-out
 * or cross-feature coordination is useful.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class SidebarController {

    private static final Logger logger = LoggerConfig.getLogger(SidebarController.class);

    @FXML
    private VBox sidebarPane;
    @FXML
    private HBox sidebarNavBar;
    @FXML
    private Button navFoldersBtn;
    @FXML
    private Button navTagsBtn;
    @FXML
    private Button navRecentBtn;
    @FXML
    private Button navFavoritesBtn;
    @FXML
    private Button navTrashBtn;
    @FXML
    private TabPane navigationTabPane;
    @FXML
    private Tab foldersTab;
    @FXML
    private Tab tagsTab;
    @FXML
    private Tab recentTab;
    @FXML
    private Tab favoritesTab;
    @FXML
    private Tab trashTab;

    // Folders
    @FXML
    private TreeView<Folder> folderTreeView;
    @FXML
    private TextField filterFoldersField;
    private TreeItem<Folder> vaultRootItem;
    private boolean folderSortAscending = true;
    /** Preferences toggle: whether the folder tree shows each folder's note count badge. */
    private boolean showFolderNoteCounts = true;
    private final Map<String, Integer> folderNoteCountCache = new HashMap<>();
    /** Each folder's own direct count PLUS every descendant subfolder's, recursively —
     *  what the sidebar actually displays. Recomputed from {@link #folderNoteCountCache}
     *  by walking the real {@code TreeItem<Folder>} hierarchy, so it stays correct
     *  regardless of storage backend (SQLite ids are opaque, filesystem ids are paths —
     *  this doesn't parse either, it just sums what the tree already shows as children). */
    private final Map<String, Integer> folderRecursiveNoteCountCache = new HashMap<>();
    private boolean folderNoteCountCacheDirty = true;
    private String activeFolderId;
    /** Guards the selection listener while a tree rebuild restores the prior selection. */
    private boolean restoringFolderSelection = false;

    // Tags
    @FXML
    private ListView<String> tagListView;
    @FXML
    private TextField filterTagsField;
    private final ObservableList<String> masterTagsList = FXCollections
            .observableArrayList();
    private final Map<String, Tag> tagsByTitleCache = new HashMap<>();
    private boolean tagSortAscending = true;

    // Recent
    @FXML
    private ListView<String> recentNotesListView;
    @FXML
    private TextField filterRecentField;
    private final ObservableList<String> masterRecentList = FXCollections
            .observableArrayList();
    private boolean recentSortAscending = true;
    private List<Note> cachedRecentNotes = new ArrayList<>();

    // Favorites
    @FXML
    private ListView<String> favoritesListView;
    @FXML
    private TextField filterFavoritesField;
    private final ObservableList<String> masterFavoritesList = FXCollections
            .observableArrayList();
    private boolean favoritesSortAscending = true;
    private List<Note> cachedFavoriteNotes = new ArrayList<>();

    // Trash
    @FXML
    private TreeView<Component> trashTreeView;
    @FXML
    private TextField filterTrashField;
    private boolean trashSortAscending = true;

    // Services
    private NoteService noteService;
    private FolderService folderService;
    private TagService tagService;
    private EventBus eventBus;
    private ResourceBundle bundle;
    private Consumer<Folder> folderSelectionAction = folder -> {
    };
    private Consumer<Tag> tagSelectionAction = tag -> {
    };
    private Consumer<Component> trashSelectionAction = component -> {
    };
    private Consumer<Note> openNoteAction = note -> {
    };
    /** Opens a note in a new editor tab (Cmd/Ctrl+click or middle click on Recent/Favorites). */
    private Consumer<Note> openInNewTabAction = note -> {
    };
    private Consumer<Folder> createNoteInFolderAction = folder -> {
    };
    private Consumer<Folder> createCanvasInFolderAction = folder -> {
    };
    private Consumer<String> statusUpdateAction = message -> {
    };
    private final List<EventBus.Subscription> eventSubscriptions = new ArrayList<>();
    private final PauseTransition foldersFilterDebounce = new PauseTransition(Duration.millis(180));
    private final PauseTransition trashFilterDebounce = new PauseTransition(Duration.millis(180));
    private final PauseTransition foldersReloadDebounce = new PauseTransition(Duration.millis(120));
    private final PauseTransition trashReloadDebounce = new PauseTransition(Duration.millis(120));
    private final PauseTransition tagsReloadDebounce = new PauseTransition(Duration.millis(120));
    private final PauseTransition recentFavoritesReloadDebounce = new PauseTransition(Duration.millis(120));
    private final PauseTransition noteCountRebuildDebounce = new PauseTransition(Duration.millis(160));
    private final ExecutorService sidebarLoadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jylos-sidebar-loader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong noteCountBuildVersion = new AtomicLong(0);
    private final AtomicLong recentFavoritesLoadVersion = new AtomicLong(0);
    private final AtomicLong folderLoadVersion = new AtomicLong(0);
    private final AtomicLong trashLoadVersion = new AtomicLong(0);
    /** Drag and drop of notes and folders onto folder rows. */
    private final FolderDropSupport folderDrop = new FolderDropSupport(
            () -> noteService, () -> folderService, () -> eventBus, this::getString, this::publishStatusUpdate,
            () -> {
                invalidateFolderNoteCountCache();
                requestFoldersReload();
                requestRecentFavoritesReload();
                requestTrashReload();
            });

    private static final class FolderTreeBuildResult {
        private final List<Folder> roots;
        private final Map<String, List<Folder>> childrenByParent;
        private final Set<String> expandedIds;
        private final boolean filterActive;

        private FolderTreeBuildResult(List<Folder> roots, Map<String, List<Folder>> childrenByParent,
                Set<String> expandedIds, boolean filterActive) {
            this.roots = roots;
            this.childrenByParent = childrenByParent;
            this.expandedIds = expandedIds;
            this.filterActive = filterActive;
        }
    }

    private static final class TrashTreeBuildResult {
        private final Folder trashRoot;
        private final Set<String> visibleIds;
        private final boolean filtering;
        private final boolean sortAscending;

        private TrashTreeBuildResult(Folder trashRoot, Set<String> visibleIds, boolean filtering, boolean sortAscending) {
            this.trashRoot = trashRoot;
            this.visibleIds = visibleIds;
            this.filtering = filtering;
            this.sortAscending = sortAscending;
        }
    }

    // ---- Explorer layout: the folders tab shows one tree with folders and notes.

    @FXML
    private Button sortFoldersBtn;
    @FXML
    private ToggleButton revealActiveNoteBtn;
    /** Expands everything, or collapses it when something is open. */
    @FXML
    private Button expandCollapseBtn;
    /** Preference: the note order of the Explorer tree (a {@code NoteService.SortOption} name). */
    private static final String EXPLORER_NOTE_SORT_KEY = "ui.explorer.note_sort";
    private ExplorerTree explorerTree;
    /** Where the Explorer's switches and open folders are saved. */
    private static Preferences explorerPrefs = Preferences.userNodeForPackage(SidebarController.class);

    /** Saves the Explorer's state somewhere else (tests); {@code null} goes back to the user's. */
    public static void usePreferences(Preferences prefs) {
        explorerPrefs = prefs != null ? prefs : Preferences.userNodeForPackage(SidebarController.class);
    }
    /** Preference: keep the Explorer tree on the note open in the editor (off by default). */
    private static final String AUTO_REVEAL_KEY = "ui.explorer.auto_reveal";
    /** Preference: the Explorer folders left open, one key per line. */
    private static final String OPEN_FOLDERS_KEY = "ui.explorer.open_folders";
    private final PauseTransition openFoldersSave = new PauseTransition(Duration.millis(300));
    private Set<String> openFoldersToSave = Set.of();
    private boolean explorerMode;
    private String explorerActiveNoteId;
    private BiConsumer<Note, EditorTabsModel.OpenTarget> explorerOpenAction = (note, target) -> {
    };
    private Function<Note, ContextMenu> explorerNoteMenu = note -> null;
    private Consumer<Folder> explorerFolderContextAction = folder -> {
    };
    private BiConsumer<Note, String> explorerRenameNoteAction = (note, name) -> {
    };
    private final PauseTransition explorerRefreshDebounce = new PauseTransition(Duration.millis(200));

    /**
     * Shows the Explorer tree in the folders tab, in the classic folder tree's place, or the
     * classic tree again. The classic tree's filter and sort only apply to it, so they are
     * hidden while the Explorer is shown.
     */
    public void setExplorerMode(boolean on) {
        if (on == explorerMode) {
            return;
        }
        explorerMode = on;
        Node explorerView = ensureExplorerTree().getView();
        Node from = on ? folderTreeView : explorerView;
        Node to = on ? explorerView : folderTreeView;
        if (from.getParent() instanceof Pane parent) {
            parent.getChildren().set(parent.getChildren().indexOf(from), to);
            VBox.setVgrow(to, Priority.ALWAYS);
        }
        // One header row for both trees: the filter, the sort button and expand/collapse act on
        // whichever tree is shown; "reveal the open note" only exists in the Explorer.
        UiNodes.show(revealActiveNoteBtn, on);
        if (filterFoldersField != null) {
            filterFoldersField.clear();
            filterFoldersField.setPromptText(getString(on ? "tab.explorer.filter" : "tab.folders.filter"));
        }
        if (sortFoldersBtn != null) {
            sortFoldersBtn.setTooltip(new Tooltip(getString(on ? "action.sort_notes" : "action.sort_folders")));
        }
        if (on) {
            explorerTree.setRoot(explorerRootFolder());
        }
        updateExpandCollapseButton();
    }

    public boolean isExplorerMode() {
        return explorerMode;
    }

    /** The Explorer tree (created on first use). */
    public ExplorerTree getExplorerTree() {
        return ensureExplorerTree();
    }

    /** What opening a note from the Explorer tree does. */
    public void setExplorerOpenAction(BiConsumer<Note, EditorTabsModel.OpenTarget> action) {
        this.explorerOpenAction = action != null ? action : (note, target) -> {
        };
    }

    /** The note context menu used by the Explorer tree's note rows. */
    public void setExplorerNoteMenu(Function<Note, ContextMenu> menu) {
        this.explorerNoteMenu = menu != null ? menu : note -> null;
        if (explorerTree != null) {
            explorerTree.setNoteMenu(this.explorerNoteMenu);
        }
    }

    /**
     * Told which folder new notes should go to when the selection in the Explorer tree
     * changes: the selected folder, or the folder of the selected note.
     */
    public void setExplorerFolderContextAction(Consumer<Folder> action) {
        this.explorerFolderContextAction = action != null ? action : folder -> {
        };
    }

    /** Marks the note open in the editor in the Explorer tree ({@code null}: none). */
    public void setExplorerActiveNote(String noteId) {
        explorerActiveNoteId = noteId;
        if (explorerTree != null) {
            explorerTree.setActiveNoteId(noteId);
        }
        if (isAutoReveal()) {
            revealExplorerActiveNote();
        }
    }

    private boolean isAutoReveal() {
        return revealActiveNoteBtn != null && revealActiveNoteBtn.isSelected();
    }

    /** "Show the current file automatically": saved; switching it on shows the open note now. */
    @FXML
    private void handleToggleAutoReveal(ActionEvent e) {
        explorerPrefs.putBoolean(AUTO_REVEAL_KEY, isAutoReveal());
        if (isAutoReveal()) {
            revealExplorerActiveNote();
        }
    }

    /** One button: expands everything when all is collapsed, otherwise collapses it. */
    @FXML
    private void handleToggleExpandAll(ActionEvent e) {
        if (allCollapsed()) {
            handleExpandAllFolders(e);
        } else {
            handleCollapseAllFolders(e);
        }
        updateExpandCollapseButton();
    }

    /** Whether no folder at the top of the tree shown is open. */
    private boolean allCollapsed() {
        TreeView<?> tree = explorerMode && explorerTree != null ? explorerTree.getView() : folderTreeView;
        if (tree == null || tree.getRoot() == null) {
            return true;
        }
        for (TreeItem<?> child : tree.getRoot().getChildren()) {
            if (child.isExpanded() && !child.isLeaf()) {
                return false;
            }
        }
        return true;
    }

    /** The button says what it will do: expand everything, or collapse it. */
    private void updateExpandCollapseButton() {
        if (expandCollapseBtn == null) {
            return;
        }
        boolean collapsed = allCollapsed();
        if (expandCollapseBtn.getGraphic() instanceof FontIcon icon) {
            icon.setIconLiteral(collapsed ? "fth-chevrons-down" : "fth-chevrons-up");
        }
        String text = getString(collapsed ? "action.expand_all" : "action.collapse_all");
        if (expandCollapseBtn.getTooltip() == null) {
            expandCollapseBtn.setTooltip(new Tooltip(text));
        } else {
            expandCollapseBtn.getTooltip().setText(text);
        }
    }

    /** Saves the open Explorer folders a moment after the last change. */
    private void rememberOpenFolders(Set<String> keys) {
        openFoldersToSave = keys;
        openFoldersSave.playFromStart();
    }

    private static List<String> storedOpenFolders() {
        String stored = explorerPrefs.get(OPEN_FOLDERS_KEY, "");
        return stored.isBlank() ? List.of() : List.of(stored.split("\n"));
    }

    /** Shows the note open in the editor in the Explorer tree (clearing a filter first). */
    public void revealExplorerActiveNote() {
        if (explorerMode && explorerTree != null) {
            if (explorerTree.isFiltering() && filterFoldersField != null) {
                filterFoldersField.clear();
                explorerTree.setFilter("");
            }
            explorerTree.reveal(explorerActiveNoteId);
        }
    }

    /** The Explorer's note order, as a menu under the sort button; the choice is remembered. */
    private void showExplorerSortMenu() {
        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        String[][] options = {
                { "TITLE_ASC", "sort.title_az" }, { "TITLE_DESC", "sort.title_za" },
                { "MODIFIED_NEWEST", "sort.modified_newest" }, { "MODIFIED_OLDEST", "sort.modified_oldest" },
                { "CREATED_NEWEST", "sort.created_newest" }, { "CREATED_OLDEST", "sort.created_oldest" } };
        for (String[] option : options) {
            NoteService.SortOption sort = NoteService.SortOption.valueOf(option[0]);
            RadioMenuItem item = new RadioMenuItem(getString(option[1]));
            item.setToggleGroup(group);
            item.setSelected(explorerTree.getNoteSort() == sort);
            item.setOnAction(ev -> {
                explorerTree.setNoteSort(sort);
                explorerPrefs.put(EXPLORER_NOTE_SORT_KEY, sort.name());
            });
            menu.getItems().add(item);
        }
        menu.show(sortFoldersBtn, Side.BOTTOM, 0, 0);
    }

    private static NoteService.SortOption storedExplorerSort() {
        String stored = explorerPrefs.get(EXPLORER_NOTE_SORT_KEY, "");
        for (NoteService.SortOption option : NoteService.SortOption.values()) {
            if (option.name().equals(stored)) {
                return option;
            }
        }
        return NoteService.SortOption.TITLE_ASC;
    }

    private ExplorerTree ensureExplorerTree() {
        if (explorerTree != null) {
            return explorerTree;
        }
        ExplorerTree.Source source = new ExplorerTree.Source() {
            @Override
            public List<Folder> subfolders(Folder folder) {
                return folderService != null ? folderService.getSubfolders(folder) : List.of();
            }

            @Override
            public List<Note> notes(Folder folder) {
                return noteService != null && folder != null && folder.getId() != null
                        ? noteService.getNotesByFolder(folder) : List.of();
            }
        };
        explorerTree = new ExplorerTree(source, sidebarLoadExecutor, this::getString);
        explorerTree.getView().getStyleClass().add("sidebar-tree");
        explorerTree.setNoteSort(storedExplorerSort());
        explorerTree.setOpenFolders(storedOpenFolders());
        explorerTree.setOnOpenFoldersChanged(this::rememberOpenFolders);
        openFoldersSave.setOnFinished(e -> explorerPrefs.put(OPEN_FOLDERS_KEY, String.join("\n", openFoldersToSave)));
        explorerTree.getView().expandedItemCountProperty()
                .addListener((obs, was, now) -> updateExpandCollapseButton());
        // Same count as the classic tree (the folder's notes plus its subfolders'), same preference.
        explorerTree.setFolderBadge(folder -> {
            int count = showFolderNoteCounts ? getNoteCountForFolder(folder) : 0;
            return count > 0 ? String.valueOf(count) : null;
        });
        explorerTree.setOnOpenNote((note, target) -> explorerOpenAction.accept(note, target));
        explorerTree.setNoteMenu(explorerNoteMenu);
        // In the Explorer, "Rename" puts a name field in the row instead of opening a dialog.
        explorerTree.setFolderMenu(folder -> FolderContextMenuSupport.create(folder, explorerFolderMenuActions(),
                isFileSystemStorage(), this::getString));
        explorerTree.setOnRename((item, name) -> {
            if (item instanceof Note note) {
                explorerRenameNoteAction.accept(note, name);
            } else if (item instanceof Folder folder) {
                renameFolderTo(folder, name.strip());
            }
        });
        explorerTree.setCellDecorator(cell -> {
            FolderDropSupport.installDragSource(cell, () -> {
                Component item = cell.getItem();
                return item instanceof Note note ? FolderDropSupport.notePayload(note)
                        : item instanceof Folder folder ? FolderDropSupport.folderPayload(folder) : null;
            });
            folderDrop.installDropTarget(cell, () -> folderOf(cell.getTreeItem()));
        });
        explorerTree.setActiveNoteId(explorerActiveNoteId);
        explorerTree.getView().getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            Folder folder = folderOf(item);
            if (folder != null) {
                explorerFolderContextAction.accept(folder);
            }
        });
        explorerRefreshDebounce.setOnFinished(e -> {
            if (explorerMode && explorerTree != null) {
                explorerTree.refresh();
            }
        });
        return explorerTree;
    }

    /** The folder a row stands for: itself, or the folder containing the note (drops onto a note land there). */
    private static Folder folderOf(TreeItem<Component> item) {
        if (item == null) {
            return null;
        }
        if (item.getValue() instanceof Folder folder) {
            return folder;
        }
        TreeItem<Component> parent = item.getParent();
        return parent != null && parent.getValue() instanceof Folder folder ? folder : null;
    }

    private Folder explorerRootFolder() {
        String title = vaultRootItem != null && vaultRootItem.getValue() != null
                ? vaultRootItem.getValue().getTitle() : getString("app.my_notes");
        return new Folder("ROOT", title);
    }

    private void requestExplorerRefresh() {
        if (explorerMode && explorerTree != null) {
            explorerRefreshDebounce.playFromStart();
        }
    }


    /** What Cmd/Ctrl+click and middle click on a Recent/Favorites entry do with the note. */
    public void setOpenInNewTabAction(Consumer<Note> action) {
        this.openInNewTabAction = action != null ? action : note -> {
        };
    }

    /**
     * Cmd/Ctrl+click or middle click on an entry of a title list (Recent, Favorites) opens
     * that note in a new tab. The press is consumed so the list does not also select the
     * entry, which would open it in the current tab.
     */
    private void installNewTabGesture(ListView<String> list, Supplier<List<Note>> notes) {
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!NewTabGesture.opensInNewTab(event)) {
                return;
            }
            Node node = event.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof ListCell<?>)) {
                node = node.getParent();
            }
            if (!(node instanceof ListCell<?> cell) || cell.isEmpty() || cell.getItem() == null) {
                return;
            }
            Object title = cell.getItem();
            event.consume();
            notes.get().stream().filter(n -> title.equals(n.getTitle())).findFirst()
                    .ifPresent(openInNewTabAction);
        });
    }

    // Setters for MainController
    public void wire(EventBus eventBus, NoteService noteService, TagService tagService,
            FolderService folderService, ResourceBundle bundle,
            Consumer<Folder> folderSelectionAction, Consumer<Tag> tagSelectionAction,
            Consumer<Component> trashSelectionAction, Consumer<Note> openNoteAction,
            Consumer<Folder> createNoteInFolderAction, Consumer<Folder> createCanvasInFolderAction,
            Consumer<String> statusUpdateAction) {
        setNoteService(noteService);
        setTagService(tagService);
        setFolderService(folderService);
        setBundle(bundle);
        this.folderSelectionAction = folderSelectionAction != null ? folderSelectionAction : folder -> {
        };
        this.tagSelectionAction = tagSelectionAction != null ? tagSelectionAction : tag -> {
        };
        this.trashSelectionAction = trashSelectionAction != null ? trashSelectionAction : component -> {
        };
        this.openNoteAction = openNoteAction != null ? openNoteAction : note -> {
        };
        this.createNoteInFolderAction = createNoteInFolderAction != null ? createNoteInFolderAction : folder -> {
        };
        this.createCanvasInFolderAction = createCanvasInFolderAction != null ? createCanvasInFolderAction : folder -> {
        };
        this.statusUpdateAction = statusUpdateAction != null ? statusUpdateAction : message -> {
        };
        setEventBus(eventBus);
    }

    private void setEventBus(EventBus eb) {
        eventSubscriptions.forEach(EventBus.Subscription::cancel);
        eventSubscriptions.clear();
        this.eventBus = eb;
        if (this.eventBus != null) {
            registerEventSubscriptions();
        }
    }

    private void setNoteService(NoteService ns) {
        this.noteService = ns;
    }

    private void setTagService(TagService ts) {
        this.tagService = ts;
    }

    private void setFolderService(FolderService fs) {
        this.folderService = fs;
    }

    private void setBundle(ResourceBundle b) {
        this.bundle = b;
        refreshLocalizedRootLabels();
        refreshLocalizedMenus();
        applySidebarTabPresentation();
    }

    @FXML
    public void initialize() {
        // Core initialization of tree structures (invisible roots)
        initializeFolderTree();
        initializeTrashTree();

        // Setup filter propagation
        setupFilteredList(tagListView, masterTagsList, filterTagsField);
        setupFilteredList(recentNotesListView, masterRecentList, filterRecentField);
        setupFilteredList(favoritesListView, masterFavoritesList, filterFavoritesField);
        if (revealActiveNoteBtn != null) {
            revealActiveNoteBtn.setSelected(explorerPrefs.getBoolean(AUTO_REVEAL_KEY, false));
        }
        folderTreeView.expandedItemCountProperty().addListener((obs, was, now) -> updateExpandCollapseButton());
        updateExpandCollapseButton();

        // Selection listeners - Matching MainController patterns
        folderTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Suppressed while we re-select the previously-selected folder after a tree
            // rebuild, so restoring the selection does not re-navigate / reload notes.
            if (restoringFolderSelection) {
                return;
            }
            if (newVal != null && newVal.getValue() != null) {
                Folder f = newVal.getValue();
                if (!"INVISIBLE_ROOT".equals(f.getTitle())) {
                    activeFolderId = f.getId();
                    folderSelectionAction.accept(f);
                }
            }
        });

        tagListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Tag selected = tagsByTitleCache.get(newVal);
                if (selected != null) {
                    tagSelectionAction.accept(selected);
                }
            }
        });

        recentNotesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                cachedRecentNotes.stream().filter(n -> n.getTitle().equals(newVal)).findFirst().ifPresent(n -> {
                    openNoteAction.accept(n);
                });
            }
        });

        installNewTabGesture(recentNotesListView, () -> cachedRecentNotes);
        installNewTabGesture(favoritesListView, () -> cachedFavoriteNotes);

        favoritesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                cachedFavoriteNotes.stream().filter(n -> n.getTitle().equals(newVal)).findFirst().ifPresent(n -> {
                    openNoteAction.accept(n);
                });
            }
        });

        trashTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                trashSelectionAction.accept(newVal.getValue());
            }
        });

        foldersFilterDebounce.setOnFinished(e -> {
            if (explorerMode && explorerTree != null) {
                explorerTree.setFilter(filterFoldersField.getText());
            } else {
                loadFolders();
            }
        });
        trashFilterDebounce.setOnFinished(e -> loadTrashTree());
        foldersReloadDebounce.setOnFinished(e -> loadFolders());
        trashReloadDebounce.setOnFinished(e -> loadTrashTree());
        tagsReloadDebounce.setOnFinished(e -> loadTags());
        recentFavoritesReloadDebounce.setOnFinished(e -> loadRecentAndFavoritesAsync());
        noteCountRebuildDebounce.setOnFinished(e -> rebuildFolderNoteCountCacheAsync());
        filterFoldersField.textProperty().addListener((o, ov, nv) -> foldersFilterDebounce.playFromStart());
        filterTrashField.textProperty().addListener((o, ov, nv) -> trashFilterDebounce.playFromStart());

        setupCellFactories();
        setupTrashContextMenu();
        invalidateFolderNoteCountCache();

        // Register event subscriptions now that eventBus is available
        registerEventSubscriptions();
        setupSidebarNavigation();
    }

    private void initializeFolderTree() {
        Folder invisibleRoot = new Folder("INVISIBLE_ROOT", null, null);
        TreeItem<Folder> rootContainer = new TreeItem<>(invisibleRoot);
        folderTreeView.setRoot(rootContainer);
        folderTreeView.setShowRoot(false);

        // Vault Root Folder logic literal
        String vaultName = "My Vault";
        try {
            Preferences prefs = Preferences.userNodeForPackage(SidebarController.class);
            String path = prefs.get("filesystem_path", "");
            if (!path.isEmpty()) {
                File f = new File(path);
                if (f.exists())
                    vaultName = f.getName();
            } else if ("sqlite".equals(prefs.get("storage_type", "sqlite"))) {
                vaultName = getString("app.my_notes");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to resolve vault name from preferences", e);
        }

        Folder vaultFolder = new Folder(vaultName, null, null);
        vaultFolder.setId("ROOT");
        vaultRootItem = new TreeItem<>(vaultFolder);
        vaultRootItem.setExpanded(true);
        folderTreeView.getRoot().getChildren().add(vaultRootItem);
        refreshLocalizedRootLabels();
    }

    private void refreshLocalizedRootLabels() {
        if (vaultRootItem != null && vaultRootItem.getValue() != null) {
            try {
                Preferences prefs = Preferences.userNodeForPackage(SidebarController.class);
                if ("sqlite".equals(prefs.get("storage_type", "sqlite"))) {
                    vaultRootItem.getValue().setTitle(getString("app.my_notes"));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to refresh localized root labels", e);
            }
        }
        if (folderTreeView != null) {
            folderTreeView.refresh();
        }
    }

    private void initializeTrashTree() {
        Folder trashRoot = new Folder("INVISIBLE_ROOT", null, null);
        TreeItem<Component> trashRootItem = new TreeItem<>(trashRoot);
        trashTreeView.setRoot(trashRootItem);
        trashTreeView.setShowRoot(false);
    }

    private void setupCellFactories() {
        folderTreeView.setCellFactory(tv -> new TreeCell<Folder>() {
            private final ChangeListener<Boolean> expandedListener = (obs, wasExpanded, isExpanded) -> refreshFolderIcon();
            private final StackPane disclosureArrow = TreeDisclosureArrow.create();
            private final TreeIndentGuides guides = new TreeIndentGuides();
            private TreeItem<Folder> observedFolderItem;
            private Label folderIconLabel;
            private Folder shownFolder;

            {
                setDisclosureNode(disclosureArrow);
                FolderDropSupport.installDragSource(this, () -> FolderDropSupport.folderPayload(getItem()));
                folderDrop.installDropTarget(this, this::getItem);
                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.SECONDARY && !isEmpty()) {
                        restoringFolderSelection = true;
                        folderTreeView.getSelectionModel().select(getTreeItem());
                        Platform.runLater(() -> restoringFolderSelection = false);
                    }
                });
            }

            @Override
            protected void updateItem(Folder folder, boolean empty) {
                super.updateItem(folder, empty);
                // JavaFX updates :hover only on mouse moves; a row reused for another item
                // while scrolling under a still mouse would keep highlighting it.
                if (shownFolder != (empty ? null : folder) && isHover()) {
                    setHover(false);
                }
                shownFolder = empty ? null : folder;
                TreeItem<Folder> currentItem = getTreeItem();
                if (observedFolderItem != currentItem) {
                    if (observedFolderItem != null) {
                        observedFolderItem.expandedProperty().removeListener(expandedListener);
                    }
                    observedFolderItem = currentItem;
                    if (observedFolderItem != null) {
                        observedFolderItem.expandedProperty().addListener(expandedListener);
                    }
                }
                if (empty || folder == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    folderIconLabel = null;
                } else {
                    Label iconLabel = new Label("");
                    folderIconLabel = iconLabel;
                    iconLabel.getStyleClass().setAll("folder-cell-icon");
                    TreeItem<Folder> ti = getTreeItem();
                    boolean isExp = ti != null && ti.isExpanded();
                    iconLabel.setText(isExp ? "[/]" : "[+]");
                    iconLabel.getStyleClass().add(isExp ? "folder-expanded" : "folder-collapsed");
                    int count = 0;
                    try {
                        count = getNoteCountForFolder(folder);
                    } catch (Exception e) {
                        logger.warning("Failed to compute folder note count for "
                                + (folder != null ? folder.getId() : "null") + ": " + e.getMessage());
                    }
                    HBox container = new HBox(6);
                    container.setAlignment(Pos.CENTER_LEFT);
                    Label nameLabel = new Label(folder.getTitle());
                    nameLabel.getStyleClass().add("folder-cell-name");
                    // The name is the flexible element, not a separate spacer: growing to
                    // fill whatever room the row actually has, but also — critically —
                    // allowed to shrink below its own text's natural width (minWidth 0
                    // overrides a Label's default of never shrinking past its content) AND
                    // told not to prefer that natural width either (prefWidth 0). Skipping
                    // that last part was the first fix's actual bug: TreeCellSkin sizes the
                    // ROW ITSELF off the graphic's *preferred* width, not its resized/actual
                    // one — a Label's preferred width is always its full, uncompressed text
                    // width regardless of minWidth, so a long folder name still made the row
                    // (and therefore the whole tree) want to be wider than the sidebar, which
                    // is exactly what forces a permanent horizontal scrollbar and pushes the
                    // count off-screen. With prefWidth pinned to 0, the row's own natural size
                    // no longer depends on the name's length; Hgrow.ALWAYS still expands it to
                    // fill whatever space is actually available once the row is laid out.
                    nameLabel.setMaxWidth(Double.MAX_VALUE);
                    nameLabel.setMinWidth(0);
                    nameLabel.setPrefWidth(0);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);
                    if (count > 0 && showFolderNoteCounts) {
                        Label countLabel = new Label("(" + count + ")");
                        countLabel.getStyleClass().add("folder-cell-count");
                        container.getChildren().addAll(iconLabel, nameLabel, countLabel);
                    } else {
                        container.getChildren().addAll(iconLabel, nameLabel);
                    }
                    setGraphic(container);
                    setText(null);
                    setContextMenu(FolderContextMenuSupport.create(folder, folderMenuActions(), isFileSystemStorage(),
                            SidebarController.this::getString));

                    refreshDisclosureArrowRotation();
                }
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                guides.layout(this, getChildren());
            }

            private void refreshFolderIcon() {
                refreshDisclosureArrowRotation();
                Folder folder = getItem();
                if (folder == null || folderIconLabel == null) {
                    return;
                }
                TreeItem<Folder> item = getTreeItem();
                boolean expanded = item != null && item.isExpanded();
                folderIconLabel.setText(expanded ? "[/]" : "[+]");
                folderIconLabel.getStyleClass().setAll(
                        "folder-cell-icon",
                        expanded ? "folder-expanded" : "folder-collapsed");
            }

            /** Points the chevron down when expanded, right when collapsed — same affordance
             *  as the "[+]"/"[/]" icon label, just on the actual clickable disclosure arrow. */
            private void refreshDisclosureArrowRotation() {
                TreeItem<Folder> item = getTreeItem();
                boolean expanded = item != null && item.isExpanded();
                disclosureArrow.setRotate(expanded ? 90 : 0);
            }
        });

        trashTreeView.setCellFactory(tv -> new TreeCell<Component>() {
            // Same disclosure-arrow cache bug as folderTreeView above (each TreeView has
            // its own independent cache entry) — the trash tree mixes deleted folders
            // (branch) and deleted notes (leaf) too, so it needs the same fix. Its icons are
            // the Explorer's (folder and document), since it is the same kind of mixed tree.
            private final StackPane arrow = TreeDisclosureArrow.create();
            private final TreeIndentGuides guides = new TreeIndentGuides();
            private final ChangeListener<Boolean> expandedListener = (obs, was, now) -> rotateArrow();
            private TreeItem<Component> observed;
            private Component shown;

            {
                setDisclosureNode(arrow);
            }

            @Override
            protected void updateItem(Component item, boolean empty) {
                super.updateItem(item, empty);
                // JavaFX updates :hover only on mouse moves; a row reused for another item
                // while scrolling under a still mouse would keep highlighting it.
                if (shown != (empty ? null : item) && isHover()) {
                    setHover(false);
                }
                shown = empty ? null : item;
                TreeItem<Component> current = empty ? null : getTreeItem();
                if (observed != current) {
                    if (observed != null) {
                        observed.expandedProperty().removeListener(expandedListener);
                    }
                    observed = current;
                    if (observed != null) {
                        observed.expandedProperty().addListener(expandedListener);
                    }
                }
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                HBox container = new HBox(6);
                container.setAlignment(Pos.CENTER_LEFT);
                String title = item.getTitle();
                FontIcon icon;
                if (item instanceof Folder) {
                    icon = new FontIcon("fth-folder");
                    if (title != null && title.equals(".trash")) {
                        title = getString("tab.trash");
                    } else if (title != null) {
                        int idx = title.lastIndexOf('/');
                        if (idx != -1) {
                            title = title.substring(idx + 1);
                        }
                    }
                } else {
                    icon = new FontIcon("fth-file-text");
                }
                icon.setIconSize(13);
                icon.getStyleClass().add("feather-icon");
                Label nameLabel = new Label(title);
                nameLabel.getStyleClass().add("folder-cell-name");
                container.getChildren().addAll(icon, nameLabel);
                setGraphic(container);
                setText(null);
                rotateArrow();
            }

            private void rotateArrow() {
                arrow.setRotate(observed != null && observed.isExpanded() ? 90 : 0);
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                guides.layout(this, getChildren());
            }
        });

        tagListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean e) {
                super.updateItem(item, e);
                if (e || item == null) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    setText("# " + item);
                    setContextMenu(createTagContextMenu(item));
                }
            }
        });
    }

    private void setupTrashContextMenu() {
        if (trashTreeView == null) {
            return;
        }
        ContextMenu trashMenu = new ContextMenu();
        MenuItem restoreItem = new MenuItem(getString("action.restore"));
        restoreItem.setOnAction(e -> handleRestoreTrashItem());
        MenuItem deleteItem = new MenuItem(getString("action.delete_permanently"));
        deleteItem.setOnAction(e -> handleDeleteTrashItem());
        trashMenu.getItems().addAll(restoreItem, deleteItem);
        trashTreeView.setContextMenu(trashMenu);
    }

    private void refreshLocalizedMenus() {
        setupTrashContextMenu();
        applySidebarTabPresentation();
    }

    private void registerEventSubscriptions() {
        if (eventBus == null || !eventSubscriptions.isEmpty()) {
            return;
        }
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class, event -> {
            invalidateFolderNoteCountCache();
            requestTrashReload();
            requestRecentFavoritesReload();
            // The deleted note may have been the last one carrying a given tag; for
            // filesystem storage a tag has no existence of its own beyond the notes that
            // currently declare it, so the sidebar list must be recomputed too.
            requestTagsReload();
            // The Explorer tree lists notes, so a note moved to the trash must leave it.
            requestExplorerRefresh();
        }));
        // A note changed outside Jylos (or by another view) may have been renamed or moved.
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteUpdatedEvent.class, event -> {
            requestExplorerRefresh();
            requestRecentFavoritesReload();
        }));
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, event -> {
            invalidateFolderNoteCountCache();
            requestFoldersReload();
            requestRecentFavoritesReload();
            requestTagsReload();
        }));
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteSavedEvent.class, event -> {
            // A content save does not change the folder structure or note counts, so the
            // folder tree is NOT rebuilt here (rebuilding re-selected the folder and fired
            // a second, racing notes-list load that could blank the panel). The
            // recents/favorites lists, whose ordering may change, are refreshed — and so
            // are tags, since an edit may have added or removed an inline #tag. Both
            // reloads are debounced, so rapid autosave ticks while typing collapse into
            // a single reload instead of one per tick.
            requestRecentFavoritesReload();
            requestTagsReload();
            // A save can rename or move the note; the Explorer tree shows notes, so it re-reads
            // the folders it has open (debounced, like the reloads above).
            requestExplorerRefresh();
        }));
        eventSubscriptions.add(eventBus.subscribe(FolderEvents.FolderDeletedEvent.class, event -> {
            invalidateFolderNoteCountCache();
            requestFoldersReload();
            requestTrashReload();
        }));
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.TrashItemDeletedEvent.class, event -> {
            invalidateFolderNoteCountCache();
            requestTrashReload();
            requestFoldersReload();
        }));
        eventSubscriptions.add(eventBus.subscribe(FolderEvents.FoldersRefreshRequestedEvent.class, event -> {
            requestFoldersReload();
            requestTrashReload();
        }));
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, event -> {
            invalidateFolderNoteCountCache();
            requestRecentFavoritesReload();
            requestTrashReload();
            requestFoldersReload();
        }));
    }

    public void teardown() {
        eventSubscriptions.forEach(EventBus.Subscription::cancel);
        eventSubscriptions.clear();
        sidebarLoadExecutor.shutdownNow();
    }

    private void setupFilteredList(ListView<String> listView, ObservableList<String> masterList,
            TextField filterField) {
        FilteredList<String> filteredList = new FilteredList<>(
                masterList, p -> true);
        listView.setItems(filteredList);
        if (filterField != null) {
            filterField.textProperty().addListener((obs, oldVal, newVal) -> {
                filteredList.setPredicate(item -> {
                    if (newVal == null || newVal.isEmpty())
                        return true;
                    return item.toLowerCase().contains(newVal.toLowerCase());
                });
            });
        }
    }

    public void loadFolders() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::loadFolders);
            return;
        }
        requestExplorerRefresh();
        if (folderService == null) {
            logger.warning("Cannot load folders: folderService is null");
            return;
        }
        try {
            if (vaultRootItem == null) {
                return;
            }
            final Set<String> expandedIds = collectExpandedFolderIds(vaultRootItem);
            final String filter = filterFoldersField.getText() == null ? ""
                    : filterFoldersField.getText().toLowerCase().trim();
            final boolean filterActive = !filter.isEmpty();
            final boolean currentSortAscending = folderSortAscending;
            final long requestId = folderLoadVersion.incrementAndGet();

            if (folderNoteCountCacheDirty) {
                noteCountRebuildDebounce.playFromStart();
            }
            sidebarLoadExecutor.submit(() -> {
                try {
                    List<Folder> folders = folderService.getAllFolders();
                    FolderTreeBuildResult buildResult = buildFolderTreeResult(
                            folders, filter, filterActive, expandedIds, currentSortAscending);
                    Platform.runLater(() -> applyFolderTreeBuildResult(requestId, buildResult));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to build folder tree", e);
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load folders", e);
        }
    }

    private FolderTreeBuildResult buildFolderTreeResult(List<Folder> folders, String filter, boolean filterActive,
            Set<String> expandedIds, boolean sortAscending) {
        Map<String, Folder> foldersById = new HashMap<>();
        for (Folder folder : folders) {
            if (folder != null && folder.getId() != null) {
                foldersById.put(normalizeId(folder.getId()), folder);
            }
        }

        Map<String, String> parentById = new HashMap<>();
        for (Folder folder : folders) {
            if (folder == null || folder.getId() == null) {
                continue;
            }
            String folderId = normalizeId(folder.getId());
            String parentId = resolveParentId(folder, foldersById);
            if (parentId != null && !parentId.isBlank()) {
                parentById.put(folderId, normalizeId(parentId));
            }
        }

        Set<String> visibleIds = new HashSet<>();
        if (filterActive) {
            for (Folder f : folders) {
                if (f == null || f.getId() == null || f.getTitle() == null) {
                    continue;
                }
                String id = normalizeId(f.getId());
                if (f.getTitle().toLowerCase().contains(filter)) {
                    visibleIds.add(id);
                    String parentId = parentById.get(id);
                    int safety = 0;
                    while (parentId != null && !parentId.isBlank() && !"ROOT".equals(parentId) && safety++ < 200) {
                        visibleIds.add(parentId);
                        parentId = parentById.get(parentId);
                    }
                }
            }
        }

        Map<String, List<Folder>> childrenByParent = new HashMap<>();
        List<Folder> roots = new ArrayList<>();
        for (Folder f : folders) {
            if (f == null || f.getId() == null) {
                continue;
            }
            String id = normalizeId(f.getId());
            if (filterActive && !visibleIds.contains(id)) {
                continue;
            }
            String parentId = parentById.get(id);
            if (parentId == null || parentId.isBlank() || "ROOT".equals(parentId)
                    || (filterActive && !visibleIds.contains(parentId))) {
                roots.add(f);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(f);
            }
        }
        // Same order as the Explorer: accents and case do not matter, numbers by value.
        Comparator<Folder> comp = Comparator.comparing(Folder::getTitle, ExplorerOrdering.NAME_ORDER);
        if (!sortAscending) {
            comp = comp.reversed();
        }
        Collections.sort(roots, comp);
        for (List<Folder> children : childrenByParent.values()) {
            children.sort(comp);
        }
        return new FolderTreeBuildResult(roots, childrenByParent, expandedIds, filterActive);
    }

    private void applyFolderTreeBuildResult(long requestId, FolderTreeBuildResult buildResult) {
        if (requestId != folderLoadVersion.get()) {
            return;
        }
        if (vaultRootItem == null || buildResult == null) {
            return;
        }
        // Same order as the Explorer: accents and case do not matter, numbers by value.
        Comparator<Folder> comp = Comparator.comparing(Folder::getTitle, ExplorerOrdering.NAME_ORDER);
        if (!folderSortAscending) {
            comp = comp.reversed();
        }
        // Remember the selected folder so a rebuild (e.g. after a vault refresh) does not
        // drop the selection and scroll the tree back to the root.
        TreeItem<Folder> previous = folderTreeView.getSelectionModel().getSelectedItem();
        String selectedFolderId = previous != null && previous.getValue() != null
                ? previous.getValue().getId() : null;

        boolean wasRestoringSelection = restoringFolderSelection;
        restoringFolderSelection = true;
        try {
            vaultRootItem.getChildren().clear();
            for (Folder f : buildResult.roots) {
                TreeItem<Folder> item = new TreeItem<>(f);
                vaultRootItem.getChildren().add(item);
                String id = normalizeId(f.getId());
                if (buildResult.filterActive || buildResult.expandedIds.contains(id)) {
                    item.setExpanded(true);
                }
                buildFolderTree(item, id, buildResult.childrenByParent, comp, buildResult.expandedIds,
                        buildResult.filterActive);
            }
            if (buildResult.filterActive) {
                expandCollapseRecursive(vaultRootItem, true);
            }
            vaultRootItem.setExpanded(true);
            restoreFolderSelection(selectedFolderId);
        } finally {
            restoringFolderSelection = wasRestoringSelection;
        }
        recomputeRecursiveNoteCounts();
    }

    /** Re-selects the folder with {@code folderId} after a tree rebuild, without re-navigating. */
    private void restoreFolderSelection(String folderId) {
        if (folderId == null) {
            return;
        }
        TreeItem<Folder> match = findFolderItem(vaultRootItem, folderId);
        if (match == null) {
            return;
        }
        boolean wasRestoringSelection = restoringFolderSelection;
        restoringFolderSelection = true;
        try {
            folderTreeView.getSelectionModel().select(match);
            folderTreeView.scrollTo(folderTreeView.getRow(match));
        } finally {
            restoringFolderSelection = wasRestoringSelection;
        }
    }

    /** Depth-first search for the tree item backing the folder whose id matches {@code folderId}. */
    private TreeItem<Folder> findFolderItem(TreeItem<Folder> node, String folderId) {
        if (node == null) {
            return null;
        }
        Folder value = node.getValue();
        if (value != null && folderId.equals(value.getId())) {
            return node;
        }
        for (TreeItem<Folder> child : node.getChildren()) {
            TreeItem<Folder> found = findFolderItem(child, folderId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void buildFolderTree(TreeItem<Folder> parentItem, String parentId,
            Map<String, List<Folder>> childrenByParent, Comparator<Folder> comp,
            Set<String> expandedIds, boolean filterActive) {
        List<Folder> children = childrenByParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        List<Folder> sorted = new ArrayList<>(children);
        Collections.sort(sorted, comp);
        for (Folder child : sorted) {
            TreeItem<Folder> item = new TreeItem<>(child);
            parentItem.getChildren().add(item);
            String childId = child.getId() != null ? normalizeId(child.getId()) : "";
            if (filterActive || expandedIds.contains(childId)) {
                item.setExpanded(true);
            }
            buildFolderTree(item, childId, childrenByParent, comp, expandedIds, filterActive);
        }
    }

    private Set<String> collectExpandedFolderIds(TreeItem<Folder> node) {
        Set<String> expanded = new HashSet<>();
        if (node == null) {
            return expanded;
        }
        collectExpandedFolderIdsRec(node, expanded);
        return expanded;
    }

    private void collectExpandedFolderIdsRec(TreeItem<Folder> node, Set<String> expanded) {
        if (node == null) {
            return;
        }
        Folder value = node.getValue();
        if (value != null && value.getId() != null && node.isExpanded()) {
            expanded.add(normalizeId(value.getId()));
        }
        for (TreeItem<Folder> child : node.getChildren()) {
            collectExpandedFolderIdsRec(child, expanded);
        }
    }

    private String resolveParentId(Folder folder, Map<String, Folder> foldersById) {
        if (folder == null || folder.getId() == null) {
            return null;
        }

        if (folder.getParent() != null && folder.getParent().getId() != null
                && !folder.getParent().getId().isBlank()) {
            return normalizeId(folder.getParent().getId());
        }

        String id = normalizeId(folder.getId());
        int slash = id.lastIndexOf('/');
        if (slash > 0) {
            return id.substring(0, slash);
        }

        try {
            Optional<Folder> parent = folderService.getParentFolder(folder);
            if (parent.isPresent() && parent.get().getId() != null) {
                return normalizeId(parent.get().getId());
            }
        } catch (Exception e) {
            logger.fine("Parent resolution fallback failed for folder " + id + ": " + e.getMessage());
        }

        return null;
    }

    public void loadTrashTree() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::loadTrashTree);
            return;
        }
        if (folderService == null || noteService == null) {
            logger.warning("Cannot load trash tree: services are not initialized");
            return;
        }
        try {
            final String filter = filterTrashField.getText() == null ? ""
                    : filterTrashField.getText().toLowerCase().trim();
            final boolean filtering = !filter.isEmpty();
            final boolean sortAscending = trashSortAscending;
            final long requestId = trashLoadVersion.incrementAndGet();
            sidebarLoadExecutor.submit(() -> {
                try {
                    Folder trashRoot = folderService.getTrashFolders();
                    List<Note> allNotes = noteService.getTrashNotes();
                    Map<String, Folder> folderMap = new HashMap<>();
                    mapTrashFolders(trashRoot, folderMap);
                    List<Note> rootNotes = new ArrayList<>();
                    for (Note n : allNotes) {
                        String id = n.getId().replace("\\", "/");
                        String pId = null;
                        if (n.getParent() != null && n.getParent().getId() != null) {
                            pId = n.getParent().getId();
                        } else {
                            int i = id.lastIndexOf('/');
                            if (i != -1) {
                                pId = id.substring(0, i);
                            }
                        }
                        boolean added = false;
                        if (pId != null) {
                            String norm = pId.replace("\\", "/");
                            Folder p = folderMap.get(norm);
                            if (p == null) {
                                if (norm.equals(".trash") || norm.equals("trash")) {
                                    p = trashRoot;
                                } else if (norm.startsWith("trash/")) {
                                    p = folderMap.get("." + norm);
                                } else if (!norm.startsWith(".trash/") && !norm.startsWith(".")) {
                                    p = folderMap.get(".trash/" + norm);
                                }
                            }
                            if (p != null) {
                                p.add(n);
                                n.setParent(p);
                                added = true;
                            }
                        }
                        if (!added) {
                            rootNotes.add(n);
                        }
                    }
                    for (Note rn : rootNotes) {
                        trashRoot.add(rn);
                        rn.setParent(trashRoot);
                    }
                    Set<String> visibleIds = new HashSet<>();
                    if (filtering) {
                        buildTrashVisibleIdsRec(trashRoot, filter, visibleIds);
                    }
                    TrashTreeBuildResult result = new TrashTreeBuildResult(trashRoot, visibleIds, filtering, sortAscending);
                    Platform.runLater(() -> applyTrashTreeBuildResult(requestId, result));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to build trash tree", e);
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load trash", e);
        }
    }

    private void mapTrashFolders(Folder f, Map<String, Folder> map) {
        if (f.getId() != null)
            map.put(f.getId(), f);
        for (Component c : f.getChildren())
            if (c instanceof Folder)
                mapTrashFolders((Folder) c, map);
    }

    private boolean buildTrashVisibleIdsRec(Component c, String filter, Set<String> vIds) {
        boolean vis = (c.getTitle() != null && c.getTitle().toLowerCase().contains(filter));
        if (c instanceof Folder) {
            for (Component child : ((Folder) c).getChildren())
                if (buildTrashVisibleIdsRec(child, filter, vIds))
                    vis = true;
        }
        if (vis)
            vIds.add(c.getId());
        return vis;
    }

    private void buildTrashTreeRecursive(TreeItem<Component> parentItem, Set<String> vIds, boolean filtering,
            boolean sortAscending) {
        if (parentItem.getValue() instanceof Folder) {
            Folder f = (Folder) parentItem.getValue();
            List<Component> sorted = new ArrayList<>(f.getChildren());
            Comparator<Component> comparator = (c1, c2) -> {
                boolean f1 = c1 instanceof Folder;
                boolean f2 = c2 instanceof Folder;
                if (f1 && !f2)
                    return -1;
                if (!f1 && f2)
                    return 1;
                String t1 = c1.getTitle() == null ? "" : c1.getTitle().toLowerCase();
                String t2 = c2.getTitle() == null ? "" : c2.getTitle().toLowerCase();
                return t1.compareTo(t2);
            };
            if (!sortAscending) {
                comparator = comparator.reversed();
            }
            sorted.sort(comparator);
            for (Component c : sorted) {
                if (filtering && c.getId() != null && !vIds.contains(c.getId()))
                    continue;
                TreeItem<Component> item = new TreeItem<>(c);
                parentItem.getChildren().add(item);
                if (c instanceof Folder)
                    buildTrashTreeRecursive(item, vIds, filtering, sortAscending);
            }
        }
    }

    private void applyTrashTreeBuildResult(long requestId, TrashTreeBuildResult result) {
        if (requestId != trashLoadVersion.get() || trashTreeView == null || result == null) {
            return;
        }
        TreeItem<Component> rootItem = new TreeItem<>(result.trashRoot);
        buildTrashTreeRecursive(rootItem, result.visibleIds, result.filtering, result.sortAscending);
        if (result.filtering && !rootItem.getChildren().isEmpty()) {
            expandCollapseRecursive(rootItem, true);
        }
        trashTreeView.setRoot(rootItem);
        trashTreeView.setShowRoot(false);
    }

    public void loadTags() {
        if (tagService == null) {
            logger.warning("Cannot load tags: tagService is null");
            return;
        }
        try {
            List<Tag> tags = tagService.getAllTags();
            masterTagsList.clear();
            tagsByTitleCache.clear();
            for (Tag t : tags) {
                masterTagsList.add(t.getTitle());
                tagsByTitleCache.put(t.getTitle(), t);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load tags", e);
        }
    }

    public void loadRecentNotes() {
        loadRecentAndFavoritesAsync();
    }

    public void loadFavorites() {
        loadRecentAndFavoritesAsync();
    }

    private void loadRecentAndFavoritesAsync() {
        if (noteService == null) {
            logger.warning("Cannot load recent/favorites: noteService is null");
            return;
        }
        final long requestId = recentFavoritesLoadVersion.incrementAndGet();
        sidebarLoadExecutor.submit(() -> {
            try {
                List<Note> allNotes = noteService.getAllNotes();
                Platform.runLater(() -> {
                    if (requestId != recentFavoritesLoadVersion.get()) {
                        return;
                    }
                    applyRecentNotes(allNotes);
                    applyFavoriteNotes(allNotes);
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load recent/favorites", e);
            }
        });
    }

    private void applyRecentNotes(List<Note> allNotes) {
        cachedRecentNotes = new ArrayList<>(allNotes != null ? allNotes : List.of());
        cachedRecentNotes.sort((a, b) -> {
            String d1 = a.getModifiedDate() != null ? a.getModifiedDate()
                    : (a.getCreatedDate() != null ? a.getCreatedDate() : "");
            String d2 = b.getModifiedDate() != null ? b.getModifiedDate()
                    : (b.getCreatedDate() != null ? b.getCreatedDate() : "");
            return recentSortAscending ? d2.compareTo(d1) : d1.compareTo(d2);
        });
        // Replace atomically (setAll) instead of clear()+add in a loop: the latter makes
        // the bound ListView flash empty on every reload (e.g. after each autosave).
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < Math.min(10, cachedRecentNotes.size()); i++) {
            titles.add(cachedRecentNotes.get(i).getTitle());
        }
        masterRecentList.setAll(titles);
    }

    private void applyFavoriteNotes(List<Note> allNotes) {
        cachedFavoriteNotes = (allNotes != null ? allNotes : List.<Note>of()).stream().filter(Note::isFavorite).toList();
        // setAll (atomic) instead of clear()+add, so the bound ListView does not flash
        // empty on every reload (e.g. after each autosave).
        masterFavoritesList.setAll(cachedFavoriteNotes.stream().map(Note::getTitle).toList());
    }

    private void requestFoldersReload() {
        foldersReloadDebounce.playFromStart();
    }

    private void requestTrashReload() {
        trashReloadDebounce.playFromStart();
    }

    private void requestTagsReload() {
        tagsReloadDebounce.playFromStart();
    }

    private void requestRecentFavoritesReload() {
        recentFavoritesReloadDebounce.playFromStart();
    }

    @FXML
    public void handleSortFolders(ActionEvent e) {
        if (explorerMode && explorerTree != null) {
            showExplorerSortMenu();
            return;
        }
        folderSortAscending = !folderSortAscending;
        loadFolders();
    }

    @FXML
    public void handleExpandAllFolders(ActionEvent e) {
        if (explorerMode && explorerTree != null) {
            // Folders are read as they open, so this opens what is already loaded, one level more.
            explorerTree.getView().getRoot().getChildren().forEach(item -> expandCollapseRecursive(item, true));
            return;
        }
        expandCollapseRecursive(vaultRootItem, true);
    }

    @FXML
    public void handleCollapseAllFolders(ActionEvent e) {
        if (explorerMode && explorerTree != null) {
            explorerTree.getView().getRoot().getChildren().forEach(item -> expandCollapseRecursive(item, false));
            return;
        }
        expandCollapseRecursive(vaultRootItem, false);
    }

    @FXML
    public void handleSortTags(ActionEvent e) {
        tagSortAscending = !tagSortAscending;
        loadTags();
    }

    @FXML
    public void handleSortRecent(ActionEvent e) {
        recentSortAscending = !recentSortAscending;
        loadRecentNotes();
    }

    @FXML
    public void handleSortFavorites(ActionEvent e) {
        favoritesSortAscending = !favoritesSortAscending;
        loadFavorites();
    }

    @FXML
    public void handleSortTrash(ActionEvent e) {
        trashSortAscending = !trashSortAscending;
        loadTrashTree();
    }

    @FXML
    public void handleEmptyTrash(ActionEvent e) {
        if (noteService == null || folderService == null) {
            logger.warning("Cannot empty trash: services are not initialized");
            publishStatusUpdate(getString("status.error"));
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(getString("dialog.empty_trash.title"));
        alert.setHeaderText(getString("dialog.empty_trash.header"));
        alert.setContentText(getString("dialog.empty_trash.content"));
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        UiDialogs.show(alert).filter(r -> r == ButtonType.OK).ifPresent(r -> {
            try {
                emptyTrashNow();
                publishStatusUpdate(getString("status.trash_emptied"));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to empty trash", ex);
            }
        });
    }

    /**
     * Empties the trash, once confirmed. Each item is announced: the views that show notes
     * update, and this sidebar reloads its trees when it hears it.
     */
    void emptyTrashNow() {
        for (Component deleted : new TrashService(noteService, folderService).emptyTrash()) {
            publishEvent(new NoteEvents.TrashItemDeletedEvent(deleted));
        }
    }

    private void handleRestoreTrashItem() {
        TreeItem<Component> sel = trashTreeView.getSelectionModel().getSelectedItem();
        if (sel != null) {
            if (folderService == null || noteService == null) {
                logger.warning("Cannot restore trash item: services are not initialized");
                publishStatusUpdate(getString("status.error"));
                return;
            }
            try {
                Component c = sel.getValue();
                if (c instanceof Folder)
                    folderService.restoreFolder(c.getId());
                else if (c instanceof Note)
                    noteService.restoreNote(c.getId());
                loadFolders();
                loadTrashTree();
                publishStatusUpdate(getString("status.item_restored"));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to restore trash item", e);
            }
        }
    }

    private void handleDeleteTrashItem() {
        TreeItem<Component> sel = trashTreeView.getSelectionModel().getSelectedItem();
        if (sel != null) {
            Component c = sel.getValue();
            if (folderService == null || noteService == null) {
                logger.warning("Cannot permanently delete trash item: services are not initialized");
                publishStatusUpdate(getString("status.error"));
                return;
            }
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle(getString("dialog.delete_permanently.title"));
            a.setHeaderText(MessageFormat.format(getString("dialog.delete_permanently.header"), c.getTitle()));
            a.setContentText(getString("dialog.delete_permanently.content"));
            a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (UiDialogs.show(a).orElse(ButtonType.CANCEL) == ButtonType.OK) {
                try {
                    deleteFromTrashNow(c);
                    publishStatusUpdate(getString("status.item_deleted"));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to permanently delete trash item", e);
                }
            }
        }
    }

    /** Deletes one item of the trash for good, once confirmed, and announces it. */
    void deleteFromTrashNow(Component c) {
        if (c instanceof Folder)
            folderService.permanentlyDeleteFolder(c.getId());
        else if (c instanceof Note)
            noteService.permanentlyDeleteNote(c.getId());
        publishEvent(new NoteEvents.TrashItemDeletedEvent(c));
    }

    /** What the folder context menu entries do. */
    private FolderContextMenuSupport.Actions explorerFolderMenuActions() {
        return new FolderContextMenuSupport.Actions(
                this::handleCreateNoteInFolder,
                this::handleCreateCanvasInFolder,
                this::handleCreateSubfolderInFolder,
                this::renameInExplorer,
                this::handleMoveFolder,
                this::revealFolderInFileManager,
                this::handleDeleteFolder);
    }

    /** Puts a name field in the Explorer row of {@code item} (once it is shown, if it was just created). */
    public void renameInExplorer(Component item) {
        if (explorerTree != null) {
            explorerTree.startRename(item);
        }
    }

    /** What renaming a note in the Explorer row does (the notes list renames it). */
    public void setExplorerRenameNoteAction(BiConsumer<Note, String> action) {
        this.explorerRenameNoteAction = action != null ? action : (note, name) -> {
        };
    }

    private FolderContextMenuSupport.Actions folderMenuActions() {
        return new FolderContextMenuSupport.Actions(
                this::handleCreateNoteInFolder,
                this::handleCreateCanvasInFolder,
                this::handleCreateSubfolderInFolder,
                this::handleRenameFolder,
                this::handleMoveFolder,
                this::revealFolderInFileManager,
                this::handleDeleteFolder);
    }

    private void handleMoveFolder(Folder folder) {
        if (!FolderContextMenuSupport.canMove(folder) || folderService == null) {
            return;
        }
        Optional<MoveTargetSupport.MoveTarget> target = chooseMoveTarget(folder);
        if (target.isEmpty()) {
            return;
        }
        try {
            Folder destination = target.get().folder();
            folderService.moveFolderToFolder(folder, destination);
            invalidateFolderNoteCountCache();
            requestFoldersReload();
            requestRecentFavoritesReload();
            requestTrashReload();
            if (eventBus != null) {
                eventBus.publish(new FolderEvents.FoldersRefreshRequestedEvent());
                eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
            }
            publishStatusUpdate(destination == null || "ROOT".equals(destination.getId())
                    ? getString("status.folder_moved_root")
                    : MessageFormat.format(getString("status.folder_moved"), destination.getTitle()));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to move folder " + folder.getId(), e);
            publishStatusUpdate(getString("status.folder_move_error"));
        }
    }

    private Optional<MoveTargetSupport.MoveTarget> chooseMoveTarget(Folder folderToMove) {
        return MoveTargetSupport.choose(folderService, folderToMove, this::getString);
    }

    private boolean isFileSystemStorage() {
        Preferences prefs = Preferences.userNodeForPackage(SidebarController.class);
        return !"sqlite".equals(prefs.get("storage_type", "sqlite"));
    }

    private void revealFolderInFileManager(Folder folder) {
        if (folderService == null || folder == null) {
            return;
        }
        Optional<String> pathOpt = folderService.getStoragePath(folder);
        if (pathOpt.isEmpty()) {
            publishStatusUpdate(getString("status.reveal_unavailable"));
            return;
        }
        try {
            FileManager.open(Path.of(pathOpt.get()));
            publishStatusUpdate(getString("status.revealed"));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to reveal folder in file manager", ex);
            publishStatusUpdate(getString("status.reveal_error"));
        }
    }

    private void handleCreateNoteInFolder(Folder folder) {
        if (folder == null) {
            return;
        }
        folderSelectionAction.accept(folder);
        createNoteInFolderAction.accept(folder);
    }

    private void handleCreateCanvasInFolder(Folder folder) {
        if (folder == null) {
            return;
        }
        folderSelectionAction.accept(folder);
        createCanvasInFolderAction.accept(folder);
    }

    private void handleCreateSubfolderInFolder(Folder folder) {
        if (eventBus == null || folder == null) {
            return;
        }
        folderSelectionAction.accept(folder);
        eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.NEW_FOLDER));
    }

    private void handleRenameFolder(Folder f) {
        if (folderService == null) {
            logger.warning("Cannot rename folder: folderService is null");
            publishStatusUpdate(getString("status.error_renaming_folder"));
            return;
        }
        TextInputDialog d = new TextInputDialog(f.getTitle());
        d.setTitle(getString("dialog.rename_folder.title"));
        d.setHeaderText(getString("dialog.rename_folder.header"));
        d.setContentText(getString("dialog.rename_folder.content"));
        d.getDialogPane().setMinWidth(380);
        UiDialogs.show(d).ifPresent(name -> renameFolderTo(f, name));
    }

    /** Renames the folder to {@code name}; a blank or unchanged name does nothing. */
    private void renameFolderTo(Folder f, String name) {
            if (f == null || folderService == null || name == null || name.isBlank()
                    || name.strip().equals(f.getTitle())) {
                return;
            }
            try {
                String previousId = f.getId();
                Folder renamed = folderService.renameFolder(f, name);
                loadFolders();
                if (previousId != null && previousId.equals(activeFolderId)) {
                    activeFolderId = renamed.getId();
                    folderSelectionAction.accept(renamed);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to rename folder " + (f != null ? f.getId() : "null"), e);
                publishStatusUpdate(getString("status.error_renaming_folder"));
            }
    }

    private void handleDeleteFolder(Folder f) {
        if (folderService == null) {
            logger.warning("Cannot delete folder: folderService is null");
            publishStatusUpdate(getString("status.error_deleting_folder"));
            return;
        }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(getString("dialog.delete_folder.title"));
        a.setHeaderText(getString("dialog.delete_folder.header"));
        a.setContentText(getString("dialog.delete_folder.content"));
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        UiDialogs.show(a).filter(r -> r == ButtonType.OK).ifPresent(r -> {
            try {
                deleteFolderNow(f);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to delete folder " + (f != null ? f.getId() : "null"), e);
                publishStatusUpdate(getString("status.error_deleting_folder"));
            }
        });
    }

    /** Moves a folder to the trash, once confirmed, and announces it. */
    void deleteFolderNow(Folder f) {
        folderService.deleteFolder(f.getId());
        publishEvent(new FolderEvents.FolderDeletedEvent(f.getId(), f.getTitle()));
    }

    private ContextMenu createTagContextMenu(String tagName) {
        ContextMenu m = new ContextMenu();
        MenuItem d = new MenuItem(getString("action.delete"));
        d.setOnAction(e -> handleDeleteTag(tagName));
        m.getItems().add(d);
        return m;
    }

    private void handleDeleteTag(String tagName) {
        if (tagService == null) {
            logger.warning("Cannot delete tag: tagService is null");
            publishStatusUpdate(getString("status.error_deleting_tag"));
            return;
        }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(getString("dialog.delete_tag.title"));
        a.setHeaderText(getString("dialog.delete_tag.header"));
        a.setContentText(MessageFormat.format(getString("dialog.delete_tag.content"), tagName));
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        UiDialogs.show(a).filter(r -> r == ButtonType.OK).ifPresent(r -> {
            Optional.ofNullable(tagsByTitleCache.get(tagName)).ifPresent(t -> {
                try {
                    tagService.deleteTag(t.getId());
                    loadTags();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Failed to delete tag " + t.getId(), ex);
                    publishStatusUpdate(getString("status.error_deleting_tag"));
                }
            });
        });
    }

    private void expandCollapseRecursive(TreeItem<?> item, boolean expand) {
        if (item != null) {
            item.setExpanded(expand);
            for (TreeItem<?> child : item.getChildren())
                expandCollapseRecursive(child, expand);
        }
    }

    private int getNoteCountForFolder(Folder f) {
        try {
            if (f == null)
                return 0;
            String id = f.getId();
            if (id == null)
                return 0;
            return folderRecursiveNoteCountCache.getOrDefault(normalizeId(id), 0);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to count notes for folder " + (f != null ? f.getId() : "null"), e);
            return 0;
        }
    }

    private void rebuildFolderNoteCountCacheAsync() {
        if (noteService == null) {
            return;
        }
        final long requestId = noteCountBuildVersion.incrementAndGet();
        sidebarLoadExecutor.submit(() -> {
            try {
                List<Note> allNotes = noteService.getAllNotes();
                Map<String, Integer> freshCounts = new HashMap<>();
                for (Note note : allNotes) {
                    String folderId = resolveFolderIdForCount(note);
                    if (folderId == null || folderId.isBlank()) {
                        folderId = "ROOT";
                    }
                    freshCounts.merge(folderId, 1, Integer::sum);
                }
                Platform.runLater(() -> {
                    if (requestId != noteCountBuildVersion.get()) {
                        return;
                    }
                    folderNoteCountCache.clear();
                    folderNoteCountCache.putAll(freshCounts);
                    folderNoteCountCacheDirty = false;
                    recomputeRecursiveNoteCounts();
                    if (folderTreeView != null) {
                        folderTreeView.refresh();
                    }
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to rebuild folder note count cache", e);
            }
        });
    }

    /**
     * Walks the real folder tree bottom-up so every folder's displayed count is its own
     * direct notes plus every descendant subfolder's, recursively — not just what's
     * sitting directly inside it. Recomputed after either the note-count cache or the
     * tree's own shape changes (a rebuild can add/remove/move folders without any note
     * being touched), so it's called from both places rather than only one.
     */
    private void recomputeRecursiveNoteCounts() {
        folderRecursiveNoteCountCache.clear();
        if (vaultRootItem != null) {
            sumRecursiveNoteCount(vaultRootItem);
        }
        if (explorerTree != null) {
            explorerTree.refreshDecorations();
        }
    }

    private int sumRecursiveNoteCount(TreeItem<Folder> item) {
        Folder folder = item.getValue();
        String id = folder != null ? folder.getId() : null;
        int total = id != null ? folderNoteCountCache.getOrDefault(normalizeId(id), 0) : 0;
        for (TreeItem<Folder> child : item.getChildren()) {
            total += sumRecursiveNoteCount(child);
        }
        if (id != null) {
            folderRecursiveNoteCountCache.put(normalizeId(id), total);
        }
        return total;
    }

    private void invalidateFolderNoteCountCache() {
        folderNoteCountCacheDirty = true;
        noteCountRebuildDebounce.playFromStart();
    }

    private String resolveFolderIdForCount(Note note) {
        if (note == null) {
            return null;
        }
        if (note.getParent() != null && note.getParent().getId() != null && !note.getParent().getId().isBlank()) {
            return normalizeId(note.getParent().getId());
        }
        if (note.getId() == null || note.getId().isBlank()) {
            return null;
        }
        String normalized = normalizeId(note.getId());
        if (normalized.startsWith(".trash/")) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        if (slash <= 0) {
            return "ROOT";
        }
        return normalized.substring(0, slash);
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.replace("\\", "/");
    }

    private String getString(String k) {
        return bundle != null && bundle.containsKey(k) ? bundle.getString(k) : k;
    }

    private void publishStatusUpdate(String m) {
        if (m != null && !m.isBlank()) {
            statusUpdateAction.accept(m);
        }
    }

    private void publishEvent(AppEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    public VBox getSidebarPane() {
        return sidebarPane;
    }


    public TreeView<Folder> getFolderTreeView() {
        return folderTreeView;
    }





    /**
     * Keeps the custom sidebar nav bar in sync with {@link TabPane} selection.
     */
    public void applySidebarTabPresentation() {
        refreshSidebarNavSelection();
        refreshSidebarNavTooltips();
    }

    /** Preferences toggle: shows/hides the "(N)" note count next to every folder. */
    public void applyShowFolderNoteCountsPreference(boolean enabled) {
        if (showFolderNoteCounts == enabled) {
            return;
        }
        showFolderNoteCounts = enabled;
        if (folderTreeView != null) {
            folderTreeView.refresh();
        }
        if (explorerTree != null) {
            explorerTree.refreshDecorations();
        }
    }

    private void setupSidebarNavigation() {
        if (navigationTabPane == null) {
            return;
        }
        navigationTabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> refreshSidebarNavSelection());
        if (navigationTabPane.getSelectionModel().getSelectedItem() == null && foldersTab != null) {
            navigationTabPane.getSelectionModel().select(foldersTab);
        }
        refreshSidebarNavSelection();
        refreshSidebarNavTooltips();
    }

    private void refreshSidebarNavTooltips() {
        setNavTooltip(navFoldersBtn, "tab.folders");
        setNavTooltip(navTagsBtn, "tab.tags");
        setNavTooltip(navRecentBtn, "tab.recent");
        setNavTooltip(navFavoritesBtn, "tab.favorites");
        setNavTooltip(navTrashBtn, "tab.trash");
    }

    private void setNavTooltip(Button button, String key) {
        if (button != null) {
            button.setTooltip(new Tooltip(getString(key)));
        }
    }

    private void refreshSidebarNavSelection() {
        Tab selected = navigationTabPane != null ? navigationTabPane.getSelectionModel().getSelectedItem() : null;
        setNavSelected(navFoldersBtn, selected == foldersTab);
        setNavSelected(navTagsBtn, selected == tagsTab);
        setNavSelected(navRecentBtn, selected == recentTab);
        setNavSelected(navFavoritesBtn, selected == favoritesTab);
        setNavSelected(navTrashBtn, selected == trashTab);
    }

    private void setNavSelected(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        if (selected) {
            if (!button.getStyleClass().contains("sidebar-nav-btn-selected")) {
                button.getStyleClass().add("sidebar-nav-btn-selected");
            }
        } else {
            button.getStyleClass().remove("sidebar-nav-btn-selected");
        }
    }

    private void selectSidebarTab(Tab tab) {
        if (navigationTabPane == null || tab == null) {
            return;
        }
        navigationTabPane.getSelectionModel().select(tab);
        refreshSidebarNavSelection();
    }

    @FXML
    private void handleNavFolders() {
        selectSidebarTab(foldersTab);
    }

    @FXML
    private void handleNavTags() {
        selectSidebarTab(tagsTab);
    }

    @FXML
    private void handleNavRecent() {
        selectSidebarTab(recentTab);
    }

    @FXML
    private void handleNavFavorites() {
        selectSidebarTab(favoritesTab);
    }

    @FXML
    private void handleNavTrash() {
        selectSidebarTab(trashTab);
    }

}
