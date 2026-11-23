package com.example.jylos.ui.controller;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.VaultSettingsStore;
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
import com.example.jylos.insights.KnowledgeInsightsService;
import com.example.jylos.plugin.EditorHooks;
import com.example.jylos.plugin.PluginLoader;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.ImportService;
import com.example.jylos.service.NoteHistoryService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.NoteTitleIndex;
import com.example.jylos.service.TagService;
import com.example.jylos.ui.NoteIcons;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.CommandPalette;
import com.example.jylos.ui.components.EditorTabs;
import com.example.jylos.ui.components.SideDock;
import com.example.jylos.ui.components.EditorTabsModel;
import com.example.jylos.ui.components.ViewTabs;
import com.example.jylos.ui.components.GitSyncPanel;
import com.example.jylos.ui.components.KnowledgeInsightsPanel;
import com.example.jylos.ui.components.PluginManagerDialog;
import com.example.jylos.ui.components.QuickSwitcher;
import com.example.jylos.ui.preferences.LayoutMode;
import com.example.jylos.ui.preferences.UiPreferencesStore;
import com.example.jylos.ui.theme.UiSizeScale;
import com.example.jylos.util.NoteAnchors;
import com.example.jylos.util.SystemBrowser;
import com.example.jylos.util.WikiLinkResolver;
import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceService;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Root FXML controller: coordinates sidebar, notes list, editor, plugins, themes,
 * and {@link com.example.jylos.event.EventBus} subscriptions. Business logic
 * for persistence is delegated to DAOs and {@link com.example.jylos.service.NoteService}
 * where possible; this class focuses on UI state and command routing.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class MainController {

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
    private EditorTabs editorTabs;

    private VBox notesPanel;
    private ComboBox<String> sortComboBox;
    private ListView<Note> notesListView;

    private final CommandRouting commandRouting = new CommandRouting();
    private final Map<SystemActionEvent.ActionType, Runnable> systemActionHandlers = new EnumMap<>(
            SystemActionEvent.ActionType.class);
    @FXML
    private SplitPane centerRightSplitPane;
    @FXML
    private VBox editor;
    @FXML
    private VBox rightPanel;
    @FXML
    private HBox statusBar;
    @FXML
    private StackPane centerStack;
    /** The editor area: the open note, or the view of a view tab such as the graph. */
    @FXML
    private StackPane editorArea;

    private final FocusModeSupport focusModeSupport = new FocusModeSupport();
    /** The right panel's views, in tabs (see {@link RightPanelSupport} and {@link PluginUi}). */
    @FXML
    private SideDock rightDock;
    private final RightPanelSupport rightPanelSupport = new RightPanelSupport();

    @FXML
    private Label statusLabel;
    @FXML
    private Label updateIndicator;
    @FXML
    private Label noteCountLabel;
    @FXML
    private Label storageLabel;
    @FXML
    private Label wordCountLabel;
    @FXML
    private Label charCountLabel;
    @FXML
    private HBox editorIndicatorsBox;
    @FXML
    private Label saveStateLabel;
    @FXML
    private Label editorModeLabel;
    @FXML
    private Separator statsSeparator;
    @FXML
    private Separator wordCharSeparator;
    @FXML
    private Separator gitSeparator;
    @FXML
    private HBox gitBar;
    @FXML
    private Separator statusMessageSeparator;
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
    private final VaultCleanupSupport vaultCleanup = new VaultCleanupSupport();
    private final ObsidianImportSupport obsidian = new ObsidianImportSupport();
    private final PrivacySupport privacySupport = new PrivacySupport();
    private final VaultLocationSupport vaultLocationSupport = new VaultLocationSupport();
    /** The editor's tabs (notes, the graph, new tabs) and their back/forward; and the Kanban board. */
    private final TabsSupport tabsSupport = new TabsSupport();
    /** Which Kanban board to open, or a new one. */
    private final KanbanPickerSupport kanbanPicker = new KanbanPickerSupport(this::getPrimaryStage,
            () -> this.resources, this::handleUiNoteOpenRequest, () -> this.noteCreationSupport.createNewBoard());
    private final StatusBarSupport statusBarSupport = new StatusBarSupport();
    private final BacklinksSupport backlinksSupport = new BacklinksSupport();
    /** Editor hook dispatcher shared by the plugin system and the editor. */
    private final EditorHooks editorHooks = new EditorHooks();
    private final ImportSupport importSupport = new ImportSupport();
    // REMOVABLE: in-app updater — a workaround for shipping unsigned installers, not
    // meant to be permanent. Delete alongside every other spot tagged "REMOVABLE:
    // in-app updater" (grep the repo for that exact string) once Jylos can afford
    // real code signing/notarization. Full rationale and removal checklist in
    // docs/PACKAGING.md, section "In-app updater (unsigned builds)".
    private final UpdateInstallSupport updateInstallSupport = new UpdateInstallSupport();
    private final HistorySupport historySupport = new HistorySupport();
    /** The status bar's area for plugin items (see {@link PluginUi}). */
    @FXML
    private HBox pluginStatusBarContainer;



    private CommandPalette commandPalette;
    private QuickSwitcher quickSwitcher;

    private NoteService noteService;
    private FolderService folderService;
    /** The open vault's own settings: templates, daily note, new notes. */
    private VaultSettingsStore vaultSettings;
    private TagService tagService;
    private BacklinkService backlinkService;
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
    /** The window's theme: light, dark or system, built-in or external, and CSS snippets. */
    private final ThemeSupport theme = new ThemeSupport();
    private final UiPreferencesStore uiPreferences = new UiPreferencesStore();
    private final PluginUi pluginUi = new PluginUi();
    private final AppSettings appSettings = new AppSettings();
    private final List<EventBus.Subscription> uiEventSubscriptions = new ArrayList<>();
    private EventBus.Subscription systemActionSubscription = EventBus.Subscription.NO_OP;
    private EventBus.Subscription pluginUiRefreshSubscription = EventBus.Subscription.NO_OP;

    @FXML
    private ResourceBundle resources;

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

    /** The back and forward arrows. */
    private final ExecutorService quickSwitcherExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jylos-quick-switcher-loader");
        t.setDaemon(true);
        return t;
    });
    /** Update checks, the update toast and its status-bar indicator. */
    private final UpdateNotificationSupport updateNotifications = new UpdateNotificationSupport();
    private final AtomicLong quickSwitcherLoadVersion = new AtomicLong(0);
    private final AtomicLong backendSessionGeneration = new AtomicLong(0);
    private final VaultWatchSupport vaultWatch = new VaultWatchSupport();
    private final StartupSupport startupSupport = new StartupSupport();
    private volatile List<Note> quickSwitcherNotesCache = List.of();
    private volatile long activeBackendSessionGeneration = 0L;
    private int notesListPreviewLines = UiPreferencesStore.DEFAULT_NOTES_PREVIEW_LINES;
    private boolean autosaveEnabled = true;
    private int autosaveIdleMs = UiPreferencesStore.DEFAULT_AUTOSAVE_IDLE_MS;
    private boolean autosaveRunning = false;

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
            navSplitPane.setOrientation(Orientation.VERTICAL);

            initializeBackendSession();
            appSettings.wire(this::getString);
            pluginUi.wire(this::getString, this::updateStatus, this::getPluginManager, () -> toolbarController,
                    rightDock, pluginStatusBarContainer);
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
                toolbarController.setLayoutModeAction(this::setLayoutMode);
            }
            initializeCommandRouting();
            initializeSystemActionHandlers();
            if (eventBus != null) {
                systemActionSubscription.cancel();
                systemActionSubscription = eventBus.subscribe(SystemActionEvent.class, this::handleSystemAction);
                subscribeToShellAndDomainEvents();
            }
            bindBackendSession();
            // After bindBackendSession(): it is what fetches the notes panel from its controller.
            uiLayout.wire(new UiLayout.Panes(mainSplitPane, contentSplitPane, navSplitPane, centerRightSplitPane,
                    sidebarController != null ? sidebarController.getSidebarPane() : null, notesPanel, editorArea,
                    rightPanel), () -> toolbarController, this::getString, this::updateStatus);

            bindToolbarSearchFieldDebounced();

            initializeSortOptions();
            initializeEditorPresentation();
            uiLayout.collapseRightPanel();
            setupToolbarResponsiveness();
            initializeLanguageMenu();
            applyUiPreferencesFromStore();
            initializeThemeMenu();
            installSystemThemeFocusRefresh();
            applyLayoutMode(uiPreferences.loadLayoutMode(prefs), false);
            setupSplitPanePersistence();

            refreshBackendSessionViews(false);
            // The tabs open when the app last closed, and the workspace in use, come back.
            Platform.runLater(() -> startupSupport.start(workspaceController));

            Platform.runLater(this::initializeKeyboardShortcuts);

            Platform.runLater(this::initializePluginSystem);
            focusModeSupport.wire(mainSplitPane, contentSplitPane,
                    () -> toolbarController != null ? toolbarController.getToolbarHBox() : null,
                    statusBar, rightPanel,
                    () -> editorController != null ? editorController.getEditorContainer() : null,
                    prefs, this::getString, this::updateStatus, centerStack);
            updateStatus(getString("status.ready"));
            updateNotifications.wire(centerStack, updateIndicator, this::getString, this::updateStatus,
                    this::openExternalUrl, updateInstallSupport::installUpdate);
            updateNotifications.checkAtStartup();
            logger.info("MainController initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize MainController", e);
            updateStatus(MessageFormat.format(getString("status.error_details"), e.getMessage()));
        }
    }
    /** Selects the configured storage backend, opens the active session, then builds all DAOs and services. */
    private void initializeBackendSession() {
        try {

            String storageType = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));

            FactoryDAO factory;
            if ("filesystem".equalsIgnoreCase(storageType)) {
                vaultLocationSupport.wire(this::getString);
                String dataDir = vaultLocationSupport.chooseVaultDirectory(prefs,
                        AppDataDirectory.getDataDirectory(), getMainWindow());
                logger.info("Using File System Storage at " + dataDir);

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
            // The master-password key material lives with the notes it protects (vault or
            // database); this also locks anything unlocked in a previous vault session.
            NoteDAO sessionNoteDAO = noteDAO;
            EncryptionService.getInstance().wire(factory.getEncryptionKeyDAO(),
                    EncryptionService.legacyUserPreferencesStore(), sessionNoteDAO::hasPrivateNotes);
            noteService = new NoteService(noteDAO, folderDAO);
            noteService.setHistoryService(new NoteHistoryService(
                    Path.of(AppDataDirectory.getBaseDirectory(), "history")));
            vaultSettings = factory.getVaultSettingsStore();
            folderService = new FolderService(folderDAO, noteDAO, factory.getFolderColorStore(), vaultSettings);
            tagService = new TagService(tagDAO, noteDAO);
            noteOperations = new NoteOperations(noteService, folderService);
            eventBus = EventBus.getInstance();
            backlinkService = new BacklinkService(noteService, eventBus);
            NoteTitleIndex titleIndex =
                    NoteTitleIndex.getInstance();
            titleIndex.wire(noteService, eventBus);
            noteService.setNoteTitleIndex(titleIndex);

            logger.info("Backend session initialized");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize backend session", e);
            throw new RuntimeException("Backend session initialization failed", e);
        }
    }

    @FXML
    public void handleSwitchStorage() {
        appSettings.handleSwitchStorage(
                getMainWindow(),
                prefs,
                this::switchFilesystemVault);
    }

    /** Builds the theme items (light / dark / system) of the View menu and applies the saved preference. */
    private void initializeThemeMenu() {
        theme.wire(prefs, () -> mainSplitPane != null && editorController != null,
                () -> mainSplitPane != null ? mainSplitPane.getScene() : null,
                () -> editorController != null ? editorController.getPreviewWebView().node() : null,
                () -> getCurrentNote() != null ? this::refreshEditorPreview : null,
                this::refreshUiThemeDependents, () -> toolbarController, appSettings, this::getString,
                this::updateStatus);
        theme.initializeMenu();
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
        theme.updateMenuSelection();
    }

    private void bindBackendSession() {
        navigationCommand.wire(this::getString, this::updateStatus);
        documentSupport.wire(noteService, folderService);
        dialogSupport.wire(this::getString, this::updateStatus, tagService);
        tagManagement.wire(this::getString, this::updateStatus, tagService);
        folderOperations.wire(folderService);

        if (sidebarController != null) {
            sidebarController.wire(eventBus, noteService, tagService, folderService, resources,
                    this::handleUiFolderSelected, this::handleUiTagSelected, this::handleUiTrashItemSelected,
                    this::handleUiNoteOpenRequest,
                    noteCreationSupport::createNewNoteInFolder,
                    noteCreationSupport::createNewCanvasInFolder,
                    this::updateStatus);
            sidebarController.setOpenInNewTabAction(this::openNoteInNewTab);
            sidebarController.setCreateBoardInFolderAction(noteCreationSupport::createNewBoardInFolder);
            sidebarController.setExplorerOpenAction(this::loadNoteInEditor);
            sidebarController.setExplorerFolderContextAction(folder -> currentFolder = folder);
        }
        if (notesListController != null) {
            notesListController.wire(eventBus, noteService, tagService, folderService, resources,
                    this::loadNoteInEditor, this::exportNote, privacySupport::toggle,
                    this::handleUiNotesLoaded, this::updateStatus, pluginUi::getNoteContextMenuItems);
            notesListController.setOpenInNewTabAction(this::openNoteInNewTab);
            notesListController.setCloseResultsAction(this::closeResultsPanel);
            notesListController.setCurrentNoteSupplier(this::getCurrentNote);
            if (sidebarController != null) {
                sidebarController.setExplorerNoteMenu(
                        note -> notesListController.noteContextMenu(note, sidebarController::renameInExplorer));
                sidebarController.setExplorerRenameNoteAction(notesListController::renameNoteTo);
            }
            notesPanel = notesListController.getNotesPanel();
            sortComboBox = notesListController.getSortComboBox();
            notesListView = notesListController.getNotesListView();
        }
        if (editorController != null) {
            editorController.wire(eventBus, noteService, tagService, resources,
                    this::handleUiNoteModified, this::updateStatus,
                    NoteTitleIndex.getInstance()::linkSuggestions,
                    uiPreferences.loadLivePreviewEnabled(prefs),
                    enabled -> uiPreferences.saveLivePreviewEnabled(prefs, enabled),
                    // A link opens the note it names by path, resolved from the note it is
                    // written in, and scrolls to its heading or block when it names one.
                    this::openLink, this::loadNotesForTag, editorHooks);
            editorController.viewModeProperty().addListener((obs, old, mode) -> refreshEditorIndicators());
            if (editorTabs == null) {
                editorController.initializeTagsBarCollapsed();
                editorTabs = new EditorTabs(
                        editorController.getEditorTabBar(),
                        editorController.getEditorTabScroll(),
                        new EditorTabs.Listener() {
                            @Override public void onSelect(String id) {
                                tabsSupport.select(id);
                            }
                            @Override public void onClose(String id) {
                                tabsSupport.close(id);
                            }
                        },
                        this::getString);
                editorTabs.setAlwaysNewTab(uiPreferences.load(prefs).alwaysOpenInNewTab());
                editorTabs.setNewTabAction(tabsSupport::newTab);
                if (toolbarController != null && toolbarController.getTabsHost() != null && editor != null) {
                    // Tabs in the top bar, as in Obsidian, starting above the editor.
                    TopBarTabsSupport.install(toolbarController.getTabsHost(),
                            editorController.getEditorTabScroll(), editorArea);
                }
            }
            editorController.formatToolbar().bind(uiPreferences.loadShowFormatToolbar(prefs),
                    shown -> uiPreferences.saveShowFormatToolbar(prefs, shown));
        }
        tabsSupport.wire(graphView, graphViewController, noteService,
                this::isDarkThemeActive, this::getString, this::updateStatus, () -> editorTabs,
                new TabsSupport.Host(this::confirmLeaveCurrent, this::openNoteInTab, this::openNoteInNewTab,
                        this::handleUiNoteOpenRequest, this::clearEditorToEmpty, this::noNoteInSight, () -> {
                            refreshEditorIndicators();
                            updateNavigationButtons();
                        },
                        kanbanPicker::show, () -> editorController.positions().where(), at -> editorController.positions().showAt(at)));
        if (graphViewController != null) {
            graphViewController.wire(eventBus, noteService, tagService, resources,
                    tabsSupport::openNoteFromGraph, tabsSupport::closeGraph,
                    () -> getCurrentNote() != null ? getCurrentNote().getId() : null, null);
        }

        statusBarSupport.wire(storageLabel, wordCountLabel, charCountLabel, statsSeparator,
                wordCharSeparator, prefs, this::getString);
        statusBarSupport.wireEditorIndicators(saveStateLabel, editorModeLabel, editorIndicatorsBox,
                this::applyEditorMode);
        Supplier<Scene> sceneSupplier =
                () -> mainSplitPane != null ? mainSplitPane.getScene() : null;
        gitController.wire(gitSeparator, gitBar, gitInitLabel, gitRemoteLabel, gitChangesLabel,
                gitCommitLabel, gitSyncLabel, gitHistoryLabel, prefs, this::getString, this::updateStatus,
                sceneSupplier, () -> handleRefresh(null));
        privacySupport.wire(this::getString, this::updateStatus, sceneSupplier, new PrivacySupport.Shell() {
            @Override
            public NoteService notes() {
                return noteService;
            }

            @Override
            public EventBus eventBus() {
                return eventBus;
            }

            @Override
            public Note openNote() {
                return getCurrentNote();
            }

            @Override
            public String openNoteText() {
                return editorController.getCurrentContent();
            }

            @Override
            public void reloadInEditor(Note note) {
                editorController.loadNote(note);
            }

            @Override
            public void refreshNotesList() {
                MainController.this.refreshNotesList();
            }
        });
        backlinksSupport.wire(backlinkService, noteService, this::getString, this::loadNoteInEditor, rightPanelSupport::linksInSight);
        rightPanelSupport.wire(rightDock, prefs, this::getString, backlinksSupport.links(), uiLayout::isRightPanelOpen,
                () -> backlinksSupport.refresh(tabsSupport.inSight(getCurrentNote())), () -> handleToggleRightPanel(null));
        rightPanelSupport.pinWith(backlinksSupport, () -> tabsSupport.inSight(getCurrentNote()));
        backlinksSupport.setCreateNoteAction(noteCreationSupport::createNoteWithTitle);
        backlinksSupport.setStatusAction(this::updateStatus);
        // "Link" rewrote a mention in another note: tell the list, tabs and indexes it was saved.
        backlinksSupport.setMentionLinkedAction(note -> {
            if (eventBus != null) {
                eventBus.publish(new NoteEvents.NoteSavedEvent(note));
            }
        });
        historySupport.wire(noteService, this::getString, this::updateStatus, sceneSupplier,
                this::getCurrentNote, this::loadNoteInEditor);
        noteCreationSupport.useVault(() -> vaultSettings, () -> editorController, this::getPrimaryStage, () -> resources);
        noteCreationSupport.setBeginTitleRename(() -> {
            if (editorController != null) {
                editorController.beginTitleRename();
            }
        });
        noteCreationSupport.wire(noteService, noteOperations,
                () -> !"sqlite".equals(prefs.get("storage_type", "sqlite")),
                this::getString, this::updateStatus,
                this::getMainWindow,
                () -> currentFolder, this::openNoteInNewTab,
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
        importSupport.wire(new ImportService(noteService, folderService),
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
        startupSupport.wire(new StartupSupport.Host(() -> vaultSettings != null ? vaultSettings.get() : null,
                path -> noteCreationSupport.templates().noteAt(path), this::loadNoteInEditor, this::openNoteInNewTab,
                tabsSupport::newTab, noteCreationSupport::openDailyNote, this::updateStatus, this::getString,
                available -> toolbarController.setHomeAvailable(available)));
        workspaceController.wire(new WorkspaceController.Host(this::captureLiveWorkspace, tabsSupport::histories,
                tabsSupport::closeAll, tabsSupport::restoreTabs, this::applyWorkspaceLayout),
                this::getString, this::updateStatus, sceneSupplier, prefs, statusBar);
        // REMOVABLE: in-app updater (see docs/PACKAGING.md).
        updateInstallSupport.wire(this::getString, this::updateStatus, this::openExternalUrl,
                this::requestApplicationClose, () -> {
                    Platform.exit();
                    System.exit(0);
                }, this::isDarkThemeActive);
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
            Platform.runLater(this::loadAllNotes);
        } else {
            Platform.runLater(() -> {
                if (sidebarController != null
                        && sidebarController.getFolderTreeView() != null
                        && sidebarController.getFolderTreeView().getSelectionModel().getSelectedItem() == null) {
                    loadAllNotes();
                }
            });
        }
        updateStorageLabel();
        gitController.refreshStatus();
    }

    private Window getMainWindow() {
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
        vaultWatch.endSession(noteDAO);
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
        if (editorTabs != null) {
            editorTabs.clear();
        }
        clearEditorToEmpty();
    }

    /** Opens the storage the preferences name now, from a clean UI, and shows it everywhere. */
    private void reloadBackendSession() {
        resetUiForBackendReload();
        disposeCurrentBackendSession();
        initializeBackendSession();
        bindBackendSession();
        reloadPluginSystemForBackendSession();
        refreshBackendSessionViews(true);
        if (eventBus != null) {
            eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
        }
        startupSupport.front(); // what the new vault puts in front (its tabs are not the last session's)
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
            reloadBackendSession();
            updateStatus(MessageFormat.format(getString("status.vault_switched"),
                    new File(newVaultPath).getName()));
            logger.info("Switched vault without restart: " + newVaultPath + " [session=" + generation + "]");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to switch vault session", e);
            prefs.put("filesystem_path", currentPath);
            try {
                long rollbackGeneration = beginBackendSessionGeneration();
                reloadBackendSession();
                logger.info("Restored previous vault session after switch failure [session=" + rollbackGeneration + "]");
            } catch (Exception rollbackError) {
                logger.log(Level.SEVERE, "Failed to restore previous vault after switch error", rollbackError);
            }
            updateStatus(MessageFormat.format(getString("status.error_details"), e.getMessage()));
            showAlert(Alert.AlertType.ERROR,
                    getString("status.error"),
                    getString("status.error"),
                    MessageFormat.format(getString("status.error_details"), e.getMessage()));
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
            Scene scene = null;
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
            scene.getAccelerators().put(FocusModeSupport.SHORTCUT, this::handleFocusMode);
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

            // What plugins add to the window lives in PluginUi; what they add to the editor and
            // the reading view, in the editor.
            pluginManager = new PluginManager(noteService, folderService, tagService, eventBus, commandPalette, pluginUi,
                    pluginUi, editorController, editorHooks, pluginUi, editorController, this::handleUiNoteOpenRequest,
                    this::handleUiHeadingNavigationRequest, pluginUi, pluginUi);
            pluginManager.setOpenNote(() -> tabsSupport.inSight(getCurrentNote()));

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
        uiEventSubscriptions.addAll(backlinksSupport.subscribe(eventBus));
        uiEventSubscriptions.add(eventBus.subscribe(NoteEvents.NoteDeletedEvent.class,
                event -> handleUiNoteDeleted(event.getNoteId())));
        uiEventSubscriptions.add(eventBus.subscribe(FolderEvents.FolderDeletedEvent.class,
                event -> handleUiFolderDeleted(event.getFolderId())));
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
                if (editorController != null) {
                    editorController.syncCurrentNoteIdentity(saved);
                    updateNoteMetadata(getCurrentNote());
                    editorController.syncFavoritePinButtons(this::getString);
                    // The save may have picked up a newly typed inline #tag (or dropped one
                    // the frontmatter round-trip no longer carries); the tags-bar chips are
                    // built once when the note opens and otherwise never re-read the note's
                    // current tag list on their own.
                    editorController.loadNoteTagsForNote(getCurrentNote(),
                            this::handleAddTagToNote, this::removeTagFromNote);
                }
                pendingModifiedNoteId = saved.getId();
                // What the note links to may have changed with this save.
                backlinksSupport.refresh(active);
            }
            refreshNotesGridIfActive();
        }));
    }

    void handleUiNotesLoaded(List<Note> notes, String statusMessage) {
        // noteCountLabel shows the count; the transient "loaded …" message goes to
        // the left status label (avoids the message being duplicated in both spots).
        if (noteCountLabel != null) {
            int count = notes != null ? notes.size() : 0;
            noteCountLabel.setText(MessageFormat.format(getString("info.notes_count"), count));
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            updateStatus(statusMessage);
        }
        refreshNotesGridIfActive();
    }

    void handleUiNoteDeleted(String noteId) {
        Note current = getCurrentNote();
        boolean wasActive = current != null && current.getId().equals(noteId);
        if (wasActive && editorController != null) {
            editorController.markClean(); // the note is gone; discard any edits
        }
        if (editorTabs != null) {
            editorTabs.forget(noteId);
        }
        if (editorTabs != null && editorTabs.isOpen(noteId)) {
            tabsSupport.close(noteId);
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

    /** The sidebar reloads its own trees when it hears the deletion. */
    void handleUiFolderDeleted(String folderId) {
        if (currentFolder != null && currentFolder.getId().equals(folderId)) {
            currentFolder = null;
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
        handleFolderSelection(selectedFolder);
        currentFolder = selectedFolder;
    }

    void handleUiTagSelected(Tag tag) {
        if (tag == null || tag.getTitle() == null || tag.getTitle().isBlank()) {
            return;
        }
        currentFolder = null;
        if (notesListController != null) {
            notesListController.loadNotesForTag(tag.getTitle());
        }
        showResultsPanel();
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

    void handleUiHeadingNavigationRequest(int offset, String headingSlug) {
        if (editorController != null) {
            editorController.navigateToHeading(offset, headingSlug);
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
        boolean dirty = isModified();
        if (editorTabs != null) {
            editorTabs.setDirty(note.getId(), dirty);
        }
        refreshEditorIndicators();
        if (!dirty) {
            pendingModifiedNoteId = null;
            noteModifiedDebounce.stop();
            autosaveDebounce.stop();
            refreshEditorAfterEdit();
            updateNoteStats(note);
            return;
        }
        pendingModifiedNoteId = note.getId();
        noteModifiedDebounce.playFromStart();
        if (autosaveEnabled) {
            autosaveDebounce.playFromStart();
        }
    }


    /** Tells the plugins the note in sight; {@code null}: none (a closed note, the graph, a new tab). */
    private void publishNoteSelected(Note note) {
        if (eventBus != null) {
            eventBus.publish(new NoteEvents.NoteSelectedEvent(note));
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
                if (notesListController.isShowingSearch()) {
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
                // An autosave never overwrites a file changed outside Jylos: the editor
                // pauses and reports the conflict instead, and the next manual save asks.
                if (editorController != null && editorController.handleSave(true)) {
                    updateStatus(getString("status.autosave_done"));
                }
            } finally {
                autosaveRunning = false;
            }
        });
    }

    /** Loads persisted UI preferences and applies them to the running shell and editor. */
    private void applyUiPreferencesFromStore() {
        UiPreferencesStore.UiPreferencesData uiPrefs = uiPreferences.load(prefs);
        autosaveEnabled = uiPrefs.autosaveEnabled();
        autosaveIdleMs = uiPrefs.autosaveIdleMs();
        theme.loadPreferences(uiPrefs.themeSource(), uiPrefs.externalThemeId(),
                uiPreferences.loadEnabledSnippets(prefs));
        notesListPreviewLines = uiPrefs.notesPreviewLines();
        uiFontSize = uiPrefs.uiFontSize();
        editorFontSize = uiPrefs.contentFontSize();
        uiAccentColor = uiPrefs.accentColor();
        autosaveDebounce.setDuration(Duration.millis(autosaveIdleMs));

        if (sidebarController != null) {
            sidebarController.applySidebarTabPresentation();
            sidebarController.applyShowFolderNoteCountsPreference(uiPrefs.showFolderNoteCounts());
        }
        if (notesListController != null) {
            notesListController.setPreviewLines(notesListPreviewLines);
        }
        if (editorTabs != null) {
            editorTabs.setAlwaysNewTab(uiPrefs.alwaysOpenInNewTab());
        }
        if (editorController != null) {
            editorController.applyLivePreviewPreference(uiPrefs.livePreviewEnabled());
            editorController.applyReadableLineLengthPreference(uiPrefs.readableLineLength());
        }
        applyEditorControlsPresentation();
        applyUiZoom();
        applyEditorZoom();
        refreshEditorIndicators();
        Platform.runLater(theme::applyAndRefresh);
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
            uiLayout.showNavigationPanels();
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
            UiDialogs.apply(alert.getDialogPane());
            alert.showAndWait();
        }
    }

    @FXML
    private void handlePluginManager(ActionEvent event) {
        showPluginManager();
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
        updateStatus(MessageFormat.format(getString("status.unknown_command"), result.resolvedToken()));
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
        // A palette command that is also a menu or toolbar action runs that action's handler.
        initializeSystemActionHandlers();
        SystemActionEvent.ActionType action = CommandRegistry.ACTION_COMMANDS.get(commandId);
        if (action != null) {
            return systemActionHandlers.get(action);
        }
        SystemActionEvent.ActionType format = CommandRegistry.FORMAT_COMMANDS.get(commandId);
        if (format != null) {
            return () -> formatInEditor(format);
        }
        switch (commandId) {
            case "cmd.save":
                return () -> handleSave(null);
            case "cmd.delete_note":
                return () -> handleDelete(null);
            case "cmd.theme_light":
                return () -> handleLightTheme(null);
            case "cmd.theme_dark":
                return () -> handleDarkTheme(null);
            case "cmd.theme_system":
                return () -> handleSystemTheme(null);
            case "cmd.global_search":
                return () -> handleSearch(null);
            case "cmd.goto_all_notes":
                return this::loadAllNotes;
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
            case "cmd.plugins.manage":
                return this::showPluginManager;
            case "cmd.keyboard_shortcuts":
                return () -> handleKeyboardShortcuts(null);
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
        systemActionHandlers.put(SystemActionEvent.ActionType.NEW_NOTE, () -> handleNewNote(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.NEW_CANVAS, () -> handleNewCanvas(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.NEW_TAG, () -> handleNewTag(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SAVE_ALL, () -> handleSaveAll(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.IMPORT, () -> handleImport(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EXPORT, () -> handleExport(null));
        noteCreationSupport.register(systemActionHandlers);
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
        systemActionHandlers.put(SystemActionEvent.ActionType.CHECK_UPDATES, updateNotifications::checkManually);
        systemActionHandlers.put(SystemActionEvent.ActionType.ABOUT, () -> handleAbout(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SORT_FOLDERS, () -> sidebarController.handleSortFolders(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EXPAND_ALL_FOLDERS,
                () -> sidebarController.handleExpandAllFolders(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.COLLAPSE_ALL_FOLDERS,
                () -> sidebarController.handleCollapseAllFolders(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.SORT_TRASH, () -> sidebarController.handleSortTrash(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.EMPTY_TRASH, () -> sidebarController.handleEmptyTrash(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.REFRESH_NOTES, () -> handleRefresh(null));
        EditorCommandSupport.register(systemActionHandlers, () -> editorController);
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_SOURCE_MODE, this::handleToggleSourceMode);
        systemActionHandlers.put(SystemActionEvent.ActionType.SPLIT_VIEW_MODE, this::handleSplitViewMode);
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_PIN, () -> handleTogglePin(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_FAVORITE, () -> handleToggleFavorite(null));
        systemActionHandlers.put(SystemActionEvent.ActionType.TOGGLE_RIGHT_PANEL, () -> handleToggleRightPanel(null));
        tabsSupport.register(systemActionHandlers);
        startupSupport.register(systemActionHandlers);
        rightPanelSupport.register(systemActionHandlers);
        systemActionHandlers.put(SystemActionEvent.ActionType.KNOWLEDGE_INSIGHTS, this::showKnowledgeInsights);
        workspaceController.register(systemActionHandlers);
        vaultCleanup.register(systemActionHandlers, () -> noteService, this::getString, this::updateStatus, this::getMainWindow);
        obsidian.register(systemActionHandlers, () -> noteService, () -> vaultSettings, this::getString,
                this::updateStatus, () -> handleRefresh(null), this::getMainWindow);
        systemActionHandlers.put(SystemActionEvent.ActionType.FOCUS_MODE, this::handleFocusMode);
        systemActionHandlers.put(SystemActionEvent.ActionType.PRIVATE_TOGGLE, () -> privacySupport.toggle(getCurrentNote()));
        systemActionHandlers.put(SystemActionEvent.ActionType.NOTES_UNLOCK, privacySupport::unlockAll);
        systemActionHandlers.put(SystemActionEvent.ActionType.NOTES_LOCK, privacySupport::lockAll);
        importSupport.register(systemActionHandlers);
        systemActionHandlers.put(SystemActionEvent.ActionType.NOTE_HISTORY, historySupport::showHistoryDialog);
        systemActionHandlers.put(SystemActionEvent.ActionType.QUICK_SWITCHER, this::showQuickSwitcher);
        systemActionHandlers.put(SystemActionEvent.ActionType.CLOSE_NOTE, this::handleCloseNote);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_PANEL, gitController::showSyncPanel);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_SYNC, gitController::sync);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_COMMIT_PUSH, gitController::commitPush);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_PULL, gitController::pull);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_HISTORY, gitController::showHistoryDialog);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_INIT, gitController::init);
        systemActionHandlers.put(SystemActionEvent.ActionType.GIT_ADD_REMOTE, gitController::addRemote);
    }

    // ── Navigation history (per tab, in TabsSupport) ─────────────────────────

    /** Syncs the back/forward toolbar-button states to reflect whether the respective navigation stacks are non-empty. */
    private void updateNavigationButtons() {
        boolean back = editorTabs != null && editorTabs.canGoBack();
        boolean forward = editorTabs != null && editorTabs.canGoForward();
        if (editorController != null) {
            editorController.updateNavigationState(back, forward);
        }
        if (graphViewController != null) {
            graphViewController.updateNavigationState(back, forward);
        }
    }

    private Stage getPrimaryStage() {
        if (mainSplitPane != null && mainSplitPane.getScene() != null) {
            Window window = mainSplitPane.getScene().getWindow();
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

    private void initializeEditorPresentation() {
        if (editorController == null || notesListController == null) {
            return;
        }
        notesListController.initializeNotesGrid();
        refreshEditorIndicators();
        applyEditorControlsPresentation();
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
                    .addListener((obs, oldVal, newVal) -> applyEditorControlsPresentation());
        }
    }

    private void updateToolbarOverflow(double width) {
        uiInitialization.updateToolbarOverflow(toolbarController, width);
    }

    private void applyEditorControlsPresentation() {
        if (editorController == null) {
            return;
        }
        editorController.applyHeaderControlsPresentation();
    }

    private void handleSplitViewMode() {
        if (editorController != null) {
            editorController.setViewMode(EditorController.ViewMode.SPLIT);
            updateStatus(getString("status.mode_split"));
        }
    }

    /** Makes source-mode changes visible when invoked from the reading-only surface. */
    private void handleToggleSourceMode() {
        if (editorController.getViewMode() == EditorController.ViewMode.PREVIEW_ONLY) {
            editorController.setViewMode(EditorController.ViewMode.EDITOR_ONLY);
        }
        editorController.toggleLivePreview();
        refreshEditorIndicators();
    }

    @FXML
    private void handleToggleRightPanel(ActionEvent event) {
        uiLayout.toggleRightPanel(tabsSupport.inSight(getCurrentNote()), note -> { backlinksSupport.refresh(note); rightPanelSupport.panelOpened(); });
    }

    /**
     * Shows every note, unfiltered: resets the folder filter and asks {@link NotesListController}
     * for every note (the sidebar has no "All Notes" row; the vault root with "show subfolders
     * recursively" covers it).
     */
    private void loadAllNotes() {
        if (notesListController != null) {
            currentFolder = null;
            notesListController.loadAllNotes();
        }
    }


    /** Updates the active folder filter and ensures the notes panel is visible, expanding collapsed splits as needed. */
    private void handleFolderSelection(Folder folder) {
        try {
            if (folder == null) {
                return;
            }
            if (uiLayout.isExplorer()) {
                // The Explorer tree browses folders itself; the notes panel only shows results.
                currentFolder = folder;
                return;
            }
            currentFolder = folder;
            if (notesListController != null) {
                notesListController.loadNotesForFolder(folder);
            }

            uiLayout.revealNotesPanel();
        } catch (Exception e) {
            logger.severe("Failed to handle folder selection "
                    + (folder != null ? folder.getTitle() : "null") + ": " + e.getMessage());
        }
    }

    /** Closes the current note: clears the editor to the empty state. */
    private void handleCloseNote() {
        // With tabs, "close note" closes the active tab (and activates a neighbour).
        if (editorTabs != null && editorTabs.getActiveId() != null) {
            tabsSupport.close(editorTabs.getActiveId());
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
        refreshEditorIndicators();
    }

    /** Shows the open note's save state and the editor mode in the status bar. */
    private void refreshEditorIndicators() {
        boolean livePreview = editorController == null || editorController.isLivePreviewEnabled();
        // A view's tab (the graph) shows no note, even with one loaded behind it.
        statusBarSupport.updateEditorIndicators(getCurrentNote() != null && !tabsSupport.isViewActive(), isModified(),
                StatusBarSupport.EditorMode.of(editorController != null ? editorController.getViewMode()
                        : EditorController.ViewMode.EDITOR_ONLY, livePreview), autosaveEnabled, autosaveIdleMs);
    }

    /** Switches the editor to the mode picked from the status bar. */
    private void applyEditorMode(StatusBarSupport.EditorMode mode) {
        if (editorController == null || mode == null) {
            return;
        }
        switch (mode) {
            case READING -> editorController.setViewMode(EditorController.ViewMode.PREVIEW_ONLY);
            case SPLIT -> editorController.setViewMode(EditorController.ViewMode.SPLIT);
            case LIVE_PREVIEW, SOURCE -> {
                editorController.setViewMode(EditorController.ViewMode.EDITOR_ONLY);
                if (editorController.isLivePreviewEnabled() != (mode == StatusBarSupport.EditorMode.LIVE_PREVIEW)) {
                    editorController.toggleLivePreview();
                }
            }
        }
        // Switching Live Preview keeps the view mode, so nothing else redraws the indicator.
        refreshEditorIndicators();
    }

    /** Updates the storage-type indicator label in the status bar. */
    private void updateStorageLabel() {
        statusBarSupport.updateStorageLabel();
    }

    /** Click on the storage indicator → open the storage switcher (Obsidian-style). */
    @FXML
    private void handleStorageClick(MouseEvent event) {
        handleSwitchStorage();
    }

    // ── Git vault synchronization ───────────────────────────────────────
    // The logic lives in GitController; these FXML status-bar click handlers delegate.

    @FXML private void handleGitStatusClick(MouseEvent event)  {
        // Right click opens the entry's context menu (changes, commit, history), not the panel.
        if (event != null && event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        gitController.showSyncPanel(GitSyncPanel.FocusTarget.BRANCH);
    }
    @FXML private void handleGitInitClick(MouseEvent event)    { gitController.init(); }
    @FXML private void handleGitRemoteClick(MouseEvent event)  { gitController.addRemote(); }
    @FXML private void handleGitCommitClick(MouseEvent event)  {
        gitController.showSyncPanel(GitSyncPanel.FocusTarget.COMMIT);
    }
    @FXML private void handleGitChangesClick(MouseEvent event) {
        gitController.showSyncPanel(GitSyncPanel.FocusTarget.CHANGES);
    }
    @FXML private void handleGitHistoryClick(MouseEvent event) { gitController.showHistoryDialog(); }

    /** Applies the active theme stylesheet to a dialog and sets its owner window. */
    private void styleDialog(Dialog<?> dialog) {
        Scene scene = mainSplitPane != null ? mainSplitPane.getScene() : null;
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        UiDialogs.apply(dialog);
    }

    /** Opens {@code note} in the active tab (or activates its tab when it is already open). */
    void loadNoteInEditor(Note note) {
        loadNoteInEditor(note, EditorTabsModel.OpenTarget.CURRENT_TAB);
    }

    /** Opens {@code note} in a new tab right after the active one (Cmd/Ctrl+click, middle click). */
    void openNoteInNewTab(Note note) {
        loadNoteInEditor(note, EditorTabsModel.OpenTarget.NEW_TAB);
    }

    /**
     * Loads {@code note} into the editor. {@code target} decides its tab: the active tab's
     * note is replaced ({@code CURRENT_TAB}) or a tab is added ({@code NEW_TAB}); a note
     * that already has a tab just activates it. See {@link EditorTabsModel}.
     */
    void loadNoteInEditor(Note note, EditorTabsModel.OpenTarget target) {
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
        if (!tabsSupport.leaveCurrent()) {
            return;
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
            editorTabs.open(activeNote.getId(), activeNote.getTitle(), NoteIcons.tabIconFor(activeNote), target);
            editorTabs.setDirty(activeNote.getId(), false);
        }
        tabsSupport.noteShown();
        if (sidebarController != null) {
            sidebarController.setExplorerActiveNote(activeNote.getId());
        }

        publishNoteSelected(activeNote);
        updateNoteMetadata(activeNote);
        backlinksSupport.refresh(activeNote);
        editorController.syncFavoritePinButtons(this::getString);

        // Attachments (PDF/images) show a native viewer — skip the Markdown-only work
        // (tags, preview render, word stats, favorite/pin toggles).
        if (editorController.isViewingAttachment()) {
            clearStatusOnNoteOpen();
            return;
        }

        editorController.loadNoteTagsForNote(activeNote, this::handleAddTagToNote, this::removeTagFromNote);
        updateNoteStats(activeNote);
        editorController.ensurePreviewWebViewThemeClass();
        refreshEditorPreview();

        clearStatusOnNoteOpen();
    }

    /**
     * Opening a note clears the status message instead of saying "Note loaded: title": the
     * title is already in the tab, the title field and the path above the editor, and the old
     * message (a search, an error) no longer applies to what is on screen.
     */
    private void clearStatusOnNoteOpen() {
        updateStatus("");
    }

    // ── Editor tabs (multiple open notes) ────────────────────────────────────

    /** Loads the note with {@code noteId} into the editor (used by tab selection). */
    private void openNoteInTab(String noteId) {
        if (noteService == null || noteId == null) {
            return;
        }
        noteService.getNoteById(noteId).ifPresent(this::loadNoteInEditor);
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
                return !isModified();
            } else if (editorController != null) {
                editorController.markClean();
            }
        }
        return true;
    }

    /** Resets the editor to the empty state (no note open). */
    private void clearEditorToEmpty() {
        if (sidebarController != null) {
            sidebarController.setExplorerActiveNote(null);
        }
        if (editorController != null) {
            editorController.loadNote(null);
            editorController.clearPreview();
        }
        if (notesListController != null && notesListController.getNotesListView() != null) {
            notesListController.getNotesListView().getSelectionModel().clearSelection();
        }
        updateNoteMetadata(null);
        noNoteInSight();
        updateStatus(getString("status.note_closed"));
    }

    /** No note in sight: what follows the open one (plugins, the links, the counts) has none. */
    private void noNoteInSight() {
        publishNoteSelected(null);
        backlinksSupport.refresh(null);
        updateNoteStats(null);
    }

    private void updateNoteMetadata(Note note) {
        if (editorController != null) {
            editorController.updateNoteMetadata(note);
        }
    }

    /** Recomputes (off the FX thread) and renders the backlinks for {@code note}. */
    /**
     * Follows a link clicked in the editor or the reading view: opens the note it names
     * (resolved by path from the open note; a link with only {@code #section} stays in the
     * open note), in a new tab when {@code newTab} (Cmd/Ctrl+click), and, when the link names
     * a heading or a block, scrolls there.
     */
    private void openLink(String link, boolean newTab) {
        Note current = getCurrentNote();
        String subpath = WikiLinkResolver.subpathOf(link);
        boolean sameNote = link != null && link.strip().startsWith("#") && current != null;
        Optional<Note> target = sameNote ? Optional.of(current)
                : noteService.resolveLink(link, current != null ? current.getId() : null);
        if (target.isEmpty()) {
            updateStatus("Note not found: " + link);
            return;
        }
        if (newTab && !sameNote) {
            openNoteInNewTab(target.get());
        } else if (!sameNote) {
            handleUiNoteOpenRequest(target.get());
        }
        Note opened = getCurrentNote();
        if (subpath == null || opened == null || !Objects.equals(opened.getId(), target.get().getId())) {
            return;
        }
        NoteAnchors.locate(opened.getContent(), subpath)
                .ifPresent(anchor -> editorController.navigateToHeading(anchor.offset(), anchor.elementId()));
    }

    private void loadNotesForTag(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return;
        }
        currentFolder = null;
        if (notesListController != null) {
            notesListController.loadNotesForTag(tagName);
        }
        showResultsPanel();
    }

    /** Sets the filter type to "search" and delegates full-text search to {@link NotesListController}. */
    private void performSearch(String searchText) {
        if (notesListController != null) {
            notesListController.performSearch(searchText);
            if (searchText != null && !searchText.isBlank()) {
                showResultsPanel();
            }
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

    void updateStatus(String message) {
        StatusBarSupport.showMessage(statusLabel, statusMessageSeparator, message);
    }

    @FXML
    private void handleUpdateIndicatorClick() {
        updateNotifications.onIndicatorClicked();
    }

    private void openExternalUrl(String url) {
        SystemBrowser.open(url);
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
        UiDialogs.apply(alert.getDialogPane());
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

    void handleNewCanvas(ActionEvent event) {
        try {
            noteCreationSupport.createNewCanvas();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create new canvas", e);
            updateStatus(getString("status.error_creating_note"));
        }
    }

    /** Opens today's daily note ({@code yyyy-MM-dd}), creating it (from a "daily" template if any). */
    @FXML
    void handleNewFolder(ActionEvent event) {
        boolean createInRoot = currentFolder == null;
        // In the Explorer, as in Obsidian: the folder is created as "Sin nombre" and renamed in its row.
        if (sidebarController != null && sidebarController.isExplorerMode()) {
            FolderOperations.FolderCreationResult untitled = folderOperations.createUntitledFolder(
                    getString("app.untitled_folder"), currentFolder, createInRoot);
            if (!untitled.success() || untitled.folder() == null) {
                updateStatus(getString("status.error_creating_folder"));
                return;
            }
            sidebarController.renameInExplorer(untitled.folder());
            sidebarController.loadFolders();
            return;
        }
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle(getString("dialog.new_folder.title"));
        dialog.setHeaderText(createInRoot
                ? getString("dialog.new_folder.header_root")
                : MessageFormat.format(getString("dialog.new_folder.header_sub"), currentFolder.getTitle()));
        dialog.setContentText(getString("dialog.new_folder.content"));
        dialog.getDialogPane().setMinWidth(380);
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
                    ? MessageFormat.format(getString("status.error_details"), detail)
                    : getString("status.error_creating_folder"));
            return;
        }

        if (sidebarController != null) {
            sidebarController.loadFolders();
        }
        if (sidebarController.getFolderTreeView() != null) {
            sidebarController.getFolderTreeView().refresh();
        }
        updateStatus(MessageFormat.format(getString("status.folder_created"), creation.folder().getTitle()));
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
        vaultWatch.start(noteService, generation, this::isActiveBackendSession, eventBus,
                new VaultWatchSupport.Editor(this::getCurrentNote, this::isModified, this::refreshOpenNoteAfterStorageRefresh,
                        this::refreshInactiveOpenTabsAfterStorageRefresh,
                        () -> updateStatus(getString("status.open_note_changed_outside")),
                        (from, to, title) -> { editorTabs.rebindId(from, to, title); backlinksSupport.rebind(from, to); }, editorController::syncCurrentNoteIdentity),
                () -> handleRefresh(null));
    }


    /** Refreshes the notes list according to the active filter (all / folder / tag / search) and view mode. */
    /** Loads again what the notes list shows (the list keeps which filter that is). */
    private void refreshNotesList() {
        if (notesListController != null) {
            notesListController.refreshCurrentView();
        }
    }

    @FXML
    void handleDelete(ActionEvent event) {
        if (eventBus != null) {
            eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.DELETE));
        }
    }

    @FXML
    private void handleExit(ActionEvent event) {
        if (requestApplicationClose()) {
            Platform.exit();
            System.exit(0);
        }
    }

    public boolean requestApplicationClose() {
        if (!confirmLeaveCurrent()) {
            return false;
        }
        shutdownApplication();
        return true;
    }

    /** Cancels all event subscriptions, shuts down plugins, closes the executor and the database connection in orderly fashion. */
    public void shutdownApplication() {
        try {
            vaultWatch.endSession(noteDAO);
            workspaceController.saveSession(); // first, while the tabs are all there
            uiEventSubscriptions.forEach(EventBus.Subscription::cancel);
            uiEventSubscriptions.clear();
            if (systemActionSubscription != null) {
                systemActionSubscription.cancel();
            }
            pluginUiRefreshSubscription.cancel();
            // Plugins first: a plugin's own shutdown() can still ask for UI work (e.g. a
            // preview enhancer unregistering triggers a preview refresh) — tearing down
            // the controllers it might call into first just means that work fails
            // instead of never being requested.
            if (pluginManager != null) {
                pluginManager.shutdownAll();
            }
            PluginLoader.closeAllClassLoaders();
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
            NoteTitleIndex.getInstance().shutdown();
            quickSwitcherExecutor.shutdownNow();
            updateNotifications.shutdown();

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
        UiDialogs.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    @FXML
    private void handleUndo(ActionEvent event) {
        inEditor(EditorController::performUndo);
    }

    @FXML
    private void handleRedo(ActionEvent event) {
        inEditor(EditorController::performRedo);
    }

    @FXML
    private void handleCut(ActionEvent event) {
        inEditor(EditorController::performCut);
    }

    @FXML
    private void handleCopy(ActionEvent event) {
        inEditor(EditorController::performCopy);
    }

    @FXML
    private void handlePaste(ActionEvent event) {
        inEditor(EditorController::performPaste);
    }

    @FXML
    private void handleFind(ActionEvent event) {
        inEditor(editor -> editor.performFind(this::getString, this::updateStatus));
    }

    @FXML
    private void handleReplace(ActionEvent event) {
        inEditor(editor -> editor.performReplace(this::getString, this::updateStatus));
    }

    /** Whether the editor is on screen: not with a view's tab (the graph) in front of its note. */
    private boolean editorInFront() {
        return editorController != null && !tabsSupport.isViewActive();
    }

    /** Runs an editing command (undo, paste, find…) on the editor when it is on screen. */
    private void inEditor(Consumer<EditorController> command) {
        if (editorInFront()) {
            command.accept(editorController);
        }
    }

    /** Toggles focus / writing mode (logic + state in {@link FocusModeSupport}). */
    private void handleFocusMode() {
        focusModeSupport.toggle();
    }

    // ── Workspaces — capture/restore the working context (logic in WorkspaceController) ───

    /** Snapshots the current tabs, active note and basic layout into a live workspace. */
    private Workspace captureLiveWorkspace() {
        List<String> openIds = editorTabs != null ? editorTabs.getOpenIds() : List.of();
        String activeId = editorTabs != null ? editorTabs.getActiveId() : null;
        double splitMain = mainSplitPane != null && mainSplitPane.getDividers().size() > 0
                ? mainSplitPane.getDividerPositions()[0] : UiPreferencesStore.DEFAULT_SPLIT_MAIN;
        double splitContent = contentSplitPane != null && contentSplitPane.getDividers().size() > 0
                ? contentSplitPane.getDividerPositions()[0] : UiPreferencesStore.DEFAULT_SPLIT_CONTENT;
        boolean sidebarVisible = sidebarController != null && sidebarController.getSidebarPane() != null
                && sidebarController.getSidebarPane().isVisible();
        String storageMode = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        return WorkspaceService.liveState(openIds, activeId,
                editorController != null ? editorController.getViewMode().name()
                        : EditorController.ViewMode.EDITOR_ONLY.name(), sidebarVisible, focusModeSupport.isActive(),
                splitMain, splitContent, storageMode);
    }

    /** Reapplies a workspace's basic layout (view mode, focus, splits, sidebar). */
    private void applyWorkspaceLayout(Workspace ws) {
        try {
            if (editorController != null) {
                editorController.setViewMode(EditorController.ViewMode.valueOf(ws.viewMode()));
            }
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
        if (sidebarVisible != ws.sidebarVisible() && !uiLayout.isStacked()) {
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
        var service = new KnowledgeInsightsService(noteService, tagService);
        Scene scene = centerStack != null ? centerStack.getScene() : null;
        new KnowledgeInsightsPanel(
                service, this::getString, this::openNoteInTab, scene).show();
    }

    void handleToggleSidebar(ActionEvent event) {
        uiLayout.toggleSidebar();
    }

    @FXML
    void handleToggleNotesPanel(ActionEvent event) {
        if (uiLayout.isExplorer()) {
            closeResultsPanel();
            return;
        }
        uiLayout.toggleNotesPanel();
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
            Scene scene = toolbarController.getToolbarHBox().getScene();
            StringBuilder style = new StringBuilder("-fx-font-size: ").append(uiFontSize).append("px;");
            if (uiAccentColor != null && !uiAccentColor.isBlank()) {
                style.append(" -fx-accent: ").append(uiAccentColor).append(';')
                     .append(" -fx-accent-hover: derive(").append(uiAccentColor).append(", -12%);")
                     .append(" -fx-selected-bg: ").append(uiAccentColor).append(';');
            }
            scene.getRoot().setStyle(style.toString());
            // Icons and icon buttons are sized in pixels: rescale them for this font too.
            UiSizeScale.apply(scene, uiFontSize, uiPreferences.loadIconSize(prefs).factor());
            UiNodes.setStyleClass(scene.getRoot(), "colored-icons", uiPreferences.loadColoredIcons(prefs));
            // Every other CSS rule in the theme derives its own font-size from this root's
            // via `em` units, so a Dialog (its own separate Scene/root — JavaFX dialogs don't
            // inherit the main window's root) needs this same inline style re-captured right
            // away, not just on the next theme switch, or a dialog opened right after a live
            // zoom change would still render at the old size.
            UiDialogs.syncFromScene(scene);
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

    /** Applies the editor/preview font size and keeps it persisted, so Ctrl+/- zoom survives a restart. */
    private void applyEditorZoom() {
        if (editorController != null) {
            editorController.applyEditorZoom(editorFontSize);
        }
        uiPreferences.saveContentFontSize(prefs, (int) Math.round(editorFontSize));
    }

    /** Layout command and menu: moves on to the next layout. */
    @FXML
    void handleViewLayoutSwitch(ActionEvent event) {
        setLayoutMode(uiLayout.mode().next());
    }

    /** Switches to {@code mode}, stores it and says so in the status bar. */
    void setLayoutMode(LayoutMode mode) {
        if (mode == null) {
            return;
        }
        uiPreferences.saveLayoutMode(prefs, mode);
        applyLayoutMode(mode, false);
        updateStatus(getString(switch (mode) {
            case EXPLORER -> "status.layout_explorer";
            case CLASSIC_STACKED -> "status.layout_stacked";
            case CLASSIC_COLUMNS -> "status.layout_column";
        }));
    }

    /** Rearranges the window for {@code mode}; in the Explorer, with or without the results panel. */
    private void applyLayoutMode(LayoutMode mode, boolean showResults) {
        uiLayout.arrange(mode, showResults);
        LayoutMode layoutMode = uiLayout.mode();
        if (layoutMode.isExplorer() && notesListController != null && notesListController.isGridViewActive()
                && eventBus != null) {
            // Results are a list; the grid belongs to browsing a folder.
            eventBus.publish(new SystemActionEvent(SystemActionEvent.ActionType.LIST_VIEW));
        }
        if (sidebarController != null) {
            sidebarController.setExplorerMode(layoutMode.isExplorer());
        }
        if (editorController != null) {
            // The folder path in the title row reveals the note where there is a tree of notes.
            editorController.setPathRevealAction(layoutMode.isExplorer() && sidebarController != null
                    ? sidebarController::revealExplorerActiveNote : null);
        }
        if (notesListController != null) {
            notesListController.setResultsPanelMode(layoutMode.isExplorer());
        }
        if (toolbarController != null) {
            toolbarController.showLayoutMode(layoutMode);
        }
    }

    /** Explorer layout: opens the notes panel next to the editor to show tag or search results. */
    private void showResultsPanel() {
        if (uiLayout.isExplorer() && !uiLayout.resultsShown()) {
            applyLayoutMode(LayoutMode.EXPLORER, true);
        }
    }

    /** Explorer layout: closes the results panel. */
    void closeResultsPanel() {
        if (uiLayout.isExplorer() && uiLayout.resultsShown()) {
            applyLayoutMode(LayoutMode.EXPLORER, false);
        }
    }

    private static final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    @FXML
    private void handleLightTheme(ActionEvent event) {
        theme.setLight();
    }

    @FXML
    private void handleDarkTheme(ActionEvent event) {
        theme.setDark();
    }

    @FXML
    private void handleSystemTheme(ActionEvent event) {
        theme.setSystem();
    }

    /**
     * Applies the persisted theme after the scene is attached but before the first
     * window paint, avoiding a visible flash from the default stylesheet.
     */
    public void applyInitialThemeBeforeShow(Scene scene) {
        theme.applyBeforeShow(scene);
        // The saved interface font: initialize() runs before the window has its scene.
        applyUiZoom();
        if (editorController != null) {
            editorController.applyEditorTheme(isDarkThemeActive(), uiAccentColor);
        }
    }

    private void installSystemThemeFocusRefresh() {
        Platform.runLater(() -> {
            if (mainSplitPane != null && mainSplitPane.getScene() != null) {
                theme.followSystemOnFocus(mainSplitPane.getScene().getWindow());
            }
        });
    }

    /** Redraws what depends on the theme: the editor and its reading view, the notes grid, the graph. */
    private void refreshUiThemeDependents() {
        if (editorController != null) {
            editorController.applyEditorTheme(isDarkThemeActive(), uiAccentColor);
            editorController.refreshPreview(isDarkThemeActive());
        }
        refreshNotesGridIfActive();
        tabsSupport.applyGraphThemeIfVisible();
    }

    private boolean isDarkThemeActive() {
        return theme.isDark();
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
                theme.source(),
                theme.externalId(),
                notesListPreviewLines,
                (int) Math.round(uiFontSize),
                uiAccentColor,
                uiPreferences.loadLivePreviewEnabled(prefs),
                uiPreferences.loadReadableLineLength(prefs),
                (int) Math.round(editorFontSize),
                uiPreferences.loadShowFolderNoteCounts(prefs),
                uiPreferences.load(prefs).alwaysOpenInNewTab());
        Optional<DialogSupport.PreferencesDialogResult> result = dialogSupport.showPreferences(
                currentUiPrefs,
                theme.catalog(),
                theme.snippetCatalog(),
                theme.enabledSnippets(),
                uiLayout.mode(),
                uiPreferences.loadShowFormatToolbar(prefs),
                uiPreferences.loadIconSize(prefs),
                uiPreferences.loadColoredIcons(prefs),
                VaultSettingsSupport.forVault(vaultSettings, noteService, this::getString, resources));
        if (result.isEmpty()) {
            return;
        }
        DialogSupport.PreferencesDialogResult values = result.get();
        VaultSettingsSupport.save(vaultSettings, values.vaultSettings());
        startupSupport.refreshHome(); // a home page chosen or cleared shows or hides the house
        uiPreferences.save(prefs, values.uiPreferences());
        uiPreferences.saveEnabledSnippets(prefs, values.enabledSnippets());
        uiPreferences.saveIconSize(prefs, values.iconSize());
        uiPreferences.saveColoredIcons(prefs, values.coloredIcons());
        applyUiPreferencesFromStore();
        updateThemeMenuSelection();
        theme.syncSystemMonitoring();
        uiPreferences.saveShowFormatToolbar(prefs, values.showFormatToolbar());
        if (editorController != null) {
            editorController.formatToolbar().setVisible(values.showFormatToolbar());
        }
        if (values.layoutMode() != null && values.layoutMode() != uiLayout.mode()) {
            setLayoutMode(values.layoutMode());
        }
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
        if (eventBus != null) {
            eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
        }
        refreshOpenNoteAfterStorageRefresh();
        // The refresh event above makes the notes list load again what it shows.
    }

    private void refreshOpenNoteAfterStorageRefresh() {
        refreshInactiveOpenTabsAfterStorageRefresh();
        Note current = getCurrentNote();
        if (current == null || current.getId() == null || noteService == null || editorController == null) {
            return;
        }
        Optional<Note> refreshed = noteService.getNoteById(current.getId());
        if (refreshed.isEmpty()) {
            handleMissingOpenNoteAfterStorageRefresh(current);
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

    private void refreshInactiveOpenTabsAfterStorageRefresh() {
        if (editorTabs == null || noteService == null) {
            return;
        }
        String activeId = editorTabs.getActiveId();
        for (String openId : new ArrayList<>(editorTabs.getOpenIds())) {
            if (Objects.equals(openId, activeId) || ViewTabs.isView(openId)) {
                continue;
            }
            Optional<Note> refreshed = noteService.getNoteById(openId);
            if (refreshed.isEmpty()) {
                editorTabs.removeTab(openId);
                continue;
            }
            editorTabs.setTitle(openId, refreshed.get().getTitle());
            editorTabs.setDirty(openId, false);
        }
    }

    private void handleMissingOpenNoteAfterStorageRefresh(Note missingNote) {
        if (missingNote == null || missingNote.getId() == null) {
            return;
        }
        if (isModified()) {
            updateStatus(getString("status.open_note_missing_dirty"));
            return;
        }
        if (editorTabs != null && editorTabs.isOpen(missingNote.getId())) {
            tabsSupport.close(missingNote.getId());
        } else {
            clearEditorToEmpty();
        }
        updateStatus(getString("status.open_note_missing_closed"));
    }

    @FXML
    private void handleToggleFavorite(ActionEvent event) {
        if (getCurrentNote() == null) {
            updateStatus(getString("status.no_note"));
            return;
        }
        toggleFavorite(getCurrentNote());
    }

    /** The notes list owns the favourite and pin marks (see {@link NotesListController#toggleFavorite}). */
    private void toggleFavorite(Note note) {
        if (notesListController != null) {
            notesListController.toggleFavorite(note);
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
        if (notesListController != null) {
            notesListController.togglePin(note);
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

    /** Runs a palette formatting command (bold, a list, a link…) in the editor, when it is on screen. */
    private void formatInEditor(SystemActionEvent.ActionType actionType) {
        inEditor(editor -> editor.applyFormat(actionType));
    }

    private void handleSystemAction(SystemActionEvent event) {
        Platform.runLater(() -> {
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
