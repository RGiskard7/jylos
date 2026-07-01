package com.example.jylos.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.util.Duration;
import java.util.*;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.*;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Sidebar: folders, tags, recent, favorites, and trash.
 * Publishes selection events consumed by {@link MainController}.
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
    private TreeItem<Folder> allNotesItem;
    private boolean folderSortAscending = true;
    private final Map<String, Integer> folderNoteCountCache = new HashMap<>();
    private int allNotesCountCache = 0;
    private boolean folderNoteCountCacheDirty = true;
    /** Guards the selection listener while a tree rebuild restores the prior selection. */
    private boolean restoringFolderSelection = false;

    // Tags
    @FXML
    private ListView<String> tagListView;
    @FXML
    private TextField filterTagsField;
    private final javafx.collections.ObservableList<String> masterTagsList = javafx.collections.FXCollections
            .observableArrayList();
    private final Map<String, Tag> tagsByTitleCache = new HashMap<>();
    private boolean tagSortAscending = true;

    // Recent
    @FXML
    private ListView<String> recentNotesListView;
    @FXML
    private TextField filterRecentField;
    private final javafx.collections.ObservableList<String> masterRecentList = javafx.collections.FXCollections
            .observableArrayList();
    private boolean recentSortAscending = true;
    private List<Note> cachedRecentNotes = new ArrayList<>();

    // Favorites
    @FXML
    private ListView<String> favoritesListView;
    @FXML
    private TextField filterFavoritesField;
    private final javafx.collections.ObservableList<String> masterFavoritesList = javafx.collections.FXCollections
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
    private FolderDAO folderDAO;
    private NoteDAO noteDAO;
    private EventBus eventBus;
    private ResourceBundle bundle;
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

    // Setters for MainController
    public void wire(EventBus eventBus, NoteService noteService, TagService tagService,
            FolderService folderService, FolderDAO folderDAO, NoteDAO noteDAO, ResourceBundle bundle) {
        setNoteService(noteService);
        setTagService(tagService);
        setFolderService(folderService);
        setFolderDAO(folderDAO);
        setNoteDAO(noteDAO);
        setBundle(bundle);
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

    private void setFolderDAO(FolderDAO fd) {
        this.folderDAO = fd;
    }

    private void setNoteDAO(NoteDAO nd) {
        this.noteDAO = nd;
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

        // Selection listeners - Matching MainController patterns
        folderTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Suppressed while we re-select the previously-selected folder after a tree
            // rebuild, so restoring the selection does not re-navigate / reload notes.
            if (restoringFolderSelection) {
                return;
            }
            if (newVal != null && newVal.getValue() != null) {
                Folder f = newVal.getValue();
                if ("ALL_NOTES_VIRTUAL".equals(f.getId())) {
                    publishEvent(new FolderEvents.FolderSelectedEvent(f));
                } else if (!"INVISIBLE_ROOT".equals(f.getTitle())) {
                    publishEvent(new FolderEvents.FolderSelectedEvent(f));
                }
            }
        });

        tagListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Tag selected = tagsByTitleCache.get(newVal);
                if (selected != null) {
                    publishEvent(new TagEvents.TagSelectedEvent(selected));
                }
            }
        });

        recentNotesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                cachedRecentNotes.stream().filter(n -> n.getTitle().equals(newVal)).findFirst().ifPresent(n -> {
                    publishEvent(new NoteEvents.NoteOpenRequestEvent(n));
                });
            }
        });

        favoritesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                cachedFavoriteNotes.stream().filter(n -> n.getTitle().equals(newVal)).findFirst().ifPresent(n -> {
                    publishEvent(new NoteEvents.NoteOpenRequestEvent(n));
                });
            }
        });

        trashTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                publishEvent(new NoteEvents.TrashItemSelectedEvent(newVal.getValue()));
            }
        });

        foldersFilterDebounce.setOnFinished(e -> loadFolders());
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

        // All Notes Virtual Folder
        Folder allNotesFolder = new Folder(getString("app.all_notes"), null, null);
        allNotesFolder.setId("ALL_NOTES_VIRTUAL");
        allNotesItem = new TreeItem<>(allNotesFolder);
        folderTreeView.getRoot().getChildren().add(allNotesItem);

        // Vault Root Folder logic literal
        String vaultName = "My Vault";
        try {
            Preferences prefs = Preferences.userNodeForPackage(MainController.class);
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
        if (allNotesItem != null && allNotesItem.getValue() != null) {
            allNotesItem.getValue().setTitle(getString("app.all_notes"));
        }
        if (vaultRootItem != null && vaultRootItem.getValue() != null) {
            try {
                Preferences prefs = Preferences.userNodeForPackage(MainController.class);
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
            private final ContextMenu folderContextMenu = createFolderContextMenuForCell(this);

            @Override
            protected void updateItem(Folder folder, boolean empty) {
                super.updateItem(folder, empty);
                if (empty || folder == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    Label iconLabel = new Label("");
                    iconLabel.getStyleClass().setAll("folder-cell-icon");
                    boolean isAllNotes = "ALL_NOTES_VIRTUAL".equals(folder.getId());
                    if (isAllNotes) {
                        iconLabel.setText("[=]");
                        iconLabel.getStyleClass().add("folder-all-notes");
                    } else {
                        TreeItem<Folder> ti = getTreeItem();
                        boolean isExp = ti != null && ti.isExpanded();
                        iconLabel.setText(isExp ? "[/]" : "[+]");
                        iconLabel.getStyleClass().add(isExp ? "folder-expanded" : "folder-collapsed");
                    }
                    int count = 0;
                    try {
                        if (isAllNotes)
                            count = allNotesCountCache;
                        else
                            count = getNoteCountForFolder(folder);
                    } catch (Exception e) {
                        logger.warning("Failed to compute folder note count for "
                                + (folder != null ? folder.getId() : "null") + ": " + e.getMessage());
                    }
                    HBox container = new HBox(6);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Label nameLabel = new Label(folder.getTitle());
                    nameLabel.getStyleClass().add("folder-cell-name");
                    if (count > 0 || isAllNotes) {
                        Label countLabel = new Label("(" + count + ")");
                        countLabel.getStyleClass().add("folder-cell-count");
                        container.getChildren().addAll(iconLabel, nameLabel, countLabel);
                    } else {
                        container.getChildren().addAll(iconLabel, nameLabel);
                    }
                    setGraphic(container);
                    setText(null);
                    if (!isAllNotes)
                        setContextMenu(folderContextMenu);
                    else
                        setContextMenu(null);

                    setupFolderDragSource(this, folder);
                    setupFolderDropTargets(this, folder);
                }
            }
        });

        trashTreeView.setCellFactory(tv -> new TreeCell<Component>() {
            @Override
            protected void updateItem(Component item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(6);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    if (item instanceof Folder) {
                        Label iconLabel = new Label("");
                        iconLabel.getStyleClass().setAll("folder-cell-icon");
                        TreeItem<Component> ti = getTreeItem();
                        boolean isExp = ti != null && ti.isExpanded();
                        iconLabel.setText(isExp ? "[/]" : "[+]");
                        iconLabel.getStyleClass().add(isExp ? "folder-expanded" : "folder-collapsed");
                        String title = item.getTitle();
                        if (title != null && title.equals(".trash"))
                            title = getString("tab.trash");
                        else if (title != null) {
                            int idx = title.lastIndexOf('/');
                            if (idx != -1)
                                title = title.substring(idx + 1);
                        }
                        Label nameLabel = new Label(title);
                        nameLabel.getStyleClass().add("folder-cell-name");
                        container.getChildren().addAll(iconLabel, nameLabel);
                    } else {
                        FontIcon noteIcon = new FontIcon("fth-file-text");
                        noteIcon.getStyleClass().add("feather-icon");
                        Label nameLabel = new Label(item.getTitle());
                        nameLabel.getStyleClass().add("folder-cell-name");
                        container.getChildren().addAll(noteIcon, nameLabel);
                    }
                    setGraphic(container);
                    setText(null);
                }
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

    private void setupFolderDragSource(TreeCell<Folder> cell, Folder folder) {
        if (folder == null || folder.getId() == null) {
            return;
        }
        String folderId = normalizeId(folder.getId());
        if ("ALL_NOTES_VIRTUAL".equals(folderId) || "ROOT".equals(folderId)) {
            return;
        }
        cell.setOnDragDetected(event -> {
            if (cell.isEmpty()) {
                return;
            }
            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("folder:" + folderId);
            db.setContent(content);
            event.consume();
        });
    }

    private void setupFolderDropTargets(TreeCell<Folder> cell, Folder targetFolder) {
        cell.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (!db.hasString()) {
                return;
            }
            String payload = db.getString();
            if (canAcceptDropPayload(payload, targetFolder)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        cell.setOnDragEntered(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString() && canAcceptDropPayload(db.getString(), targetFolder)) {
                if (!cell.getStyleClass().contains("drag-over-target")) {
                    cell.getStyleClass().add("drag-over-target");
                }
            }
            event.consume();
        });

        cell.setOnDragExited(event -> {
            cell.getStyleClass().remove("drag-over-target");
            event.consume();
        });

        cell.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                success = handleDroppedPayload(db.getString(), targetFolder);
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private boolean canAcceptDropPayload(String payload, Folder targetFolder) {
        if (payload == null || targetFolder == null || targetFolder.getId() == null) {
            return false;
        }
        String targetId = normalizeId(targetFolder.getId());
        if ("ALL_NOTES_VIRTUAL".equals(targetId) || "INVISIBLE_ROOT".equals(targetId)) {
            return false;
        }
        if (payload.startsWith("note:")) {
            return noteService != null && folderService != null;
        }
        if (payload.startsWith("folder:")) {
            String sourceFolderId = normalizeId(payload.substring("folder:".length()));
            if (sourceFolderId.isBlank() || sourceFolderId.equals(targetId)) {
                return false;
            }
            if (targetId.startsWith(sourceFolderId + "/")) {
                return false;
            }
            if (folderService == null) {
                return false;
            }
            Optional<Folder> source = folderService.getFolderById(sourceFolderId);
            return source.isPresent() && folderService.canMoveFolder(source.get(), targetFolder);
        }
        return false;
    }

    private boolean handleDroppedPayload(String payload, Folder targetFolder) {
        if (!canAcceptDropPayload(payload, targetFolder)) {
            return false;
        }
        try {
            if (payload.startsWith("note:")) {
                String noteId = payload.substring("note:".length());
                Optional<Note> noteOpt = noteService.getNoteById(noteId);
                if (noteOpt.isEmpty()) {
                    return false;
                }
                folderService.moveNoteToFolder(noteOpt.get(), targetFolder);
                publishStatusUpdate(java.text.MessageFormat.format(getString("status.note_moved_folder"), targetFolder.getTitle()));
            } else if (payload.startsWith("folder:")) {
                String folderId = payload.substring("folder:".length());
                Optional<Folder> folderOpt = folderService.getFolderById(folderId);
                if (folderOpt.isEmpty()) {
                    return false;
                }
                folderService.moveFolderToFolder(folderOpt.get(), targetFolder);
                publishStatusUpdate(java.text.MessageFormat.format(getString("status.folder_moved"), targetFolder.getTitle()));
            } else {
                return false;
            }
            invalidateFolderNoteCountCache();
            requestFoldersReload();
            requestRecentFavoritesReload();
            requestTrashReload();
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to process drag and drop payload: " + payload, e);
            publishStatusUpdate(getString("status.error"));
            return false;
        }
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
        }));
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, event -> {
            invalidateFolderNoteCountCache();
            requestFoldersReload();
            requestRecentFavoritesReload();
        }));
        eventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteSavedEvent.class, event -> {
            // A content save does not change the folder structure or note counts, so the
            // folder tree is NOT rebuilt here (rebuilding re-selected the folder and fired
            // a second, racing notes-list load that could blank the panel). Only the
            // recents/favorites lists, whose ordering may change, are refreshed.
            requestRecentFavoritesReload();
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

    private void setupFilteredList(ListView<String> listView, javafx.collections.ObservableList<String> masterList,
            TextField filterField) {
        javafx.collections.transformation.FilteredList<String> filteredList = new javafx.collections.transformation.FilteredList<>(
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
        Comparator<Folder> comp = (f1, f2) -> f1.getTitle().compareToIgnoreCase(f2.getTitle());
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
        Comparator<Folder> comp = (f1, f2) -> f1.getTitle().compareToIgnoreCase(f2.getTitle());
        if (!folderSortAscending) {
            comp = comp.reversed();
        }
        // Remember the selected folder so a rebuild (e.g. after a vault refresh) does not
        // drop the selection and scroll the tree back to the root.
        TreeItem<Folder> previous = folderTreeView.getSelectionModel().getSelectedItem();
        String selectedFolderId = previous != null && previous.getValue() != null
                ? previous.getValue().getId() : null;

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
        restoringFolderSelection = true;
        try {
            folderTreeView.getSelectionModel().select(match);
            folderTreeView.scrollTo(folderTreeView.getRow(match));
        } finally {
            restoringFolderSelection = false;
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
        folderSortAscending = !folderSortAscending;
        loadFolders();
    }

    @FXML
    public void handleExpandAllFolders(ActionEvent e) {
        expandCollapseRecursive(vaultRootItem, true);
    }

    @FXML
    public void handleCollapseAllFolders(ActionEvent e) {
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
        alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            try {
                for (Note n : noteService.getTrashNotes())
                    noteService.permanentlyDeleteNote(n.getId());
                Folder root = folderService.getTrashFolders();
                if (root != null)
                    for (Component c : root.getChildren())
                        if (c instanceof Folder)
                            folderService.permanentlyDeleteFolder(c.getId());
                loadTrashTree();
                publishStatusUpdate(getString("status.trash_emptied"));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to empty trash", ex);
            }
        });
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
                if (folderDAO != null)
                    folderDAO.refreshCache();
                if (noteDAO != null)
                    noteDAO.refreshCache();
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
            a.setHeaderText(java.text.MessageFormat.format(getString("dialog.delete_permanently.header"), c.getTitle()));
            a.setContentText(getString("dialog.delete_permanently.content"));
            a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                try {
                    if (c instanceof Folder)
                        folderService.permanentlyDeleteFolder(c.getId());
                    else if (c instanceof Note)
                        noteService.permanentlyDeleteNote(c.getId());
                    loadTrashTree();
                    publishStatusUpdate(getString("status.item_deleted"));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to permanently delete trash item", e);
                }
            }
        }
    }

    private ContextMenu createFolderContextMenuForCell(TreeCell<Folder> cell) {
        ContextMenu m = new ContextMenu();
        MenuItem newNote = new MenuItem(getString("action.new_note"));
        newNote.setOnAction(e -> {
            Folder f = cell != null ? cell.getItem() : null;
            if (f != null) {
                handleCreateNoteInFolder(f);
            }
        });

        MenuItem newSubfolder = new MenuItem(getString("action.new_subfolder"));
        newSubfolder.setOnAction(e -> {
            Folder f = cell != null ? cell.getItem() : null;
            if (f != null) {
                handleCreateSubfolderInFolder(f);
            }
        });

        MenuItem r = new MenuItem(getString("action.rename"));
        r.setOnAction(e -> {
            Folder f = cell != null ? cell.getItem() : null;
            if (f != null) {
                handleRenameFolder(f);
            }
        });
        MenuItem d = new MenuItem(getString("action.delete"));
        d.setOnAction(e -> {
            Folder f = cell != null ? cell.getItem() : null;
            if (f != null) {
                handleDeleteFolder(f);
            }
        });
        m.getItems().addAll(newNote, newSubfolder, new SeparatorMenuItem(), r, d);
        return m;
    }

    private void handleCreateNoteInFolder(Folder folder) {
        if (eventBus == null || folder == null) {
            return;
        }
        eventBus.publish(new FolderEvents.FolderSelectedEvent(folder));
        eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.NEW_NOTE));
    }

    private void handleCreateSubfolderInFolder(Folder folder) {
        if (eventBus == null || folder == null) {
            return;
        }
        eventBus.publish(new FolderEvents.FolderSelectedEvent(folder));
        eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.NEW_FOLDER));
    }

    private void handleRenameFolder(Folder f) {
        if (folderService == null) {
            logger.warning("Cannot rename folder: folderService is null");
            publishStatusUpdate(getString("status.error_renaming_folder"));
            return;
        }
        TextInputDialog d = new TextInputDialog(f.getTitle());
        d.showAndWait().ifPresent(name -> {
            try {
                folderService.renameFolder(f, name);
                loadFolders();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to rename folder " + (f != null ? f.getId() : "null"), e);
                publishStatusUpdate(getString("status.error_renaming_folder"));
            }
        });
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
        a.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            try {
                folderService.deleteFolder(f.getId());
                loadFolders();
                loadTrashTree();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to delete folder " + (f != null ? f.getId() : "null"), e);
                publishStatusUpdate(getString("status.error_deleting_folder"));
            }
        });
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
        a.setContentText(java.text.MessageFormat.format(getString("dialog.delete_tag.content"), tagName));
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        a.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
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
            if (id == null || "ALL_NOTES_VIRTUAL".equals(id))
                return allNotesCountCache;
            return folderNoteCountCache.getOrDefault(normalizeId(id), 0);
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
                final int allNotesCount = allNotes.size();
                Platform.runLater(() -> {
                    if (requestId != noteCountBuildVersion.get()) {
                        return;
                    }
                    folderNoteCountCache.clear();
                    folderNoteCountCache.putAll(freshCounts);
                    allNotesCountCache = allNotesCount;
                    folderNoteCountCacheDirty = false;
                    if (folderTreeView != null) {
                        folderTreeView.refresh();
                    }
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to rebuild folder note count cache", e);
            }
        });
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
        if (eventBus != null)
            eventBus.publish(new UIEvents.StatusUpdateEvent(m));
    }

    private void publishEvent(AppEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    public VBox getSidebarPane() {
        return sidebarPane;
    }

    public TabPane getNavigationTabPane() {
        return navigationTabPane;
    }

    public TreeView<Folder> getFolderTreeView() {
        return folderTreeView;
    }

    public TreeView<Component> getTrashTreeView() {
        return trashTreeView;
    }

    public TextField getFilterFoldersField() {
        return filterFoldersField;
    }

    public ListView<String> getTagListView() {
        return tagListView;
    }

    public TextField getFilterTrashField() {
        return filterTrashField;
    }

    /**
     * Keeps the custom sidebar nav bar in sync with {@link TabPane} selection.
     */
    public void applySidebarTabPresentation() {
        refreshSidebarNavSelection();
        refreshSidebarNavTooltips();
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
