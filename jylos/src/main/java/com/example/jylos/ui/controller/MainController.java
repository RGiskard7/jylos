package com.example.jylos.ui.controller;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.interfaces.FactoryDAO;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.data.database.SQLiteDB;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.plugin.PluginMenuRegistry;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.plugin.PreviewEnhancerRegistry;
import com.example.jylos.plugin.SidePanelRegistry;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.components.CommandPalette;
import com.example.jylos.ui.components.PluginManagerDialog;
import com.example.jylos.ui.components.QuickSwitcher;
import com.example.jylos.ui.preferences.UiPreferencesStore;
import com.example.jylos.ui.theme.CssSnippetCatalog;
import com.example.jylos.ui.theme.SystemThemeMonitor;
import com.example.jylos.ui.theme.ThemeCatalog;
import com.example.jylos.ui.theme.ThemeCommand;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Root FXML controller: coordinates sidebar, notes list, editor, plugins, themes,
 * and {@link com.example.jylos.event.EventBus} subscriptions. Business logic
 * for persistence is delegated to DAOs and {@link com.example.jylos.service.NoteService}
 * where possible; this class focuses on UI state and command routing.
 */
public class MainController implements PluginMenuRegistry, SidePanelRegistry, PreviewEnhancerRegistry,
        com.example.jylos.plugin.ToolbarRegistry {

    private static final Logger logger = LoggerConfig.getLogger(MainController.class);

    private Connection connection;
    private FolderDAO folderDAO;
    private NoteDAO noteDAO;
    private TagDAO tagDAO;

    private Folder currentFolder;

    private Note getCurrentNote() {
        return editorController != null ? editorController.getCurrentNote() : null;
    }

    private boolean isModified() {
        return editorController != null && editorController.isModified();
    }

    private String currentFilterType = "all"; // "all", "folder", "tag", "favorites", "search"
    private Tag currentTag = null;

    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private SplitPane contentSplitPane;

    private SplitPane navSplitPane;

    @FXML
    private SidebarController sidebarController;
    @FXML
    private NotesListController notesListController;
    @FXML
    private EditorController editorController;
    @FXML
    private ToolbarController toolbarController;
    @FXML
    private VBox graphView;
    @FXML
    private GraphController graphViewController;

    /** Open-note tab strip (one tab per open note). Driven by this controller. */
    private com.example.jylos.ui.components.EditorTabs editorTabs;

    private VBox notesPanel;
    private ComboBox<String> sortComboBox;
    private ListView<Note> notesListView;

    private boolean isStackedLayout = false;
    private final CommandRouting commandRouting = new CommandRouting();
    private final Map<SystemActionEvent.ActionType, Runnable> systemActionHandlers = new EnumMap<>(
            SystemActionEvent.ActionType.class);
    @FXML
    private SplitPane editorRightSplitPane;
    @FXML
    private VBox rightPanel;
    @FXML
    private javafx.scene.layout.HBox statusBar;
    @FXML
    private javafx.scene.layout.StackPane centerStack;

    private final FocusModeSupport focusModeSupport = new FocusModeSupport();
    @FXML
    private VBox rightPanelContent;
    @FXML
    private VBox noteInfoSection;
    @FXML
    private HBox noteInfoHeader;
    @FXML
    private Label noteInfoCollapseIcon;
    @FXML
    private VBox noteInfoContent;

    @FXML
    private VBox backlinksSection;
    @FXML
    private HBox backlinksHeader;
    @FXML
    private Label backlinksCollapseIcon;
    @FXML
    private VBox backlinksContent;

    @FXML
    private Label infoCreatedLabel;
    @FXML
    private Label infoModifiedLabel;
    @FXML
    private Label infoLatitudeLabel;
    @FXML
    private Label infoLongitudeLabel;

    private ToggleGroup themeToggleGroup;
    @FXML
    private Label infoAuthorLabel;
    @FXML
    private Label infoSourceUrlLabel;

    @FXML
    private Label statusLabel;
    @FXML
    private Label noteCountLabel;
    @FXML
    private Label storageLabel;
    @FXML
    private Label wordCountLabel;
    @FXML
    private Label charCountLabel;
    @FXML
    private Separator statsSeparator;
    @FXML
    private Separator wordCharSeparator;
    @FXML
    private Separator gitSeparator;
    @FXML
    private HBox gitBar;
    @FXML
    private Label gitInitLabel;
    @FXML
    private Label gitRemoteLabel;
    @FXML
    private Label gitChangesLabel;
    @FXML
    private Label gitCommitLabel;
    @FXML
    private Label gitSyncLabel;
    @FXML
    private Label gitHistoryLabel;

    private final GitController gitController = new GitController();
    private final WorkspaceController workspaceController = new WorkspaceController();
    private final PrivacySupport privacySupport = new PrivacySupport();
    private final OverlaySupport overlaySupport = new OverlaySupport();
    private final StatusBarSupport statusBarSupport = new StatusBarSupport();
    private final BacklinksSupport backlinksSupport = new BacklinksSupport();
    /** Editor hook dispatcher shared by the plugin system and the editor. */
    private final com.example.jylos.plugin.EditorHooks editorHooks = new com.example.jylos.plugin.EditorHooks();
    private final ImportSupport importSupport = new ImportSupport();
    private final HistorySupport historySupport = new HistorySupport();
    /** pluginId → toolbar buttons contributed via {@link com.example.jylos.plugin.ToolbarRegistry}. */
    private final Map<String, List<javafx.scene.Node>> pluginToolbarButtons = new HashMap<>();

    private final Map<String, Menu> pluginCategoryMenus = new HashMap<>();
    private final Map<String, List<MenuItem>> pluginMenuItems = new HashMap<>();

    @FXML
    private VBox pluginPanelsContainer;
    private final Map<String, VBox> pluginPanels = new HashMap<>();
    private final Map<String, List<String>> pluginPanelIds = new HashMap<>();

    @FXML
    private HBox pluginStatusBarContainer;
    private final Map<String, javafx.scene.Node> pluginStatusBarItems = new HashMap<>();
    private final Map<String, List<String>> pluginStatusBarItemIds = new HashMap<>();

    private UiLayout.ViewMode currentViewMode = UiLayout.ViewMode.SPLIT;


    private CommandPalette commandPalette;
    private QuickSwitcher quickSwitcher;

    private NoteService noteService;
    private FolderService folderService;
    private TagService tagService;
    private com.example.jylos.service.BacklinkService backlinkService;
    private EventBus eventBus;
    private PluginManager pluginManager;
    private NoteOperations noteOperations;
    private PluginManagerDialog pluginManagerDialog;

    private final TagManagement tagManagement = new TagManagement();
    
    private final PluginLifecycle pluginLifecycle = new PluginLifecycle();
    private final CommandUI commandUI = new CommandUI();
    private final DocumentSupport documentSupport = new DocumentSupport();
    private final DocumentWorkflowSupport documentWorkflowSupport = new DocumentWorkflowSupport();
    private final NoteCreationSupport noteCreationSupport = new NoteCreationSupport();
    private final UiInitialization uiInitialization = new UiInitialization();
    private final UiLayout uiLayout = new UiLayout();
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private final FolderOperations folderOperations = new FolderOperations();

    private final NavigationCommand navigationCommand = new NavigationCommand();
    private final DialogSupport dialogSupport = new DialogSupport();
    private final ThemeCommand themeCommand = new ThemeCommand();
    private final ThemeCatalog themeCatalog = new ThemeCatalog();
    private final CssSnippetCatalog cssSnippetCatalog = new CssSnippetCatalog();
    private final SystemThemeMonitor systemThemeMonitor = new SystemThemeMonitor(
            () -> themeCommand.detectSystemTheme(),
            this::applyThemeAndRefreshDependents);
    private final UiPreferencesStore uiPreferences = new UiPreferencesStore();
    private final PluginUi pluginUi = new PluginUi();
    private final AppSettings appSettings = new AppSettings();
    private final List<EventBus.Subscription> uiEventSubscriptions = new ArrayList<>();
    private EventBus.Subscription systemActionSubscription = EventBus.Subscription.NO_OP;
    private EventBus.Subscription pluginUiRefreshSubscription = EventBus.Subscription.NO_OP;

    @FXML
    private java.util.ResourceBundle resources;

    private double uiFontSize = 13.0;
    /** Custom accent ({@code #rrggbb}) or "" for the theme default. */
    private String uiAccentColor = "";
    private double editorFontSize = 14.0;
    private final PauseTransition noteModifiedDebounce = new PauseTransition(Duration.millis(250));
    private final PauseTransition toolbarSearchDebounce = new PauseTransition(Duration.millis(180));
    private final PauseTransition autosaveDebounce = new PauseTransition(
            Duration.millis(UiPreferencesStore.DEFAULT_AUTOSAVE_IDLE_MS));
    private String pendingModifiedNoteId;
    private String pendingSearchText = "";
    private boolean searchListenerBound = false;

    // ── Navigation history (back / forward arrows) ──────────────────────
    /** IDs of previously visited notes, most-recent last. */
    private final Deque<String> navBackStack    = new ArrayDeque<>();
    /** IDs of notes skipped via back, most-recent last. */
    private final Deque<String> navForwardStack = new ArrayDeque<>();
    /** Suppresses history recording during programmatic back/forward jumps. */
    private boolean navJumping = false;
    private static final int NAV_MAX_HISTORY = 50;
    private final ExecutorService quickSwitcherExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jylos-quick-switcher-loader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong quickSwitcherLoadVersion = new AtomicLong(0);
    private final AtomicLong backendSessionGeneration = new AtomicLong(0);
    private volatile List<Note> quickSwitcherNotesCache = List.of();
    private volatile long activeBackendSessionGeneration = 0L;
    private int notesListPreviewLines = UiPreferencesStore.DEFAULT_NOTES_PREVIEW_LINES;
    private boolean autosaveEnabled = true;
    private int autosaveIdleMs = UiPreferencesStore.DEFAULT_AUTOSAVE_IDLE_MS;
    private boolean autosaveRunning = false;
    private String themeSource = UiPreferencesStore.THEME_SOURCE_BUILTIN;
    private String externalThemeId = "";
    /** Filenames of the CSS snippets the user has enabled; layered over the theme. */
    private java.util.Set<String> enabledSnippets = java.util.Set.of();

    private enum SaveDialogDecision {
        SAVE,
        DONT_SAVE,
        CANCEL
    }

    String getString(String key) {
        if (resources != null && resources.containsKey(key)) {
            return resources.getString(key);
        }
        return key; // Fallback to key if not found
    }


    /** Initializes the entire UI: wires sub-controllers, loads data, registers keyboard shortcuts, and starts the vault content callback. */
    @FXML
    public void initialize() {
        try {
            configureNoteModifiedDebounce();
            configureToolbarSearchDebounce();
            configureAutosaveDebounce();
            navSplitPane = new SplitPane();
            navSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);

            initializeDatabase();
            appSettings.wire(this::getString);
            pluginUi.wire(this::getString, this::updateStatus, this::getPluginManager);
            uiInitialization.wire(this::getString, new UiInitialization.OverflowActions(
                    () -> handleNewNote(null),
                    () -> handleNewCanvas(null),
                    () -> handleNewFolder(null),
                    () -> handleNewTag(null),
                    () -> handleSave(null),
                    () -> handleDelete(null),
                    () -> handleToggleSidebar(null),
                    () -> handleToggleNotesPanel(null),
                    () -> handleToggleRightPanel(null),
                    () -> handleViewLayoutSwitch(null)));

            if (toolbarController != null) {
                toolbarController.wire(eventBus, this::showCommandPalette, this::showQuickSwitcher,
                        this::showKeyboardShortcutsHelp,
                        () -> handleLightTheme(null),
                        () -> handleDarkTheme(null),
                        () -> handleSystemTheme(null),
                        () -> handleToggleRightPanel(null));
            }
            initializeCommandRouting();
            initializeSystemActionHandlers();
            if (eventBus != null) {
                systemActionSubscription.cancel();
                systemActionSubscription = eventBus.subscribe(SystemActionEvent.class, this::handleSystemAction);
                subscribeToShellAndDomainEvents();
            }
            bindBackendSession();

            bindToolbarSearchFieldDebounced();

            initializeSortOptions();
            initializeViewModeButtons();
            initializeRightPanelSections();
            initializeRightPanelVisibility();
            setupToolbarResponsiveness();
            initializeLanguageMenu();
            applyUiPreferencesFromStore();
            initializeThemeMenu();
            installSystemThemeFocusRefresh();
            setupSplitPanePersistence();

            refreshBackendSessionViews(false);

            Platform.runLater(this::initializeKeyboardShortcuts);

            Platform.runLater(this::initializePluginSystem);
            focusModeSupport.wire(mainSplitPane, contentSplitPane,
                    () -> toolbarController != null ? toolbarController.getToolbarHBox() : null,
                    statusBar, rightPanel,
                    () -> editorController != null ? editorController.getEditorContainer() : null,
                    prefs, this::getString, this::updateStatus);
            updateStatus(getString("status.ready"));
            logger.info("MainController initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize MainController", e);
            updateStatus(java.text.MessageFormat.format(getString("status.error_details"), e.getMessage()));
        }
    }
    /** Selects and opens the configured storage backend (SQLite or filesystem), then builds all DAOs and services. */
    private void initializeDatabase() {
        try {

            String storageType = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));

            FactoryDAO factory;
            if ("filesystem".equalsIgnoreCase(storageType)) {
                String customPath = prefs.get("filesystem_path", "");
                String dataDir;
                if (customPath != null && !customPath.isEmpty() && new File(customPath).exists()) {
                    dataDir = customPath;
                    logger.info("Using Custom File System Storage at " + dataDir);
                } else {
                    dataDir = com.example.jylos.AppDataDirectory.getDataDirectory();
                    logger.info("Using Default File System Storage at " + dataDir);
                }

                factory = FactoryDAO.getFactory(FactoryDAO.FILE_SYSTEM_FACTORY, dataDir);
            } else {
                SQLiteDB db = SQLiteDB.getInstance();
                connection = db.openConnection();
                factory = FactoryDAO.getFactory(FactoryDAO.SQLITE_FACTORY, connection);
                logger.info("Initialized SQLite Storage");
            }

            folderDAO = factory.getFolderDAO();
            noteDAO = factory.getNoteDAO();
            tagDAO = factory.getLabelDAO();
            noteService = new NoteService(noteDAO, folderDAO);
            noteService.setHistoryService(new com.example.jylos.service.NoteHistoryService(
                    java.nio.file.Path.of(com.example.jylos.AppDataDirectory.getBaseDirectory(), "history")));
            folderService = new FolderService(folderDAO, noteDAO);
            tagService = new TagService(tagDAO, noteDAO);
            noteOperations = new NoteOperations(noteService, folderService);
            eventBus = EventBus.getInstance();
            backlinkService = new com.example.jylos.service.BacklinkService(noteService, eventBus);
            com.example.jylos.service.NoteTitleIndex titleIndex =
                    com.example.jylos.service.NoteTitleIndex.getInstance();
            titleIndex.wire(noteService, eventBus);
            noteService.setNoteTitleIndex(titleIndex);

            logger.info("Database connections and services initialized");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    @FXML
    public void handleSwitchStorage() {
        appSettings.handleSwitchStorage(
                getMainWindow(),
                prefs,
                this::switchFilesystemVault);
    }

    /** Builds the theme toggle group (light / dark / system) in the menu bar and applies the saved preference. */
    private void initializeThemeMenu() {
        themeToggleGroup = appSettings.initializeThemeMenu(
                toolbarController,
                currentTheme,
                this::detectSystemTheme,
                theme -> currentTheme = theme,
                this::updateThemeMenuSelection,
                this::applyTheme);
    }

    /** Sets up the language toggle group, wiring each item to persist and announce the locale change. */
    private void initializeLanguageMenu() {
        appSettings.initializeLanguageMenu(toolbarController, prefs, this::notifyLanguageChange);
    }



    private void notifyLanguageChange(String lang) {
        if (appSettings.changeLanguage(lang, prefs)) {
            showAlert(Alert.AlertType.INFORMATION,
                    getString("app.restart_required"),
                    getString("app.restart_required"),
                    getString("app.restart_message"));
        }
    }

    private void updateThemeMenuSelection() {
        appSettings.updateThemeMenuSelection(toolbarController, themeToggleGroup, currentTheme);
    }

    private void bindBackendSession() {
        navigationCommand.wire(this::getString, this::updateStatus, noteService);
        documentSupport.wire(noteService, folderService);
        dialogSupport.wire(this::getString, this::updateStatus, tagService);
        tagManagement.wire(this::getString, this::updateStatus, tagService);
        folderOperations.wire(folderService);

        if (sidebarController != null) {
            sidebarController.wire(eventBus, noteService, tagService, folderService, resources,
                    this::handleUiFolderSelected, this::handleUiTagSelected, this::handleUiTrashItemSelected,
                    this::handleUiNoteOpenRequest, this::updateStatus);
        }
        if (notesListController != null) {
            notesListController.wire(eventBus, noteService, tagService, folderService, resources,
                    this::loadNoteInEditor, this::exportNote, this::toggleNotePrivacy,
                    this::handleUiNotesLoaded, this::updateStatus);
            notesPanel = notesListController.getNotesPanel();
            sortComboBox = notesListController.getSortComboBox();
            notesListView = notesListController.getNotesListView();
        }
        if (editorController != null) {
            editorController.wire(eventBus, noteService, tagService, resources,
                    this::handleUiNoteModified, com.example.jylos.service.NoteTitleIndex.getInstance()::titlesSorted);
            editorController.setWikiLinkHandler(title -> noteService.findNoteByTitle(title).ifPresentOrElse(
                    this::handleUiNoteOpenRequest,
                    () -> updateStatus("Note not found: " + title)));
            editorController.setEditorHooks(editorHooks);
            if (editorTabs == null) {
                editorController.initializeTagsBarCollapsed();
                editorTabs = new com.example.jylos.ui.components.EditorTabs(
                        editorController.getEditorTabBar(),
                        editorController.getEditorTabScroll(),
                        new com.example.jylos.ui.components.EditorTabs.Listener() {
                            @Override public void onSelect(String noteId) { openNoteInTab(noteId); }
                            @Override public void onClose(String noteId) { closeTab(noteId); }
                        },
                        this::getString);
            }
        }
        overlaySupport.wire(centerStack, graphView, graphViewController, noteService,
                this::isDarkThemeActive, this::getString, this::updateStatus, this::handleUiNoteOpenRequest,
                this::publishNoteCreated, this::publishNoteUpdated);
        if (graphViewController != null) {
            graphViewController.wire(eventBus, noteService, tagService, resources,
                    overlaySupport::openNoteFromGraph, overlaySupport::hideGraph,
                    () -> getCurrentNote() != null ? getCurrentNote().getId() : null);
        }

        statusBarSupport.wire(storageLabel, wordCountLabel, charCountLabel, statsSeparator,
                wordCharSeparator, prefs, this::getString);
        java.util.function.Supplier<javafx.scene.Scene> sceneSupplier =
                () -> mainSplitPane != null ? mainSplitPane.getScene() : null;
        gitController.wire(gitSeparator, gitBar, gitInitLabel, gitRemoteLabel, gitChangesLabel,
                gitCommitLabel, gitSyncLabel, gitHistoryLabel, prefs, this::getString, this::updateStatus,
                sceneSupplier);
        privacySupport.wire(this::getString, this::updateStatus, sceneSupplier);
        backlinksSupport.wire(backlinksContent, backlinkService, noteService, this::getString,
                this::loadNoteInEditor,
                () -> backlinksSection != null && backlinksSection.isVisible() && backlinksSection.isManaged()
                        && backlinksContent != null && backlinksContent.isVisible() && backlinksContent.isManaged());
        historySupport.wire(noteService, this::getString, this::updateStatus, sceneSupplier,
                this::getCurrentNote, this::loadNoteInEditor);
        noteCreationSupport.wire(noteService, noteOperations,
                () -> !"sqlite".equals(prefs.get("storage_type", "sqlite")),
                this::getString, this::updateStatus,
                this::getMainWindow,
                () -> currentFolder, this::loadNoteInEditor,
                note -> {
                    if (eventBus != null) {
                        eventBus.publish(new NoteEvents.NoteCreatedEvent(note));
                    }
                },
                noteId -> {
                    if (notesListController != null) {
                        notesListController.requestSelectAfterRefresh(noteId);
                    }
                },
                this::refreshNotesList,
                () -> {
                    if (sidebarController != null) {
                        sidebarController.loadRecentNotes();
                        sidebarController.loadFolders();
                    }
                });
        documentWorkflowSupport.wire(documentSupport, noteService, this::getString, this::updateStatus,
                this::getMainWindow,
                () -> currentFolder, this::getCurrentNote,
                () -> editorController != null ? editorController.getCurrentContent() : null,
                () -> {
                    refreshNotesList();
                    if (sidebarController != null) {
                        sidebarController.loadRecentNotes();
                    }
                });
        importSupport.wire(new com.example.jylos.service.ImportService(noteService, folderService),
                this::getString, this::updateStatus,
                this::getMainWindow,
                () -> {
                    refreshNotesList();
                    if (sidebarController != null) {
                        sidebarController.loadFolders();
                        sidebarController.loadTags();
                        sidebarController.loadRecentNotes();
                    }
                });
        workspaceController.wire(this::captureLiveWorkspace, this::applyWorkspace,
                this::getString, this::updateStatus, sceneSupplier);
        updateStorageLabel();
        installVaultContentLoadedCallback(beginBackendSessionGeneration());
    }

    private void refreshBackendSessionViews(boolean forceAllNotes) {
        if (sidebarController != null) {
            sidebarController.loadFolders();
            sidebarController.loadTags();
            sidebarController.loadRecentNotes();
            sidebarController.loadFavorites();
            sidebarController.loadTrashTree();
        }
        if (forceAllNotes) {
            loadAllNotes();
            Platform.runLater(this::goToAllNotes);
        } else {
            Platform.runLater(() -> {
                if (sidebarController != null
                        && sidebarController.getFolderTreeView() != null
                        && sidebarController.getFolderTreeView().getSelectionModel().getSelectedItem() == null) {
                    goToAllNotes();
                }
            });
        }
        updateStorageLabel();
        gitController.refreshStatus();
    }

    private javafx.stage.Window getMainWindow() {
        return mainSplitPane != null && mainSplitPane.getScene() != null ? mainSplitPane.getScene().getWindow() : null;
    }

    private long beginBackendSessionGeneration() {
        long generation = backendSessionGeneration.incrementAndGet();
        activeBackendSessionGeneration = generation;
        return generation;
    }

    private boolean isActiveBackendSession(long generation) {
        return generation == activeBackendSessionGeneration;
    }

    private void installVaultContentLoadedCallback(long generation) {
        if (noteDAO == null) {
            return;
        }
        noteDAO.setOnContentLoaded(() -> {
            if (!isActiveBackendSession(generation)) {
                return;
            }
            Platform.runLater(() -> onVaultContentLoaded(generation));
        });
    }

    private void disposeCurrentBackendSession() {
        if (noteDAO != null) {
            noteDAO.setOnContentLoaded(null);
        }
        if (backlinkService != null) {
            backlinkService.shutdown();
        }
        if (connection != null) {
            closeActiveConnectionQuietly();
        }
    }

    private void closeActiveConnectionQuietly() {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                SQLiteDB.getInstance().closeConnection(connection);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to close backend connection", e);
        } finally {
            connection = null;
        }
    }

    private void resetUiForBackendReload() {
        autosaveDebounce.stop();
        autosaveRunning = false;
        pendingModifiedNoteId = null;
        pendingSearchText = "";
        quickSwitcherLoadVersion.incrementAndGet();
        quickSwitcherNotesCache = List.of();
        if (toolbarController != null && toolbarController.getSearchField() != null) {
            toolbarController.getSearchField().clear();
        }
        currentFolder = null;
        currentTag = null;
        currentFilterType = "all";
        if (editorTabs != null) {
            editorTabs.clear();
        }
        clearEditorToEmpty();
        refreshBacklinks(null);
    }

    private void switchFilesystemVault(String newVaultPath) {
        if (newVaultPath == null || newVaultPath.isBlank()) {
            return;
        }
        String currentType = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        if (!"filesystem".equalsIgnoreCase(currentType)) {
            return;
        }
        String currentPath = prefs.get("filesystem_path", "");
        if (newVaultPath.equals(currentPath)) {
            return;
        }
        if (!confirmLeaveCurrent()) {
            return;
        }

        long generation = beginBackendSessionGeneration();
        try {
            prefs.put("filesystem_path", newVaultPath);
            resetUiForBackendReload();
            disposeCurrentBackendSession();
            initializeDatabase();
            bindBackendSession();
            reloadPluginSystemForBackendSession();
            refreshBackendSessionViews(true);
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
            }
            updateStatus(java.text.MessageFormat.format(getString("status.vault_switched"),
                    new File(newVaultPath).getName()));
            logger.info("Switched vault without restart: " + newVaultPath + " [session=" + generation + "]");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to switch vault session", e);
            prefs.put("filesystem_path", currentPath);
            try {
                long rollbackGeneration = beginBackendSessionGeneration();
                resetUiForBackendReload();
                disposeCurrentBackendSession();
                initializeDatabase();
                bindBackendSession();
                reloadPluginSystemForBackendSession();
                refreshBackendSessionViews(true);
                if (eventBus != null) {
                    eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
                }
                logger.info("Restored previous vault session after switch failure [session=" + rollbackGeneration + "]");
            } catch (Exception rollbackError) {
                logger.log(Level.SEVERE, "Failed to restore previous vault after switch error", rollbackError);
            }
            updateStatus(java.text.MessageFormat.format(getString("status.error_details"), e.getMessage()));
            showAlert(Alert.AlertType.ERROR,
                    getString("status.error"),
                    getString("status.error"),
                    java.text.MessageFormat.format(getString("status.error_details"), e.getMessage()));
        }
    }

    private void reloadPluginSystemForBackendSession() {
        if (commandPalette == null) {
            return;
        }
        if (pluginManager != null) {
            pluginManager.shutdownAll();
        }
        commandPalette.removeCommandById("cmd.plugins.manage");
        pluginUiRefreshSubscription.cancel();
        pluginUiRefreshSubscription = EventBus.Subscription.NO_OP;
        pluginManager = null;
        pluginManagerDialog = null;
        initializePluginSystem();
    }

    /** Registers global keyboard accelerators (command palette, quick-switcher, focus mode) on the primary scene. */
    private void initializeKeyboardShortcuts() {
        try {
            javafx.scene.Scene scene = null;
            if (mainSplitPane != null && mainSplitPane.getScene() != null) {
                scene = mainSplitPane.getScene();
            }

            if (scene == null) {
                logger.warning("Scene not available for keyboard shortcuts");
                return;
            }

            Stage stage = (Stage) scene.getWindow();
            if (stage == null) {
                logger.warning("Stage not available for keyboard shortcuts");
                return;
            }

            ensureCommandUisInitialized(stage);
            commandUI.initializeKeyboardShortcuts(scene, this::showCommandPalette, this::showQuickSwitcher);

            // Focus / writing mode: Cmd/Ctrl + Shift + F.
            scene.getAccelerators().put(
                    new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F,
                            javafx.scene.input.KeyCombination.SHORTCUT_DOWN,
                            javafx.scene.input.KeyCombination.SHIFT_DOWN),
                    this::handleFocusMode);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize keyboard shortcuts", e);
        }
    }

    /** Opens the command palette, initializing it lazily against the primary stage if needed. */
    public void showCommandPalette() {
        ensureCommandUisInitialized(getPrimaryStage());
        if (commandPalette != null) {
            commandPalette.show();
            logger.info("Command Palette opened");
        } else {
            logger.warning("Command Palette not initialized yet");
            updateStatus(getString("status.command_palette_not_ready"));
        }
    }

    /** Opens the quick-switcher overlay, preloading the current note list asynchronously before showing. */
    public void showQuickSwitcher() {
        ensureCommandUisInitialized(getPrimaryStage());
        if (quickSwitcher != null) {
            if (!quickSwitcherNotesCache.isEmpty()) {
                quickSwitcher.setNotes(quickSwitcherNotesCache);
            }
            loadQuickSwitcherNotesAsync();
            quickSwitcher.show();
        }
    }

    private void loadQuickSwitcherNotesAsync() {
        if (noteService == null || quickSwitcher == null) {
            return;
        }
        final long requestId = quickSwitcherLoadVersion.incrementAndGet();
        quickSwitcherExecutor.submit(() -> {
            try {
                List<Note> allNotes = noteService.getAllNotes();
                quickSwitcherNotesCache = List.copyOf(allNotes);
                Platform.runLater(() -> {
                    if (requestId != quickSwitcherLoadVersion.get() || quickSwitcher == null) {
                        return;
                    }
                    quickSwitcher.setNotes(quickSwitcherNotesCache);
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load notes for quick switcher", e);
            }
        });
    }

    private void initializePluginSystem() {
        try {
            if (commandPalette == null) {
                logger.warning("CommandPalette not available, delaying plugin initialization");
                return;
            }

            pluginManager = new PluginManager(noteService, folderService, tagService, eventBus, commandPalette, this,
                    this, this, editorHooks, this, this::handleUiNoteOpenRequest);

            PluginLifecycle.LoadResult pluginLoadResult = pluginLifecycle
                    .registerCoreAndExternalPlugins(pluginManager);

            pluginManager.initializeAll();

            Stage stage = mainSplitPane != null && mainSplitPane.getScene() != null
                    ? (Stage) mainSplitPane.getScene().getWindow()
                    : null;
            pluginManagerDialog = new PluginManagerDialog(stage, pluginManager);
            pluginManagerDialog.setBundle(resources);

            commandPalette.addCommand(new CommandPalette.Command(
                    "cmd.plugins.manage",
                    "Plugins: Manage Plugins",
                    "Open plugin manager to enable/disable plugins",
                    "Ctrl+Shift+P",
                    "=",
                    "Tools",
                    this::showPluginManager));

            pluginUiRefreshSubscription.cancel();
            pluginUiRefreshSubscription = pluginLifecycle.subscribePluginUiEvents(
                    eventBus,
                    () -> Platform.runLater(() -> {
                        sidebarController.loadRecentNotes();
                        sidebarController.loadTags();
                        sidebarController.loadFavorites();
                    }));

            if (!pluginLoadResult.loadFailures().isEmpty()) {
                for (String failure : pluginLoadResult.loadFailures()) {
                    logger.warning("Plugin load warning: " + failure);
                }
            }
            logger.info("Plugin system initialized with " + pluginManager.getPluginCount() + " plugins");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize plugin system", e);
        }
    }

    /** Subscribes to the remaining shell/domain fan-out events that still belong on the {@link EventBus}. */
    private void subscribeToShellAndDomainEvents() {
        if (eventBus == null) {
            return;
        }
        uiEventSubscriptions.forEach(EventBus.Subscription::cancel);
        uiEventSubscriptions.clear();
        uiEventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class,
                event -> handleUiNoteDeleted(event.getNoteId())));
        uiEventSubscriptions.add(eventBus.subscribe(FolderEvents.FolderDeletedEvent.class,
                event -> handleUiFolderDeleted(event.getFolderId())));
        uiEventSubscriptions.add(eventBus.subscribe(NoteEvents.TrashItemDeletedEvent.class,
                event -> handleUiTrashItemDeleted()));
        // Keep the editor tab in sync when its note is saved (clear dirty dot, refresh title).
        uiEventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteSavedEvent.class, e -> {
            Note saved = e.getNote();
            String previousNoteId = e.getPreviousNoteId();
            if (saved == null || saved.getId() == null) {
                return;
            }
            if (editorTabs != null) {
                if (previousNoteId != null && !previousNoteId.equals(saved.getId())) {
                    editorTabs.rebindId(previousNoteId, saved.getId(), saved.getTitle());
                }
                editorTabs.setDirty(saved.getId(), false);
                editorTabs.setTitle(saved.getId(), saved.getTitle());
            }
            if (notesListController != null) {
                notesListController.handleSavedNote(saved, previousNoteId);
            }
            Note active = getCurrentNote();
            if (active != null && (Objects.equals(active.getId(), saved.getId())
                    || Objects.equals(previousNoteId, active.getId()))) {
                pendingModifiedNoteId = saved.getId();
            }
            refreshNotesGridIfActive();
        }));
    }

    void handleUiNotesLoaded(List<Note> notes, String statusMessage) {
        // noteCountLabel shows the count; the transient "loaded …" message goes to
        // the left status label (avoids the message being duplicated in both spots).
        if (noteCountLabel != null) {
            int count = notes != null ? notes.size() : 0;
            noteCountLabel.setText(java.text.MessageFormat.format(getString("info.notes_count"), count));
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            updateStatus(statusMessage);
        }
        refreshNotesGridIfActive();
    }

    void handleUiNoteDeleted(String noteId) {
        Note current = getCurrentNote();
        boolean wasActive = current != null && current.getId().equals(noteId);
        if (editorTabs != null && editorTabs.isOpen(noteId)) {
            String neighbor = editorTabs.neighborOf(noteId);
            if (wasActive && editorController != null) {
                editorController.markClean(); // the note is gone; discard any edits
            }
            editorTabs.removeTab(noteId);
            if (wasActive) {
                if (neighbor != null) {
                    openNoteInTab(neighbor);
                } else if (editorController != null) {
                    editorController.loadNote(null);
                    editorController.clearPreview();
                }
            }
        } else if (wasActive && editorController != null) {
            editorController.loadNote(null);
            editorController.clearPreview();
        }
        refreshNotesList();
        if (sidebarController != null) {
            sidebarController.loadTrashTree();
            sidebarController.loadRecentNotes();
            sidebarController.loadFavorites();
        }
    }

    void handleUiFolderDeleted(String folderId) {
        if (currentFolder != null && currentFolder.getId().equals(folderId)) {
            currentFolder = null;
        }
        if (sidebarController != null) {
            if (sidebarController.getFolderTreeView() != null) {
                sidebarController.getFolderTreeView().refresh();
            }
            sidebarController.loadFolders();
            sidebarController.loadTrashTree();
        }
    }

    void handleUiTrashItemDeleted() {
        if (sidebarController != null) {
            sidebarController.loadTrashTree();
            sidebarController.loadFolders();
        }
    }

    void handleUiFolderSelected(Folder selectedFolder) {
        if (selectedFolder == null) {
            currentFolder = null;
            return;
        }
        String id = selectedFolder.getId();
        if (id == null && "INVISIBLE_ROOT".equals(selectedFolder.getTitle())) {
            currentFolder = null;
            return;
        }
        if ("ALL_NOTES_VIRTUAL".equals(id)) {
            if (notesListController != null) {
                notesListController.loadAllNotes();
            }
            currentFolder = null;
            return;
        }
        handleFolderSelection(selectedFolder);
        currentFolder = selectedFolder;
    }

    void handleUiTagSelected(Tag tag) {
        if (tag == null || tag.getTitle() == null || tag.getTitle().isBlank()) {
            return;
        }
        currentFolder = null;
        currentTag = tag;
        currentFilterType = "tag";
        if (notesListController != null) {
            notesListController.loadNotesForTag(tag.getTitle());
        }
    }

    void handleUiNoteOpenRequest(Note note) {
        Note noteToOpen = resolveNoteToOpen(note);
        if (noteToOpen == null) {
            return;
        }
        loadNoteInEditor(noteToOpen);
        if (notesListView != null) {
            notesListView.getSelectionModel().select(noteToOpen);
        }
    }

    private Note resolveNoteToOpen(Note requestedNote) {
        if (requestedNote == null) {
            return null;
        }
        if (requestedNote.getId() != null || requestedNote.getTitle() == null) {
            return requestedNote;
        }
        if (noteService != null) {
            Optional<Note> resolved = noteService.findNoteByTitle(requestedNote.getTitle());
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return getNoteResolutionSource().stream()
                .filter(n -> requestedNote.getTitle().equalsIgnoreCase(n.getTitle()))
                .findFirst()
                .orElse(requestedNote);
    }

    private List<Note> getNoteResolutionSource() {
        if (!quickSwitcherNotesCache.isEmpty()) {
            return quickSwitcherNotesCache;
        }
        return noteService != null ? noteService.getAllNotes() : List.of();
    }

    void handleUiTrashItemSelected(Component component) {
        if (component instanceof Note note) {
            loadNoteInEditor(note);
        } else if (component instanceof Folder folder) {
            handleFolderSelection(folder);
        }
    }

    void handleUiNoteModified(Note note) {
        if (note == null || getCurrentNote() == null || !Objects.equals(note.getId(), getCurrentNote().getId())) {
            return;
        }
        if (editorTabs != null) {
            editorTabs.setDirty(note.getId(), true);
        }
        pendingModifiedNoteId = note.getId();
        noteModifiedDebounce.playFromStart();
        if (autosaveEnabled) {
            autosaveDebounce.playFromStart();
        }
    }

    private void publishNoteCreated(Note note) {
        if (eventBus != null && note != null) {
            eventBus.publish(new NoteEvents.NoteCreatedEvent(note));
        }
    }

    private void publishNoteSelected(Note note) {
        if (eventBus != null && note != null) {
            eventBus.publish(new NoteEvents.NoteSelectedEvent(note));
        }
    }

    private void publishNoteUpdated(Note note) {
        if (eventBus != null && note != null) {
            eventBus.publish(new NoteEvents.NoteUpdatedEvent(note));
        }
    }

    private void configureNoteModifiedDebounce() {
        noteModifiedDebounce.setOnFinished(e -> {
            Note active = getCurrentNote();
            if (active == null || pendingModifiedNoteId == null || !Objects.equals(pendingModifiedNoteId, active.getId())) {
                return;
            }
            refreshEditorAfterEdit();
            updateNoteStats(active);
        });
    }

    private void configureToolbarSearchDebounce() {
        toolbarSearchDebounce.setOnFinished(e -> {
            if (notesListController == null) {
                return;
            }
            String query = pendingSearchText != null ? pendingSearchText : "";
            if (query.trim().isEmpty()) {
                if ("search".equals(currentFilterType)) {
                    refreshNotesList();
                }
                return;
            }
            performSearch(query);
        });
    }

    private void configureAutosaveDebounce() {
        autosaveDebounce.setOnFinished(e -> {
            if (!autosaveEnabled || autosaveRunning) {
                return;
            }
            Note active = getCurrentNote();
            if (active == null || pendingModifiedNoteId == null || !Objects.equals(pendingModifiedNoteId, active.getId())) {
                return;
            }
            if (!isModified()) {
                return;
            }
            autosaveRunning = true;
            try {
                handleSave(null);
                updateStatus(getString("status.autosave_done"));
            } finally {
                autosaveRunning = false;
            }
        });
    }

    /** Loads persisted UI preferences (autosave, theme, font size, accent, preview lines) and applies them to the running UI. */
    private void applyUiPreferencesFromStore() {
        UiPreferencesStore.UiPreferencesData uiPrefs = uiPreferences.load(prefs);
        autosaveEnabled = uiPrefs.autosaveEnabled();
        autosaveIdleMs = uiPrefs.autosaveIdleMs();
        themeSource = uiPrefs.themeSource();
        externalThemeId = uiPrefs.externalThemeId();
        notesListPreviewLines = uiPrefs.notesPreviewLines();
        uiFontSize = uiPrefs.uiFontSize();
        uiAccentColor = uiPrefs.accentColor();
        enabledSnippets = uiPreferences.loadEnabledSnippets(prefs);
        autosaveDebounce.setDuration(Duration.millis(autosaveIdleMs));

        if (sidebarController != null) {
            sidebarController.applySidebarTabPresentation();
        }
        if (notesListController != null) {
            notesListController.setPreviewLines(notesListPreviewLines);
        }
        applyEditorButtonsPresentation();
        applyUiZoom();
        Platform.runLater(this::applyThemeAndRefreshDependents);
    }

    /**
     * Restores the sidebar and notes-list split proportions saved in a previous
     * session, then keeps them up to date as the user drags the dividers.
     *
     * <p>Only the two stable dividers are persisted: {@code mainSplitPane}
     * (sidebar | content) and {@code contentSplitPane} (notes list | editor). The
     * editor/preview divider is intentionally left to the view-mode logic, which
     * resets it to 50/50 when entering split view.</p>
     *
     * <p>Restoration runs in {@code Platform.runLater} because a SplitPane only
     * creates its dividers after its first layout pass.</p>
     */
    private void setupSplitPanePersistence() {
        Platform.runLater(() -> {
            persistDivider(mainSplitPane, UiPreferencesStore.SPLIT_MAIN_KEY,
                    UiPreferencesStore.DEFAULT_SPLIT_MAIN);
            persistDivider(contentSplitPane, UiPreferencesStore.SPLIT_CONTENT_KEY,
                    UiPreferencesStore.DEFAULT_SPLIT_CONTENT);
        });
    }

    /** Restores a divider position from preferences and registers a listener to persist future drag changes. */
    private void persistDivider(SplitPane splitPane, String key, double defaultPos) {
        if (splitPane == null || splitPane.getDividers().isEmpty()) {
            return;
        }
        double saved = prefs.getDouble(key, defaultPos);
        splitPane.setDividerPositions(saved);
        splitPane.getDividers().get(0).positionProperty().addListener(
                (obs, oldV, newV) -> prefs.putDouble(key, newV.doubleValue()));
    }

    private void bindToolbarSearchFieldDebounced() {
        if (searchListenerBound || toolbarController == null || toolbarController.getSearchField() == null) {
            return;
        }
        toolbarController.getSearchField().textProperty().addListener((obs, oldVal, newVal) -> {
            pendingSearchText = newVal != null ? newVal : "";
            toolbarSearchDebounce.playFromStart();
        });
        searchListenerBound = true;
    }

    public void showPluginManager() {
        if (pluginManagerDialog != null) {
            pluginManagerDialog.setBundle(resources);
            pluginManagerDialog.show();
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(getString("dialog.plugin_manager.title"));
            alert.setHeaderText(getString("dialog.plugin_manager.not_initialized_header"));
            alert.setContentText(getString("dialog.plugin_manager.restart_required_content"));
            com.example.jylos.ui.UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        }
    }

    @FXML
    private void handlePluginManager(ActionEvent event) {
        showPluginManager();
    }
    @Override
    public void registerMenuItem(String pluginId, String category, String itemName, Runnable action) {
        registerMenuItem(pluginId, category, itemName, null, action);
    }

    @Override
    public void registerMenuItem(String pluginId, String category, String itemName, String shortcut, Runnable action) {
        pluginUi.registerMenuItem(
                pluginId,
                category,
                itemName,
                shortcut,
                action,
                toolbarController,
                pluginCategoryMenus,
                pluginMenuItems);
    }

    @Override
    public void addMenuSeparator(String pluginId, String category) {
        pluginUi.addMenuSeparator(pluginId, category, pluginCategoryMenus, pluginMenuItems);
    }

    @Override
    public void removePluginMenuItems(String pluginId) {
        pluginUi.removePluginMenuItems(
                pluginId,
                toolbarController,
                pluginCategoryMenus,
                pluginMenuItems);
    }

    @Override
    public boolean isPluginEnabled(String pluginId) {
        return pluginManager != null && pluginManager.isPluginEnabled(pluginId);
    }


    @Override
    public void registerSidePanel(String pluginId, String panelId, String title, javafx.scene.Node content,
            String icon) {
        pluginUi.registerSidePanel(
                pluginId,
                panelId,
                title,
                content,
                icon,
                pluginPanelsContainer,
                pluginPanels,
                pluginPanelIds);
    }

    @Override
    public void removeSidePanel(String pluginId, String panelId) {
        pluginUi.removeSidePanel(
                pluginId,
                panelId,
                pluginPanelsContainer,
                pluginPanels,
                pluginPanelIds);
    }

    @Override
    public void removeAllSidePanels(String pluginId) {
        pluginUi.removeAllSidePanels(
                pluginId,
                pluginPanelsContainer,
                pluginPanels,
                pluginPanelIds);
    }

    @Override
    public void setPluginPanelsVisible(boolean visible) {
        pluginUi.setPluginPanelsVisible(visible, pluginPanelsContainer);
    }

    @Override
    public boolean isPluginPanelsVisible() {
        return pluginUi.isPluginPanelsVisible(pluginPanelsContainer);
    }

    // ── ToolbarRegistry (plugin toolbar buttons) ─────────────────────────────

    @Override
    public void registerToolbarButton(String pluginId, String buttonId, String tooltip,
            String iconLiteral, Runnable action) {
        if (pluginId == null || buttonId == null || action == null) {
            return;
        }
        HBox container = toolbarController != null ? toolbarController.getPluginToolbarContainer() : null;
        if (container == null) {
            return;
        }
        Platform.runLater(() -> {
            Button button = new Button();
            button.getStyleClass().add("toolbar-btn");
            // Stable per-plugin id so re-registration replaces instead of duplicating.
            button.setId("plugin-toolbar-" + pluginId + "-" + buttonId);
            if (tooltip != null && !tooltip.isBlank()) {
                button.setTooltip(new Tooltip(tooltip));
            }
            if (iconLiteral != null && !iconLiteral.isBlank()) {
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(iconLiteral);
                icon.getStyleClass().add("feather-icon");
                icon.setIconSize(16);
                button.setGraphic(icon);
            } else {
                button.setText(tooltip != null ? tooltip : buttonId);
            }
            button.setOnAction(e -> action.run());

            container.getChildren().removeIf(n -> button.getId().equals(n.getId()));
            container.getChildren().add(button);
            pluginToolbarButtons.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(button);
            container.setVisible(true);
            container.setManaged(true);
        });
    }

    @Override
    public void removeToolbarButtons(String pluginId) {
        HBox container = toolbarController != null ? toolbarController.getPluginToolbarContainer() : null;
        List<javafx.scene.Node> buttons = pluginToolbarButtons.remove(pluginId);
        if (container == null || buttons == null) {
            return;
        }
        Platform.runLater(() -> {
            container.getChildren().removeAll(buttons);
            boolean empty = container.getChildren().isEmpty();
            container.setVisible(!empty);
            container.setManaged(!empty);
        });
    }


    public void registerStatusBarItem(String pluginId, String itemId, javafx.scene.Node content) {
        pluginUi.registerStatusBarItem(
                pluginId,
                itemId,
                content,
                pluginStatusBarContainer,
                pluginStatusBarItems,
                pluginStatusBarItemIds);
    }

    public void removeStatusBarItem(String pluginId, String itemId) {
        pluginUi.removeStatusBarItem(
                pluginId,
                itemId,
                pluginStatusBarContainer,
                pluginStatusBarItems,
                pluginStatusBarItemIds);
    }

    public void updateStatusBarItem(String pluginId, String itemId, javafx.scene.Node content) {
        pluginUi.updateStatusBarItem(pluginId, itemId, content, pluginStatusBarItems);
    }

    public void removeAllStatusBarItems(String pluginId) {
        pluginUi.removeAllStatusBarItems(
                pluginId,
                pluginStatusBarContainer,
                pluginStatusBarItems,
                pluginStatusBarItemIds);
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /** Dispatches a command token through the routing table; logs a warning and updates the status bar if the command is unknown. */
    private void executeCommand(String commandName) {
        CommandRouting.DispatchResult result = commandRouting
                .dispatch(commandName, this::executePluginCommandToken);
        logger.info("Executing command: " + result.resolvedToken());
        if (result.handled()) {
            return;
        }
        logger.warning("Unknown command: " + result.resolvedToken());
        updateStatus(java.text.MessageFormat.format(getString("status.unknown_command"), result.resolvedToken()));
    }

    private boolean executePluginCommandToken(String token) {
        if (commandPalette == null || token == null || token.isBlank()) {
            return false;
        }
        CommandPalette.Command cmd = commandPalette.findCommand(token);
        if (cmd == null) {
            cmd = commandPalette.findCommandById(token);
        }
        if (cmd == null) {
            return false;
        }
        cmd.execute();
        return true;
    }

    /** Populates the command routing table once, mapping every command ID and legacy name to its action {@link Runnable}. */
    private void initializeCommandRouting() {
        if (!commandRouting.isEmpty()) {
            return;
        }
        commandRegistry.registerDefaultRoutes(
                this::registerCommandRoute,
                commandRouting::registerAlias,
                this::resolveCommandAction);
    }

    /** Returns the {@link Runnable} bound to {@code commandId}, or {@code null} for unknown IDs (used by the command registry). */
    private Runnable resolveCommandAction(String commandId) {
        if (commandId == null) {
            return null;
        }
        switch (commandId) {
            case "cmd.new_note":
                return () -> handleNewNote(null);
            case "cmd.new_canvas":
                return () -> handleNewCanvas(null);
            case "cmd.new_folder":
                return () -> handleNewFolder(null);
            case "cmd.save":
                return () -> handleSave(null);
            case "cmd.save_all":
                return () -> handleSaveAll(null);
            case "cmd.import":
                return () -> handleImport(null);
            case "cmd.export":
                return () -> handleExport(null);
            case "cmd.delete_note":
                return () -> handleDelete(null);
            case "cmd.undo":
                return () -> handleUndo(null);
            case "cmd.redo":
                return () -> handleRedo(null);
            case "cmd.find":
                return () -> handleFind(null);
            case "cmd.replace":
                return () -> handleReplace(null);
            case "cmd.cut":
                return () -> handleCut(null);
            case "cmd.copy":
                return () -> handleCopy(null);
            case "cmd.paste":
                return () -> handlePaste(null);
            case "cmd.bold":
                return () -> publishEditorAction(SystemActionEvent.ActionType.BOLD);
            case "cmd.italic":
                return () -> publishEditorAction(SystemActionEvent.ActionType.ITALIC);
            case "cmd.underline":
                return () -> publishEditorAction(SystemActionEvent.ActionType.UNDERLINE);
            case "cmd.insert_link":
                return () -> publishEditorAction(SystemActionEvent.ActionType.LINK);
            case "cmd.insert_rich_link":
                return () -> publishEditorAction(SystemActionEvent.ActionType.RICH_LINK);
            case "cmd.insert_image":
                return () -> publishEditorAction(SystemActionEvent.ActionType.IMAGE);
            case "cmd.insert_todo":
                return () -> publishEditorAction(SystemActionEvent.ActionType.TODO_LIST);
            case "cmd.insert_list":
                return () -> publishEditorAction(SystemActionEvent.ActionType.NUMBERED_LIST);
            case "cmd.toggle_sidebar":
                return () -> handleToggleSidebar(null);
            case "cmd.toggle_info_panel":
                return () -> handleToggleRightPanel(null);
            case "cmd.editor_mode":
                return () -> handleEditorOnlyMode(null);
            case "cmd.preview_mode":
                return () -> handlePreviewOnlyMode(null);
            case "cmd.split_mode":
                return () -> handleSplitViewMode(null);
            case "cmd.zoom_in":
                return () -> handleZoomIn(null);
            case "cmd.zoom_out":
                return () -> handleZoomOut(null);
            case "cmd.reset_zoom":
                return () -> handleResetZoom(null);
            case "cmd.theme_light":
                return () -> handleLightTheme(null);
            case "cmd.theme_dark":
                return () -> handleDarkTheme(null);
            case "cmd.theme_system":
                return () -> handleSystemTheme(null);
            case "cmd.graph_view":
                return overlaySupport::toggleGraph;
            case "cmd.knowledge_insights":
                return this::showKnowledgeInsights;
            case "cmd.workspace_save":
                return workspaceController::saveCurrent;
            case "cmd.workspace_save_as":
                return workspaceController::saveCurrentAs;
            case "cmd.workspace_open":
                return workspaceController::openWorkspaceDialog;
            case "cmd.workspace_manage":
                return workspaceController::manageDialog;
            case "cmd.git_panel":
                return gitController::showSyncPanel;
            case "cmd.git_sync":
                return gitController::sync;
            case "cmd.git_commit_push":
                return gitController::commitPush;
            case "cmd.git_pull":
                return gitController::pull;
            case "cmd.git_init":
                return gitController::init;
            case "cmd.git_add_remote":
                return gitController::addRemote;
            case "cmd.quick_switcher":
                return this::showQuickSwitcher;
            case "cmd.global_search":
                return () -> handleSearch(null);
            case "cmd.daily_note":
                return this::handleDailyNote;
            case "cmd.new_from_template":
                return this::handleNewFromTemplate;
            case "cmd.export_vault":
                return this::handleExportVault;
            case "cmd.goto_all_notes":
                return this::goToAllNotes;
            case "cmd.goto_favorites":
                return () -> {
                    if (sidebarController != null) {
                        sidebarController.loadFavorites();
                    }
                };
            case "cmd.goto_recent":
                return () -> {
                    if (sidebarController != null) {
                        sidebarController.loadRecentNotes();
                    }
                };
            case "cmd.tag_manager":
                return () -> handleTagsManager(null);
            case "cmd.preferences":
                return () -> handlePreferences(null);
            case "cmd.toggle_favorite":
                return () -> handleToggleFavorite(null);
            case "cmd.refresh":
                return () -> handleRefresh(null);
            case "cmd.plugins.manage":
                return this::showPluginManager;
            case "cmd.keyboard_shortcuts":
                return () -> handleKeyboardShortcuts(null);
            case "cmd.documentation":
                return () -> handleDocumentation(null);
            case "cmd.about":
                return () -> handleAbout(null);
            default:
                return null;
        }
    }

    /** Registers a command route with both its stable ID and a legacy display-name alias in the routing table. */
    private void registerCommandRoute(String id, String legacyName, Runnable action) {
        commandRouting.registerRoute(id, legacyName, action);
    }

    private void initializeSystemActionHandlers() {
        if (!systemActionHandlers.isEmpty()) {
            return;
        }

        systemActionHandlers.put(SystemActionEvent.ActionType.NEW_FOLDER, () -> handleNewFolder(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.NEW_TAG, () -> handleNewTag(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SAVE_ALL, () -> handleSaveAll(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.IMPORT, () -> handleImport(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EXPORT, () -> handleExport(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.DAILY_NOTE, this::handleDailyNote);
        systemActionHandlers.put(SystemActionEvent.ActionType.NEW_FROM_TEMPLATE, this::handleNewFromTemplate);
        systemActionHandlers.put(SystemActionEvent.ActionType.EXPORT_VAULT, this::handleExportVault);
        systemActionHandlers.put(SystemActionEvent.ActionType.EXIT, () -> handleExit(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.UNDO, () -> handleUndo(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.REDO, () -> handleRedo(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.CUT, () -> handleCut(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.COPY, () -> handleCopy(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.PASTE, () -> handlePaste(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.FIND, () -> handleFind(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.REPLACE, () -> handleReplace(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_SIDEBAR, () -> handleToggleSidebar(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_NOTES_LIST, () -> handleToggleNotesPanel(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SWITCH_LAYOUT, () -> handleViewLayoutSwitch(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.ZOOM_IN, () -> handleZoomIn(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.ZOOM_OUT, () -> handleZoomOut(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.RESET_ZOOM, () -> handleResetZoom(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.ZOOM_EDITOR_IN, () -> handleEditorZoomIn(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.ZOOM_EDITOR_OUT, () -> handleEditorZoomOut(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.RESET_EDITOR_ZOOM, () -> handleEditorResetZoom(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.LIST_VIEW, () -> handleListView(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.GRID_VIEW, () -> handleGridView(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TAGS_MANAGER, () -> handleTagsManager(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.PLUGIN_MANAGER, () -> handlePluginManager(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.PREFERENCES, () -> handlePreferences(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SWITCH_STORAGE, this::handleSwitchStorage);
        systemActionHandlers.put(SystemActionEvent.ActionType.DOCUMENTATION, () -> handleDocumentation(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.ABOUT, () -> handleAbout(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SORT_FOLDERS, () -> sidebarController.handleSortFolders(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EXPAND_ALL_FOLDERS,
                () -> sidebarController.handleExpandAllFolders(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.COLLAPSE_ALL_FOLDERS,
                () -> sidebarController.handleCollapseAllFolders(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SORT_TRASH, () -> sidebarController.handleSortTrash(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EMPTY_TRASH, () -> sidebarController.handleEmptyTrash(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.REFRESH_NOTES, () -> handleRefresh(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_TAGS, () -> handleToggleTags(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EDITOR_ONLY_MODE, () -> handleEditorOnlyMode(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SPLIT_VIEW_MODE, () -> handleSplitViewMode(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.PREVIEW_ONLY_MODE, () -> handlePreviewOnlyMode(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_PIN, () -> handleTogglePin(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_FAVORITE, () -> handleToggleFavorite(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_RIGHT_PANEL, () -> handleToggleRightPanel(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.NAVIGATE_BACK,    this::navigateBack);
        systemActionHandlers.put(SystemActionEvent.ActionType.NAVIGATE_FORWARD, this::navigateForward);
        systemActionHandlers.put(SystemActionEvent.ActionType.GRAPH_VIEW, overlaySupport::toggleGraph);
        systemActionHandlers.put(SystemActionEvent.ActionType.KNOWLEDGE_INSIGHTS, this::showKnowledgeInsights);
        systemActionHandlers.put(SystemActionEvent.ActionType.WORKSPACE_SAVE, workspaceController::saveCurrent);
        systemActionHandlers.put(SystemActionEvent.ActionType.WORKSPACE_SAVE_AS, workspaceController::saveCurrentAs);
        systemActionHandlers.put(SystemActionEvent.ActionType.WORKSPACE_OPEN, workspaceController::openWorkspaceDialog);
        systemActionHandlers.put(SystemActionEvent.ActionType.WORKSPACE_MANAGE, workspaceController::manageDialog);
        systemActionHandlers.put(SystemActionEvent.ActionType.FOCUS_MODE, this::handleFocusMode);
        systemActionHandlers.put(SystemActionEvent.ActionType.KANBAN_VIEW, overlaySupport::toggleKanban);
        systemActionHandlers.put(SystemActionEvent.ActionType.PRIVATE_TOGGLE, this::handleTogglePrivate);
        systemActionHandlers.put(SystemActionEvent.ActionType.NOTES_UNLOCK, this::handleUnlockNotes);
        systemActionHandlers.put(SystemActionEvent.ActionType.NOTES_LOCK, this::handleLockNotes);
        systemActionHandlers.put(SystemActionEvent.ActionType.IMPORT_OBSIDIAN, importSupport::importObsidianVault);
        systemActionHandlers.put(SystemActionEvent.ActionType.IMPORT_ENEX, importSupport::importEnex);
        systemActionHandlers.put(SystemActionEvent.ActionType.NOTE_HISTORY, historySupport::showHistoryDialog);
        systemActionHandlers.put(SystemActionEvent.ActionType.QUICK_SWITCHER, this::showQuickSwitcher);
        systemActionHandlers.put(SystemActionEvent.ActionType.CLOSE_NOTE, this::handleCloseNote);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_PANEL, gitController::showSyncPanel);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_SYNC, gitController::sync);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_COMMIT_PUSH, gitController::commitPush);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_PULL, gitController::pull);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_INIT, gitController::init);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_ADD_REMOTE, gitController::addRemote);
    }

    // ------------------------------------------------------------------
    // Navigation history
    // ------------------------------------------------------------------

    /** Pops the most recent entry from the back-stack, pushes the current note onto the forward-stack, and loads the target note. */
    private void navigateBack() {
        if (navBackStack.isEmpty()) return;
        String currentId = (getCurrentNote() != null) ? getCurrentNote().getId() : null;
        String targetId  = navBackStack.pollLast();
        if (currentId != null) {
            if (navForwardStack.size() >= NAV_MAX_HISTORY) navForwardStack.pollFirst();
            navForwardStack.addLast(currentId);
        }
        loadNoteById(targetId);
    }

    /** Pops the most recent entry from the forward-stack, pushes the current note onto the back-stack, and loads the target note. */
    private void navigateForward() {
        if (navForwardStack.isEmpty()) return;
        String currentId = (getCurrentNote() != null) ? getCurrentNote().getId() : null;
        String targetId  = navForwardStack.pollLast();
        if (currentId != null) {
            if (navBackStack.size() >= NAV_MAX_HISTORY) navBackStack.pollFirst();
            navBackStack.addLast(currentId);
        }
        loadNoteById(targetId);
    }

    /** Loads the note identified by {@code noteId} into the editor without recording a new navigation event. */
    private void loadNoteById(String noteId) {
        if (noteId == null || noteService == null) return;
        navJumping = true;
        try {
            noteService.getNoteById(noteId).ifPresent(note -> {
                loadNoteInEditor(note);
                if (notesListView != null) notesListView.getSelectionModel().select(note);
            });
        } finally {
            navJumping = false;
            updateNavigationButtons();
        }
    }

    /** Pushes {@code previousNoteId} onto the back-stack and clears the forward-stack, unless a programmatic jump is in progress. */
    private void recordNavigation(String previousNoteId) {
        if (navJumping || previousNoteId == null) return;
        if (navBackStack.size() >= NAV_MAX_HISTORY) navBackStack.pollFirst();
        navBackStack.addLast(previousNoteId);
        navForwardStack.clear();
        updateNavigationButtons();
    }

    /** Syncs the back/forward toolbar-button states to reflect whether the respective navigation stacks are non-empty. */
    private void updateNavigationButtons() {
        if (editorController != null) {
            editorController.updateNavigationState(!navBackStack.isEmpty(), !navForwardStack.isEmpty());
        }
    }

    // ------------------------------------------------------------------

    private Stage getPrimaryStage() {
        if (mainSplitPane != null && mainSplitPane.getScene() != null) {
            javafx.stage.Window window = mainSplitPane.getScene().getWindow();
            if (window instanceof Stage stage) {
                return stage;
            }
        }
        return null;
    }

    private void ensureCommandUisInitialized(Stage stage) {
        CommandUI.CommandUiComponents components = commandUI.ensureCommandUiComponents(
                stage,
                commandPalette,
                quickSwitcher,
                this::executeCommand,
                this::loadNoteInEditor,
                resources);
        commandPalette = components.commandPalette();
        quickSwitcher = components.quickSwitcher();
    }

    private void initializeSortOptions() {
        uiInitialization.initializeSortOptions(sortComboBox, this::sortNotes);
    }

    private void initializeViewModeButtons() {
        if (editorController == null || notesListController == null) {
            return;
        }
        uiInitialization.initializeViewModeButtons(
                editorController.getEditorOnlyButton(),
                editorController.getSplitViewButton(),
                editorController.getPreviewOnlyButton(),
                notesListController::initializeNotesGrid,
                this::applyViewMode);
        applyEditorButtonsPresentation();
    }

    @FXML
    private void handleListView(ActionEvent event) {
        if (notesListController != null
                && notesListController.showListView(this::refreshNotesGridIfActive, logger::warning)) {
            updateStatus(getString("status.view_list"));
        }
    }

    @FXML
    private void handleGridView(ActionEvent event) {
        if (notesListController != null
                && notesListController.showGridView(this::refreshNotesGridIfActive, logger::warning)) {
            updateStatus(getString("status.view_grid"));
        }
    }

    private void refreshNotesGridIfActive() {
        if (notesListController != null) {
            notesListController.refreshGridViewIfActive(
                    isDarkThemeActive(),
                    this::getString,
                    this::loadNoteInEditor,
                    this::updateStatus);
        }
    }

    private void setupToolbarResponsiveness() {
        uiInitialization.setupToolbarResponsiveness(toolbarController, this::updateToolbarOverflow);
        if (editorController != null && editorController.getEditorContainer() != null) {
            editorController.getEditorContainer().widthProperty()
                    .addListener((obs, oldVal, newVal) -> applyEditorButtonsPresentation());
        }
    }

    private void updateToolbarOverflow(double width) {
        uiInitialization.updateToolbarOverflow(toolbarController, width);
    }

    private void applyViewMode() {
        uiLayout.applyViewMode(
                currentViewMode,
                editorController.getEditorPreviewSplitPane(),
                editorController.getEditorPane(),
                editorController.getPreviewPane(),
                editorController.getEditorOnlyButton(),
                editorController.getSplitViewButton(),
                editorController.getPreviewOnlyButton(),
                this::refreshEditorPreview);
        // "Read" mode (preview-only) makes the properties panel read-only with
        // clickable internal links; editor/split modes keep it editable.
        if (editorController != null) {
            editorController.setReadOnlyView(currentViewMode == UiLayout.ViewMode.PREVIEW_ONLY);
        }
    }

    private void applyEditorButtonsPresentation() {
        if (editorController == null) {
            return;
        }
        editorController.applyViewModeButtonsPresentation();
    }

    @FXML
    private void handleEditorOnlyMode(ActionEvent event) {
        currentViewMode = UiLayout.ViewMode.EDITOR_ONLY;
        applyViewMode();
        updateStatus(getString("status.mode_editor"));
    }

    @FXML
    private void handleSplitViewMode(ActionEvent event) {
        currentViewMode = UiLayout.ViewMode.SPLIT;
        applyViewMode();
        updateStatus(getString("status.mode_split"));
    }

    @FXML
    private void handlePreviewOnlyMode(ActionEvent event) {
        currentViewMode = UiLayout.ViewMode.PREVIEW_ONLY;
        applyViewMode();
        updateStatus(getString("status.mode_preview"));
    }


    @FXML
    private void handleToggleRightPanel(ActionEvent event) {
        uiLayout.toggleRightPanel(editorRightSplitPane, rightPanel,
                toolbarController != null ? toolbarController.getRightPanelToggleBtn() : null,
                getCurrentNote(),
                () -> updateNoteMetadata(getCurrentNote()));
    }

    private void initializeRightPanelSections() {
        uiInitialization.initializeRightPanelSections(
                noteInfoHeader,
                noteInfoContent,
                noteInfoCollapseIcon,
                pluginPanelsContainer);
        uiInitialization.wireCollapsibleSection(backlinksHeader, backlinksContent, backlinksCollapseIcon,
                expand -> {
                    if (expand) {
                        refreshBacklinks(getCurrentNote());
                    }
                });
    }

    private void initializeRightPanelVisibility() {
        if (rightPanel != null) {
            rightPanel.setVisible(true);
            rightPanel.setManaged(true);
            rightPanel.setMinWidth(0);
            rightPanel.setMaxWidth(0);
            rightPanel.setPrefWidth(0);
        }
        if (toolbarController != null && toolbarController.getRightPanelToggleBtn() != null) {
            toolbarController.getRightPanelToggleBtn().setSelected(false);
        }
    }

    /** Resets the filter state and asks {@link NotesListController} to display every note. */
    private void loadAllNotes() {
        if (notesListController != null) {
            currentFolder = null;
            currentTag = null;
            currentFilterType = "all";
            notesListController.loadAllNotes();
        }
    }

    /** Selects the "All Notes" virtual node in the sidebar tree, or falls back to {@link #loadAllNotes()} when the tree is unavailable. */
    private void goToAllNotes() {
        if (sidebarController == null) {
            loadAllNotes();
            return;
        }
        if (sidebarController.getFolderTreeView() != null && sidebarController.getFolderTreeView().getRoot() != null) {
            for (TreeItem<Folder> child : sidebarController.getFolderTreeView().getRoot().getChildren()) {
                Folder folder = child.getValue();
                if (folder != null && "ALL_NOTES_VIRTUAL".equals(folder.getId())) {
                    sidebarController.getFolderTreeView().getSelectionModel().select(child);
                    child.setExpanded(true);
                    return;
                }
            }
        }
        loadAllNotes();
    }

    /** Updates the active folder filter and ensures the notes panel is visible, expanding collapsed splits as needed. */
    private void handleFolderSelection(Folder folder) {
        try {
            if (folder == null) {
                return;
            }
            currentFolder = folder;
            currentTag = null;
            currentFilterType = "folder";
            if (notesListController != null) {
                notesListController.loadNotesForFolder(folder);
            }

            if (notesPanel == null) {
                return;
            }
            if (isStackedLayout) {
                double[] positions = navSplitPane != null ? navSplitPane.getDividerPositions() : new double[0];
                boolean dividerCollapsed = positions != null && positions.length > 0 && positions[0] > 0.95;
                if (notesPanel.getMaxWidth() < 10 || dividerCollapsed) {
                    notesPanel.setMinWidth(180);
                    notesPanel.setMaxWidth(Double.MAX_VALUE);
                    if (navSplitPane != null) {
                        navSplitPane.setDividerPositions(0.5);
                    }
                }
            } else if (notesPanel.getMaxWidth() < 10) {
                notesPanel.setMinWidth(180);
                notesPanel.setMaxWidth(Double.MAX_VALUE);
                if (contentSplitPane != null) {
                    contentSplitPane.setDividerPositions(0.25);
                }
            }
            if (toolbarController != null && toolbarController.getNotesPanelToggleBtn() != null) {
                toolbarController.getNotesPanelToggleBtn().setSelected(true);
            }
        } catch (Exception e) {
            logger.severe("Failed to handle folder selection "
                    + (folder != null ? folder.getTitle() : "null") + ": " + e.getMessage());
        }
    }

    /** Closes the current note: clears the editor to the empty state. */
    private void handleCloseNote() {
        // With tabs, "close note" closes the active tab (and activates a neighbour).
        if (editorTabs != null && editorTabs.getActiveId() != null) {
            closeTab(editorTabs.getActiveId());
            return;
        }
        if (!confirmLeaveCurrent()) {
            return;
        }
        clearEditorToEmpty();
    }

    /** Delegates note word/character count display to {@link StatusBarSupport}. */
    private void updateNoteStats(Note note) {
        statusBarSupport.updateNoteStats(note);
    }

    /** Updates the storage-type indicator label in the status bar. */
    private void updateStorageLabel() {
        statusBarSupport.updateStorageLabel();
    }

    /** Click on the storage indicator → open the storage switcher (Obsidian-style). */
    @FXML
    private void handleStorageClick(javafx.scene.input.MouseEvent event) {
        handleSwitchStorage();
    }

    // ------------------------------------------------------------------
    // Git vault synchronization — logic lives in GitController; these are the
    // FXML-bound status-bar click handlers, which simply delegate.
    // ------------------------------------------------------------------

    @FXML private void handleGitStatusClick(javafx.scene.input.MouseEvent event)  { gitController.sync(); }
    @FXML private void handleGitInitClick(javafx.scene.input.MouseEvent event)    { gitController.init(); }
    @FXML private void handleGitRemoteClick(javafx.scene.input.MouseEvent event)  { gitController.addRemote(); }
    // Changes and commit both open the consolidated Git Sync panel (status + changes +
    // commit + pull/push/sync + log), which superseded the standalone changes/commit dialogs.
    @FXML private void handleGitCommitClick(javafx.scene.input.MouseEvent event)  { gitController.showSyncPanel(); }
    @FXML private void handleGitChangesClick(javafx.scene.input.MouseEvent event) { gitController.showSyncPanel(); }
    @FXML private void handleGitHistoryClick(javafx.scene.input.MouseEvent event) { gitController.showHistoryDialog(); }

    /** Applies the active theme stylesheet to a dialog and sets its owner window. */
    private void styleDialog(Dialog<?> dialog) {
        javafx.scene.Scene scene = mainSplitPane != null ? mainSplitPane.getScene() : null;
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        com.example.jylos.ui.UiDialogs.apply(dialog);
    }

    void loadNoteInEditor(Note note) {
        if (note != null && note.getId() != null && noteService != null) {
            note = noteService.getNoteById(note.getId()).orElse(note);
        }
        // A locked private note loads as a placeholder; offer to unlock just this note
        // (the global "unlock private notes" command is what reveals all of them).
        if (note != null && note.isPrivate() && note.getId() != null && noteService != null) {
            EncryptionService enc = EncryptionService.getInstance();
            if (enc.isConfigured() && !enc.canRead(note.getId()) && privacySupport.promptRevealNote(note.getId())) {
                note = noteService.getNoteById(note.getId()).orElse(note);
            }
        }
        // Record the note we're leaving in the back-navigation stack.
        Note leaving = getCurrentNote();
        if (leaving != null && note != null && !leaving.getId().equals(note.getId())) {
            recordNavigation(leaving.getId());
        }

        if (isModified() && leaving != null) {
            SaveDialogDecision decision = showSaveDialog();
            if (decision == SaveDialogDecision.CANCEL) {
                return;
            }
            if (decision == SaveDialogDecision.SAVE) {
                handleSave(new ActionEvent());
            }
        }

        if (editorController != null) {
            editorController.loadNote(note);
        }

        Note activeNote = getCurrentNote();
        if (activeNote == null) {
            editorController.syncFavoritePinButtons(this::getString);
            editorController.clearPreview();
            updateNoteMetadata(null);
            updateStatus(getString("status.no_note_selected"));
            return;
        }

        if (editorTabs != null) {
            editorTabs.ensureTab(activeNote.getId(), activeNote.getTitle());
            editorTabs.setActive(activeNote.getId());
            editorTabs.setDirty(activeNote.getId(), false);
        }

        publishNoteSelected(activeNote);
        updateNoteMetadata(activeNote);
        refreshBacklinks(activeNote);
        editorController.syncFavoritePinButtons(this::getString);

        // Attachments (PDF/images) show a native viewer — skip the Markdown-only work
        // (tags, preview render, word stats, favorite/pin toggles).
        if (editorController.isViewingAttachment()) {
            updateStatus(java.text.MessageFormat.format(getString("status.note_loaded"), activeNote.getTitle()));
            return;
        }

        editorController.loadNoteTagsForNote(activeNote, this::handleAddTagToNote, this::removeTagFromNote);
        updateNoteStats(activeNote);
        editorController.ensurePreviewWebViewThemeClass();
        refreshEditorPreview();

        updateStatus(java.text.MessageFormat.format(getString("status.note_loaded"), activeNote.getTitle()));
    }

    // ============================================================
    // Editor tabs (multiple open notes)
    // ============================================================

    /** Loads the note with {@code noteId} into the editor (used by tab selection). */
    private void openNoteInTab(String noteId) {
        if (noteService == null || noteId == null) {
            return;
        }
        noteService.getNoteById(noteId).ifPresent(this::loadNoteInEditor);
    }

    /**
     * Closes a tab. For the active tab this prompts to save unsaved changes, then
     * activates a neighbouring tab (or empties the editor when none remain). Non-active
     * tabs are always clean in the single-editor model, so they close immediately.
     */
    private void closeTab(String noteId) {
        if (editorTabs == null || noteId == null) {
            return;
        }
        if (!noteId.equals(editorTabs.getActiveId())) {
            editorTabs.removeTab(noteId);
            return;
        }
        if (!confirmLeaveCurrent()) {
            return; // user cancelled
        }
        String neighbor = editorTabs.neighborOf(noteId);
        editorTabs.removeTab(noteId);
        if (neighbor != null) {
            openNoteInTab(neighbor); // editor is clean now → no second save prompt
        } else {
            clearEditorToEmpty();
        }
    }

    /**
     * If the current note has unsaved edits, asks the user to save/discard/cancel.
     * Returns {@code false} only when the user cancels (caller should abort). On
     * discard the editor's dirty flag is cleared so a following load won't re-prompt.
     */
    private boolean confirmLeaveCurrent() {
        if (isModified() && getCurrentNote() != null) {
            SaveDialogDecision decision = showSaveDialog();
            if (decision == SaveDialogDecision.CANCEL) {
                return false;
            }
            if (decision == SaveDialogDecision.SAVE) {
                handleSave(new ActionEvent());
            } else if (editorController != null) {
                editorController.markClean();
            }
        }
        return true;
    }

    /** Resets the editor to the empty state (no note open). */
    private void clearEditorToEmpty() {
        if (editorController != null) {
            editorController.loadNote(null);
            editorController.clearPreview();
        }
        if (notesListController != null && notesListController.getNotesListView() != null) {
            notesListController.getNotesListView().getSelectionModel().clearSelection();
        }
        updateNoteMetadata(null);
        updateNoteStats(null);
        updateStatus(getString("status.note_closed"));
    }

    private void updateNoteMetadata(Note note) {
        if (editorController == null) {
            return;
        }
        editorController.updateNoteMetadata(
                note,
                editorController.getModifiedDateLabel(),
                infoCreatedLabel,
                infoModifiedLabel,
                infoLatitudeLabel,
                infoLongitudeLabel,
                infoAuthorLabel,
                infoSourceUrlLabel,
                this::getString);
    }

    /** Recomputes (off the FX thread) and renders the backlinks for {@code note}. */
    private void refreshBacklinks(Note note) {
        backlinksSupport.refresh(note);
    }

    private void loadNotesForTag(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return;
        }
        currentFolder = null;
        currentTag = new Tag(tagName);
        currentFilterType = "tag";
        if (notesListController != null) {
            notesListController.loadNotesForTag(tagName);
        }
    }

    /** Sets the filter type to "search" and delegates full-text search to {@link NotesListController}. */
    private void performSearch(String searchText) {
        if (notesListController != null) {
            currentFilterType = "search";
            notesListController.performSearch(searchText);
        }
    }

    /** Forwards the selected sort criterion to {@link NotesListController} for immediate resorting. */
    private void sortNotes(String sortOption) {
        if (notesListController != null) {
            notesListController.sortNotes(sortOption);
        }
    }

    /** Re-renders the editor's Markdown preview, applying the correct dark/light theme to the WebView. */
    private void refreshEditorPreview() {
        if (editorController != null && editorController.isPreviewVisible()) {
            editorController.refreshPreview(isDarkThemeActive());
        }
    }

    private void refreshEditorAfterEdit() {
        if (editorController == null) {
            return;
        }
        refreshEditorPreview();
        updateNoteMetadata(getCurrentNote());
    }

    @Override
    public void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer) {
        if (editorController != null) {
            editorController.registerPreviewEnhancer(pluginId, enhancer);
        }
    }

    @Override
    public void unregisterPreviewEnhancer(String pluginId) {
        if (editorController != null) {
            editorController.unregisterPreviewEnhancer(pluginId);
        }
    }

    void updateStatus(String message) {
        statusLabel.setText(message);
        // The label truncates with an ellipsis when narrow; expose the full text on hover.
        statusLabel.setTooltip(message != null && message.length() > 40 ? new Tooltip(message) : null);
    }

    private SaveDialogDecision showSaveDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(getString("dialog.save_changes.title"));
        alert.setHeaderText(getString("dialog.save_changes.header"));
        alert.setContentText(getString("dialog.save_changes.content"));

        ButtonType saveButton = new ButtonType(getString("action.save"));
        ButtonType dontSaveButton = new ButtonType(getString("action.dont_save"));
        ButtonType cancelButton = new ButtonType(getString("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveButton, dontSaveButton, cancelButton);
        com.example.jylos.ui.UiDialogs.apply(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty()) {
            return SaveDialogDecision.CANCEL;
        }
        if (result.get() == saveButton) {
            return SaveDialogDecision.SAVE;
        }
        if (result.get() == dontSaveButton) {
            return SaveDialogDecision.DONT_SAVE;
        }
        return SaveDialogDecision.CANCEL;
    }

    @FXML
    private void handleAddTagToNote() {
        tagManagement.handleAddTagToNote(
                getCurrentNote(),
                () -> {
                    if (sidebarController != null) {
                        sidebarController.loadTags();
                    }
                },
                this::reloadCurrentNoteTags);
    }

    private void removeTagFromNote(Tag tag) {
        tagManagement.removeTagFromNote(getCurrentNote(), tag, this::reloadCurrentNoteTags);
    }

    private void reloadCurrentNoteTags(Note note) {
        if (note != null && editorController != null) {
            editorController.loadNoteTagsForNote(note, this::handleAddTagToNote, this::removeTagFromNote);
        }
    }

    @FXML
    void handleNewNote(ActionEvent event) {
        try {
            noteCreationSupport.createNewNote();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create new note", e);
            updateStatus(getString("status.error_creating_note"));
        }
    }

    /**
     * Requests creating a new canvas. The actual creation lives in {@code NotesListController}
     * (it knows the selected folder); this just publishes the system action.
     */
    void handleNewCanvas(ActionEvent event) {
        if (eventBus != null) {
            eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.NEW_CANVAS));
        }
    }

    /** Opens today's daily note ({@code yyyy-MM-dd}), creating it (from a "daily" template if any). */
    private void handleDailyNote() {
        noteCreationSupport.openDailyNote();
    }

    /** Lets the user pick a template (notes under a "Templates" folder) and create a note from it. */
    private void handleNewFromTemplate() {
        noteCreationSupport.createFromTemplate();
    }

    @FXML
    void handleNewFolder(ActionEvent event) {
        boolean createInRoot = (currentFolder == null || "ALL_NOTES_VIRTUAL".equals(currentFolder.getId()));
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle(getString("dialog.new_folder.title"));
        dialog.setHeaderText(createInRoot
                ? getString("dialog.new_folder.header_root")
                : java.text.MessageFormat.format(getString("dialog.new_folder.header_sub"), currentFolder.getTitle()));
        dialog.setContentText(getString("dialog.new_folder.content"));
        styleDialog(dialog);

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        FolderOperations.FolderCreationResult creation = folderOperations.createFolder(
                result.get().trim(), currentFolder, createInRoot);
        if (!creation.success() || creation.folder() == null) {
            String detail = creation.errorMessage();
            updateStatus(detail != null && !detail.isBlank()
                    ? java.text.MessageFormat.format(getString("status.error_details"), detail)
                    : getString("status.error_creating_folder"));
            return;
        }

        if (sidebarController != null) {
            sidebarController.loadFolders();
        }
        if (sidebarController.getFolderTreeView() != null) {
            sidebarController.getFolderTreeView().refresh();
        }
        updateStatus(java.text.MessageFormat.format(getString("status.folder_created"), creation.folder().getTitle()));
    }

    @FXML
    private void handleImport(ActionEvent event) {
        documentWorkflowSupport.importFiles(!"sqlite".equals(prefs.get("storage_type", "sqlite")));
    }

    @FXML
    void handleSave(ActionEvent event) {
        if (editorController != null) {
            editorController.handleSave();
        }
        // Saving a note's content does not change which notes live in the current folder,
        // so the central notes list is NOT reloaded here — doing so (especially on every
        // autosave) caused a redundant reload that could race a cache prune and blank the
        // list. The recents/favorites lists (whose order may change) refresh via the
        // NoteSavedEvent the editor publishes.
    }

    /**
     * Called (on the FX thread) once the filesystem vault finishes loading note
     * contents in the background. Repaints the notes list and tags, and signals the
     * rest of the app (sidebar counts, graph, link/title indexes) to refresh with the
     * now-complete data. Safe to run more than once.
     */
    private void onVaultContentLoaded(long generation) {
        if (!isActiveBackendSession(generation)) {
            return;
        }
        try {
            refreshNotesList();
            if (sidebarController != null) {
                sidebarController.loadTags();
            }
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to refresh UI after vault content load", e);
        }
    }

    /** Refreshes the notes list according to the active filter (all / folder / tag / search) and view mode. */
    private void refreshNotesList() {
        if (notesListController == null) {
            return;
        }
        String searchText = toolbarController != null && toolbarController.getSearchField() != null
                ? toolbarController.getSearchField().getText()
                : "";
        notesListController.refreshNotesList(
                currentFilterType,
                currentFolder,
                currentTag,
                searchText,
                notesListController.isGridViewActive(),
                () -> {
                    if (sidebarController != null) {
                        sidebarController.loadFavorites();
                    }
                },
                this::refreshNotesGridIfActive);
    }

    @FXML
    void handleDelete(ActionEvent event) {
        if (eventBus != null) {
            eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.DELETE));
        }
    }

    @FXML
    private void handleExit(ActionEvent event) {
        if (isModified() && getCurrentNote() != null) {
            SaveDialogDecision decision = showSaveDialog();
            if (decision == SaveDialogDecision.CANCEL) {
                return;
            }
            if (decision == SaveDialogDecision.SAVE) {
                handleSave(event);
            }
        }

        shutdownApplication();
        Platform.exit();
        System.exit(0);
    }

    /** Cancels all event subscriptions, shuts down plugins, closes the executor and the database connection in orderly fashion. */
    public void shutdownApplication() {
        try {
            uiEventSubscriptions.forEach(EventBus.Subscription::cancel);
            uiEventSubscriptions.clear();
            if (systemActionSubscription != null) {
                systemActionSubscription.cancel();
            }
            pluginUiRefreshSubscription.cancel();
            if (editorController != null) {
                editorController.teardown();
            }
            if (graphViewController != null) {
                graphViewController.teardown();
            }
            if (notesListController != null) {
                notesListController.teardown();
            }
            if (sidebarController != null) {
                sidebarController.teardown();
            }
            if (backlinkService != null) {
                backlinkService.shutdown();
            }
            com.example.jylos.service.NoteTitleIndex.getInstance().shutdown();
            if (pluginManager != null) {
                pluginManager.shutdownAll();
            }
            com.example.jylos.plugin.PluginLoader.closeAllClassLoaders();
            quickSwitcherExecutor.shutdownNow();

            if (connection != null && !connection.isClosed()) {
                SQLiteDB db = SQLiteDB.getInstance();
                db.closeConnection(connection);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
    }

    @FXML
    private void handleExport(ActionEvent event) {
        documentWorkflowSupport.exportCurrentNote();
    }

    /** Exports every note to a chosen folder, one file each, in PDF or HTML. */
    private void handleExportVault() {
        documentWorkflowSupport.exportVault();
    }

    /** Exports a specific note to a user-chosen Markdown/text/HTML/PDF file. */
    private void exportNote(Note note) {
        documentWorkflowSupport.exportNote(note);
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        com.example.jylos.ui.UiDialogs.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    @FXML
    private void handleUndo(ActionEvent event) {
        editorController.performUndo();
    }

    @FXML
    private void handleRedo(ActionEvent event) {
        editorController.handleRedo(this::getString, this::updateStatus);
    }

    @FXML
    private void handleCut(ActionEvent event) {
        editorController.performCut();
    }

    @FXML
    private void handleCopy(ActionEvent event) {
        editorController.performCopy();
    }

    @FXML
    private void handlePaste(ActionEvent event) {
        editorController.performPaste();
    }

    @FXML
    private void handleFind(ActionEvent event) {
        editorController.performFind(this::getString, this::updateStatus);
    }

    @FXML
    private void handleReplace(ActionEvent event) {
        editorController.performReplace(this::getString, this::updateStatus);
    }

    /** Toggles focus / writing mode (logic + state in {@link FocusModeSupport}). */
    private void handleFocusMode() {
        focusModeSupport.toggle();
    }

    // ------------------------------------------------------------------
    // Workspaces — capture/restore the working context (logic in WorkspaceController)
    // ------------------------------------------------------------------

    /** Snapshots the current tabs, active note and basic layout into a live workspace. */
    private com.example.jylos.workspace.Workspace captureLiveWorkspace() {
        java.util.List<String> openIds = editorTabs != null ? editorTabs.getOpenIds() : java.util.List.of();
        String activeId = editorTabs != null ? editorTabs.getActiveId() : null;
        double splitMain = mainSplitPane != null && mainSplitPane.getDividers().size() > 0
                ? mainSplitPane.getDividerPositions()[0] : UiPreferencesStore.DEFAULT_SPLIT_MAIN;
        double splitContent = contentSplitPane != null && contentSplitPane.getDividers().size() > 0
                ? contentSplitPane.getDividerPositions()[0] : UiPreferencesStore.DEFAULT_SPLIT_CONTENT;
        boolean sidebarVisible = sidebarController != null && sidebarController.getSidebarPane() != null
                && sidebarController.getSidebarPane().isVisible();
        String storageMode = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        return com.example.jylos.workspace.WorkspaceService.liveState(openIds, activeId,
                currentViewMode.name(), sidebarVisible, focusModeSupport.isActive(),
                splitMain, splitContent, storageMode);
    }

    /**
     * Restores a workspace: reopens its still-existing notes as tabs, activates the saved
     * active note, and reapplies the basic layout. Missing notes are skipped with a
     * non-blocking status warning; a storage-mode mismatch is also flagged. Existing tabs
     * are kept (open is additive — it never closes notes you already had open).
     */
    private void applyWorkspace(com.example.jylos.workspace.Workspace ws) {
        if (ws == null || noteService == null) {
            return;
        }
        String storageMode = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        if (ws.storageMode() != null && !ws.storageMode().isBlank()
                && !ws.storageMode().equalsIgnoreCase(storageMode)) {
            updateStatus(getString("workspace.status.storage_mismatch"));
        }

        int missing = 0;
        String lastOpened = null;
        for (String id : ws.openNoteIds()) {
            java.util.Optional<Note> note = noteService.getNoteById(id);
            if (note.isPresent()) {
                loadNoteInEditor(note.get());
                lastOpened = id;
            } else {
                missing++;
            }
        }
        // Activate the saved active note when it still exists, else the last one opened.
        String target = ws.activeNoteId() != null && noteService.getNoteById(ws.activeNoteId()).isPresent()
                ? ws.activeNoteId() : lastOpened;
        if (target != null) {
            openNoteInTab(target);
        }

        applyWorkspaceLayout(ws);

        if (missing > 0) {
            updateStatus(java.text.MessageFormat.format(getString("workspace.status.missing"), missing));
        } else {
            updateStatus(java.text.MessageFormat.format(getString("workspace.status.opened"), ws.name()));
        }
    }

    /** Reapplies a workspace's basic layout (view mode, focus, splits, sidebar). */
    private void applyWorkspaceLayout(com.example.jylos.workspace.Workspace ws) {
        try {
            currentViewMode = UiLayout.ViewMode.valueOf(ws.viewMode());
            applyViewMode();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // unknown/blank view mode → leave the current one
        }
        if (focusModeSupport.isActive() != ws.focusMode()) {
            focusModeSupport.toggle();
        }
        if (ws.splitMain() > 0 && mainSplitPane != null && mainSplitPane.getDividers().size() > 0) {
            mainSplitPane.setDividerPositions(ws.splitMain());
        }
        if (ws.splitContent() > 0 && contentSplitPane != null && contentSplitPane.getDividers().size() > 0) {
            contentSplitPane.setDividerPositions(ws.splitContent());
        }
        boolean sidebarVisible = sidebarController != null && sidebarController.getSidebarPane() != null
                && sidebarController.getSidebarPane().isVisible();
        if (sidebarVisible != ws.sidebarVisible() && !isStackedLayout) {
            handleToggleSidebar(null);
        }
    }

    /**
     * Opens the Knowledge Insights dialog: a read-only analytics view (orphans, broken
     * links, most-connected notes, tag usage and a health score) over the current
     * vault/database. Computation runs off the FX thread inside the dialog. Clicking a
     * row opens that note. Works in both SQLite and Markdown-vault modes.
     */
    private void showKnowledgeInsights() {
        if (noteService == null || tagService == null) {
            return;
        }
        var service = new com.example.jylos.insights.KnowledgeInsightsService(noteService, tagService);
        javafx.scene.Scene scene = centerStack != null ? centerStack.getScene() : null;
        new com.example.jylos.ui.components.KnowledgeInsightsPanel(
                service, this::getString, this::openNoteInTab, scene).show();
    }

    void handleToggleSidebar(ActionEvent event) {
        navigationCommand.toggleSidebar(
                isStackedLayout,
                navSplitPane,
                sidebarController.getSidebarPane(),
                mainSplitPane,
                toolbarController);
    }

    @FXML
    void handleToggleNotesPanel(ActionEvent event) {
        navigationCommand.toggleNotesPanel(
                isStackedLayout,
                notesPanel,
                contentSplitPane,
                toolbarController,
                () -> handleToggleSidebar(event));
    }

    @FXML
    private void handleZoomIn(ActionEvent event) {
        uiFontSize = navigationCommand.zoomIn(uiFontSize);
        applyUiZoom();
    }

    @FXML
    private void handleZoomOut(ActionEvent event) {
        uiFontSize = navigationCommand.zoomOut(uiFontSize);
        applyUiZoom();
    }

    @FXML
    private void handleResetZoom(ActionEvent event) {
        uiFontSize = navigationCommand.resetUiZoom();
        applyUiZoom();
    }

    /**
     * Applies the root inline style: UI font size plus the optional custom accent.
     * Inline styles on the scene root override the theme stylesheets' looked-up
     * colors, so a custom {@code -fx-accent} recolors every accent-driven control
     * (selection, focus, toggles) in built-in and external themes alike.
     */
    private void applyUiZoom() {
        if (toolbarController != null && toolbarController.getToolbarHBox() != null
                && toolbarController.getToolbarHBox().getScene() != null) {
            StringBuilder style = new StringBuilder("-fx-font-size: ").append(uiFontSize).append("px;");
            if (uiAccentColor != null && !uiAccentColor.isBlank()) {
                style.append(" -fx-accent: ").append(uiAccentColor).append(';')
                     .append(" -fx-accent-hover: derive(").append(uiAccentColor).append(", -12%);")
                     .append(" -fx-selected-bg: ").append(uiAccentColor).append(';');
            }
            toolbarController.getToolbarHBox().getScene().getRoot().setStyle(style.toString());
        }
        // Keep the persisted font size in sync so Ctrl+/- zoom survives a restart.
        prefs.putInt(UiPreferencesStore.UI_FONT_SIZE_KEY, (int) Math.round(uiFontSize));
    }

    @FXML
    private void handleEditorZoomIn(ActionEvent event) {
        editorFontSize += 1.0;
        applyEditorZoom();
    }

    @FXML
    private void handleEditorZoomOut(ActionEvent event) {
        if (editorFontSize > 8.0) {
            editorFontSize -= 1.0;
            applyEditorZoom();
        }
    }

    @FXML
    private void handleEditorResetZoom(ActionEvent event) {
        editorFontSize = 14.0; // Default
        applyEditorZoom();
    }

    private void applyEditorZoom() {
        if (editorController != null) {
            editorController.applyEditorZoom(editorFontSize);
        }
    }

    @FXML
    void handleViewLayoutSwitch(ActionEvent event) {
        isStackedLayout = navigationCommand.switchLayout(
                isStackedLayout,
                mainSplitPane,
                contentSplitPane,
                navSplitPane,
                sidebarController.getSidebarPane(),
                notesPanel,
                editorRightSplitPane,
                toolbarController);
    }

    @FXML
    private void handleToggleTags(ActionEvent event) {
        if (editorController == null) {
            return;
        }
        boolean show = true;
        ToggleButton toggleBtn = editorController.getToggleTagsBtn();
        VBox tagsBox = editorController.getTagsContainer();
        if (toggleBtn != null) {
            show = toggleBtn.isSelected();
        } else if (tagsBox != null) {
            show = !tagsBox.isVisible();
        }
        if (tagsBox != null) {
            tagsBox.setVisible(show);
            tagsBox.setManaged(show);
            updateStatus(show ? getString("status.tags_bar_shown") : getString("status.tags_bar_hidden"));
        }
    }

    private static final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    private String currentTheme = prefs.get("theme", "light"); // Load from preferences

    @FXML
    private void handleLightTheme(ActionEvent event) {
        currentTheme = themeCommand.setLightTheme(prefs);
        updateThemeMenuSelection();
        applyThemeAndRefreshDependents();
        updateStatus(getString("status.theme_light"));
    }

    @FXML
    private void handleDarkTheme(ActionEvent event) {
        currentTheme = themeCommand.setDarkTheme(prefs);
        updateThemeMenuSelection();
        applyThemeAndRefreshDependents();
        updateStatus(getString("status.theme_dark"));
    }

    @FXML
    private void handleSystemTheme(ActionEvent event) {
        ThemeCommand.SystemThemeResult result = themeCommand.setSystemTheme(prefs);
        currentTheme = result.currentTheme();
        updateThemeMenuSelection();
        applyThemeAndRefreshDependents();
        updateStatus(java.text.MessageFormat.format(getString("status.theme_system"), result.detectedTheme()));
    }

    /** Applies the resolved theme stylesheet to the scene and synchronizes dialog stylesheets via {@link com.example.jylos.ui.UiDialogs}. */
    private void applyTheme() {
        if (mainSplitPane == null || editorController == null) {
            return;
        }
        javafx.scene.Scene scene = mainSplitPane.getScene();
        if (scene == null) {
            Platform.runLater(this::applyTheme);
            return;
        }
        Runnable refreshPreview = getCurrentNote() != null ? this::refreshEditorPreview : null;
        themeCommand.applyTheme(
                scene,
                currentTheme,
                themeSource,
                externalThemeId,
                themeCatalog,
                getClass(),
                editorController.getPreviewWebView(),
                refreshPreview,
                cssSnippetCatalog.resolveEnabledUris(enabledSnippets));
        // Make modals/alerts follow the active theme (JavaFX dialogs don't inherit
        // the scene's stylesheets on their own).
        com.example.jylos.ui.UiDialogs.syncFromScene(scene);
    }

    /** Applies the theme, refreshes all theme-dependent UI elements, and updates the system-theme monitor state. */
    private void applyThemeAndRefreshDependents() {
        applyTheme();
        refreshUiThemeDependents();
        syncSystemThemeMonitoring();
    }

    /** Enables or disables the OS system-theme monitor based on whether the "system" built-in mode is currently active. */
    private void syncSystemThemeMonitoring() {
        systemThemeMonitor.setActive(ThemeCommand.isSystemBuiltinMode(currentTheme, themeSource));
    }

    private void installSystemThemeFocusRefresh() {
        Platform.runLater(() -> {
            if (mainSplitPane == null || mainSplitPane.getScene() == null) {
                return;
            }
            javafx.stage.Window window = mainSplitPane.getScene().getWindow();
            if (window == null) {
                return;
            }
            window.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!Boolean.TRUE.equals(isFocused)) {
                    return;
                }
                if (!ThemeCommand.isSystemBuiltinMode(currentTheme, themeSource)) {
                    return;
                }
                systemThemeMonitor.refreshOnFocus();
                applyThemeAndRefreshDependents();
            });
        });
    }

    /** Refreshes theme-sensitive UI sub-systems: editor preview, notes grid and the graph overlay. */
    private void refreshUiThemeDependents() {
        if (editorController != null) {
            editorController.refreshPreview(isDarkThemeActive());
        }
        refreshNotesGridIfActive();
        overlaySupport.applyGraphThemeIfVisible();
    }

    // ============================================================
    // Private (encrypted) notes — Fase 4
    // ============================================================

    /** Toggles the current note between private (encrypted body) and public. */
    private void handleTogglePrivate() {
        Note note = getCurrentNote();
        if (note == null || note.getId() == null) {
            updateStatus(getString("status.no_note_selected"));
            return;
        }
        toggleNotePrivacy(note);
    }

    /**
     * Turns {@code target} private (encrypt its body) or public (decrypt and store as
     * plaintext). Works for any note — the current editor note or one picked from a list's
     * context menu — and acquires/reveals the key only as the chosen direction needs:
     * making private needs the key to encrypt; making public needs to read (reveal) the note.
     */
    void toggleNotePrivacy(Note target) {
        if (target == null || target.getId() == null || noteService == null) {
            updateStatus(getString("status.no_note_selected"));
            return;
        }
        EncryptionService enc = EncryptionService.getInstance();
        boolean makePrivate = !target.isPrivate();

        if (makePrivate) {
            if (!enc.isConfigured()) {
                if (!privacySupport.setupMasterPassword()) {
                    return;
                }
            } else if (!privacySupport.ensureKey()) {
                return; // need the key to encrypt
            }
        } else if (!enc.canRead(target.getId()) && !privacySupport.promptRevealNote(target.getId())) {
            return; // need to decrypt the note to turn it public
        }

        // Resolve the plaintext body. For the open note use the live editor text, but never
        // persist the 🔒 placeholder — fall back to the (now readable) stored content.
        Note current = getCurrentNote();
        boolean isCurrent = current != null && target.getId().equals(current.getId());
        String content = isCurrent ? editorController.getCurrentContent() : null;
        if (content == null || NoteService.LOCKED_PLACEHOLDER.equals(content)) {
            content = noteService.getNoteById(target.getId()).map(Note::getContent).orElse(content);
        }
        if (content == null || NoteService.LOCKED_PLACEHOLDER.equals(content)) {
            updateStatus(getString("status.unlock_failed"));
            return;
        }

        target.setPrivate(makePrivate);
        target.setContent(content);
        noteService.updateNote(target); // NoteService encrypts when private + key held
        if (makePrivate) {
            enc.reveal(target.getId()); // keep it readable this session (just created it)
        }
        eventBus.publish(new NoteEvents.NoteSavedEvent(target));
        // Reflect the new state in the editor when this is the open note.
        if (isCurrent) {
            noteService.getNoteById(target.getId()).ifPresent(editorController::loadNote);
        }
        refreshNotesList();
        updateStatus(getString(makePrivate ? "status.note_private_on" : "status.note_private_off"));
    }

    /** Global unlock: prompts for the master password and reveals all private notes. */
    private void handleUnlockNotes() {
        EncryptionService enc = EncryptionService.getInstance();
        if (!enc.isConfigured()) {
            updateStatus(getString("status.no_private_notes"));
            return;
        }
        if (enc.isUnlocked()) {
            updateStatus(getString("status.unlocked"));
            return;
        }
        if (privacySupport.promptUnlockAll()) {
            // Reload the open note so a previously-locked body shows decrypted.
            Note current = getCurrentNote();
            if (current != null && current.isPrivate() && current.getId() != null) {
                noteService.getNoteById(current.getId()).ifPresent(editorController::loadNote);
            }
            refreshNotesList();
        }
    }

    /** Locks encryption: private notes become unreadable until unlocked again. */
    private void handleLockNotes() {
        EncryptionService enc = EncryptionService.getInstance();
        if (!enc.isConfigured()) {
            updateStatus(getString("status.no_private_notes"));
            return;
        }
        enc.lock();
        // Reload the open private note straight to its 🔒 placeholder (no unlock prompt).
        Note current = getCurrentNote();
        if (current != null && current.isPrivate() && current.getId() != null) {
            noteService.getNoteById(current.getId()).ifPresent(editorController::loadNote);
        }
        refreshNotesList();
        updateStatus(getString("status.notes_locked"));
    }


    /** Delegates OS dark/light detection to {@link ThemeCommand} and returns "dark" or "light". */
    private String detectSystemTheme() {
        return themeCommand.detectSystemTheme();
    }

    /** Returns "external" when an external theme is selected, otherwise delegates to {@link ThemeCommand#resolveThemeToApply}. */
    private String resolveThemeToApply() {
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(themeSource)) {
            return "external";
        }
        return themeCommand.resolveThemeToApply(currentTheme);
    }

    /** Returns {@code true} when the currently active theme (built-in or external) renders as a dark UI. */
    private boolean isDarkThemeActive() {
        ThemeCatalog.ThemeDescriptor external = null;
        if (UiPreferencesStore.THEME_SOURCE_EXTERNAL.equals(
                ThemeCommand.effectiveThemeSource(currentTheme, themeSource))) {
            external = themeCatalog.findById(themeCatalog.getAvailableThemes(), externalThemeId);
        }
        return themeCommand.resolveDarkUi(currentTheme, themeSource, external);
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        navigationCommand.handleSearch(toolbarController);
    }

    @FXML
    private void handleTagsManager(ActionEvent event) {
        tagManagement.showTagsManager(() -> {
            if (sidebarController != null) {
                sidebarController.loadTags();
            }
        });
    }

    @FXML
    private void handlePreferences(ActionEvent event) {
        UiPreferencesStore.UiPreferencesData currentUiPrefs = new UiPreferencesStore.UiPreferencesData(
                autosaveEnabled,
                autosaveIdleMs,
                themeSource,
                externalThemeId,
                notesListPreviewLines,
                (int) Math.round(uiFontSize),
                uiAccentColor);
        List<ThemeCatalog.ThemeDescriptor> themes = themeCatalog.getAvailableThemes();
        Optional<DialogSupport.PreferencesDialogResult> result = dialogSupport.showPreferences(
                currentUiPrefs,
                themes,
                cssSnippetCatalog,
                enabledSnippets);
        if (result.isEmpty()) {
            return;
        }
        DialogSupport.PreferencesDialogResult values = result.get();
        UiPreferencesStore.UiPreferencesData newPrefs = new UiPreferencesStore.UiPreferencesData(
                values.autosaveEnabled(),
                values.autosaveIdleMs(),
                values.themeSource(),
                values.externalThemeId(),
                values.notesPreviewLines(),
                values.uiFontSize(),
                values.accentColor());
        uiPreferences.save(prefs, newPrefs);
        uiPreferences.saveEnabledSnippets(prefs, values.enabledSnippets());
        applyUiPreferencesFromStore();
        updateThemeMenuSelection();
        syncSystemThemeMonitoring();
        updateStatus(getString("status.preferences_saved"));
    }

    @FXML
    private void handleDocumentation(ActionEvent event) {
        dialogSupport.showDocumentation();
    }

    @FXML
    void showKeyboardShortcutsHelp() {
        dialogSupport.showKeyboardShortcuts();
    }

    @FXML
    private void handleKeyboardShortcuts(ActionEvent event) {
        showKeyboardShortcutsHelp();
    }

    @FXML
    private void handleAbout(ActionEvent event) {
        dialogSupport.showAbout();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        if (noteService != null) {
            noteService.refreshStorageCache();
        }
        refreshOpenNoteAfterStorageRefresh();
        navigationCommand.refreshByContext(
                currentFilterType,
                currentFolder,
                currentTag,
                notesListView,
                noteCountLabel,
                this::refreshNotesList,
                this::handleFolderSelection,
                this::loadNotesForTag,
                filterType -> toolbarController != null && toolbarController.getSearchField() != null
                        ? toolbarController.getSearchField().getText()
                        : "",
                this::performSearch,
                message -> {
                    if ("favorites".equals(currentFilterType)) {
                        currentFilterType = "favorites";
                        currentFolder = null;
                        currentTag = null;
                        sortNotes(sortComboBox.getValue());
                    }
                    updateStatus(message);
                });
    }

    private void refreshOpenNoteAfterStorageRefresh() {
        Note current = getCurrentNote();
        if (current == null || current.getId() == null || noteService == null || editorController == null) {
            return;
        }
        java.util.Optional<Note> refreshed = noteService.getNoteById(current.getId());
        if (refreshed.isEmpty()) {
            return;
        }
        Note latest = refreshed.get();
        if (!isModified()) {
            loadNoteInEditor(latest);
            return;
        }
        current.setFavorite(latest.isFavorite());
        current.setPinned(latest.isPinned());
        current.setPrivate(latest.isPrivate());
        current.setDeleted(latest.isDeleted());
        current.setDeletedDate(latest.getDeletedDate());
        current.setStatus(latest.getStatus());
        updateNoteMetadata(current);
        editorController.syncFavoritePinButtons(this::getString);
    }

    @FXML
    private void handleToggleFavorite(ActionEvent event) {
        if (getCurrentNote() == null) {
            updateStatus(getString("status.no_note"));
            return;
        }
        toggleFavorite(getCurrentNote());
    }

    private void toggleFavorite(Note note) {
        if (note == null)
            return;
        try {
            boolean newState = noteService.toggleFavorite(note);
            if (getCurrentNote() != null && getCurrentNote().getId().equals(note.getId())) {
                editorController.syncFavoritePinButtons(this::getString);
            }
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteSavedEvent(note));
            }
            if ("favorites".equals(currentFilterType)) {
                refreshNotesList();
            }
            updateStatus(getString(newState ? "status.note_marked_favorite" : "status.note_unmarked_favorite"));
        } catch (Exception e) {
            updateStatus(getString("status.fav_error"));
        }
    }

    @FXML
    private void handleTogglePin(ActionEvent event) {
        if (getCurrentNote() == null) {
            updateStatus(getString("status.no_note"));
            return;
        }
        togglePin(getCurrentNote());
    }

    private void togglePin(Note note) {
        if (note == null)
            return;
        try {
            boolean newState = noteService.togglePinned(note);
            if (getCurrentNote() != null && getCurrentNote().getId().equals(note.getId())) {
                editorController.syncFavoritePinButtons(this::getString);
            }
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteSavedEvent(note));
            }
            if (notesListController != null) {
                notesListController.resortVisibleNotes();
            }
            refreshNotesGridIfActive();
            updateStatus(getString(newState ? "status.note_pinned" : "status.note_unpinned"));
        } catch (Exception e) {
            updateStatus(getString("status.pin_error"));
        }
    }

    @FXML
    void handleNewTag(ActionEvent event) {
        dialogSupport.handleNewTag(() -> {
            if (sidebarController != null) {
                sidebarController.loadTags();
            }
        });
    }

    private void handleSaveAll(ActionEvent event) {
        if (editorController != null) {
            editorController.handleSave();
        }
        updateStatus(getString("status.saved_all"));
    }

    /** Publishes a {@link SystemActionEvent} of the given type to the editor via the event bus. */
    private void publishEditorAction(SystemActionEvent.ActionType actionType) {
        if (editorController != null && eventBus != null && actionType != null) {
            editorController.publishAction(eventBus, actionType);
        }
    }

    private void handleSystemAction(SystemActionEvent event) {
        javafx.application.Platform.runLater(() -> {
            if (event == null || event.getActionType() == null) {
                return;
            }
            Runnable handler = systemActionHandlers.get(event.getActionType());
            if (handler != null) {
                handler.run();
            }
        });
    }
}
