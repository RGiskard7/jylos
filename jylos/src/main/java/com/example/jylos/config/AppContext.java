package com.example.jylos.config;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.interfaces.TagDAO;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Lightweight application-level service locator.
 *
 * <p>Replaces the manual setter-injection pattern where {@code MainController}
 * pushed every service reference into every sub-controller via
 * {@code setFolderService()}, {@code setEventBus()}, etc.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@code MainController.initializeDatabase()} calls
 *       {@link #initialize(NoteDAO, FolderDAO, TagDAO, NoteService, FolderService, TagService)}
 *       once at startup.</li>
 *   <li>Any controller can call the static getters
 *       (e.g.&nbsp;{@code AppContext.getNoteService()}) at any time after
 *       initialization.</li>
 *   <li>The {@link EventBus} is always available via its own singleton, but is
 *       also exposed here for consistency.</li>
 * </ol>
 *
 * <h3>Design rationale</h3>
 * <ul>
 *   <li><b>No framework dependency.</b> Plain static singleton — zero external
 *       libraries required.</li>
 *   <li><b>Immutable after boot.</b> Once {@code initialize()} completes the
 *       references never change (except {@code bundle}, which can be swapped
 *       on language change).</li>
 *   <li><b>Thread-safe.</b> All fields are volatile or effectively final after
 *       startup on the FX Application Thread.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public final class AppContext {

    private static final Logger logger = LoggerConfig.getLogger(AppContext.class);

    // --- Singleton state (effectively immutable after initialize()) ---

    private static volatile NoteDAO noteDAO;
    private static volatile FolderDAO folderDAO;
    private static volatile TagDAO tagDAO;

    private static volatile NoteService noteService;
    private static volatile FolderService folderService;
    private static volatile TagService tagService;

    private static volatile ResourceBundle bundle;

    private static volatile boolean initialized = false;

    private AppContext() {
        // prevent instantiation
    }

    // ------------------------------------------------------------------
    // Bootstrap
    // ------------------------------------------------------------------

    /**
     * Initializes the application context with the core DAOs and services.
     * Must be called exactly once from the startup path (typically
     * {@code MainController.initializeDatabase()}).
     *
     * @throws IllegalStateException if called more than once
     */
    public static void initialize(
            NoteDAO noteDAO,
            FolderDAO folderDAO,
            TagDAO tagDAO,
            NoteService noteService,
            FolderService folderService,
            TagService tagService) {

        if (initialized) {
            logger.warning("AppContext.initialize() called more than once — ignoring.");
            return;
        }

        AppContext.noteDAO = Objects.requireNonNull(noteDAO, "noteDAO");
        AppContext.folderDAO = Objects.requireNonNull(folderDAO, "folderDAO");
        AppContext.tagDAO = Objects.requireNonNull(tagDAO, "tagDAO");
        AppContext.noteService = Objects.requireNonNull(noteService, "noteService");
        AppContext.folderService = Objects.requireNonNull(folderService, "folderService");
        AppContext.tagService = Objects.requireNonNull(tagService, "tagService");

        initialized = true;
        logger.info("AppContext initialized with all services and DAOs.");
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public static EventBus getEventBus() {
        return EventBus.getInstance();
    }

    public static NoteDAO getNoteDAO() {
        ensureInitialized();
        return noteDAO;
    }

    public static FolderDAO getFolderDAO() {
        ensureInitialized();
        return folderDAO;
    }

    public static TagDAO getTagDAO() {
        ensureInitialized();
        return tagDAO;
    }

    public static NoteService getNoteService() {
        ensureInitialized();
        return noteService;
    }

    public static FolderService getFolderService() {
        ensureInitialized();
        return folderService;
    }

    public static TagService getTagService() {
        ensureInitialized();
        return tagService;
    }

    /**
     * Returns the current i18n {@link ResourceBundle}, or {@code null} if none
     * has been set yet.
     */
    public static ResourceBundle getBundle() {
        return bundle;
    }

    /**
     * Sets (or replaces) the i18n {@link ResourceBundle}.
     * This is the <b>only</b> mutable field after initialization and is
     * expected to change when the user switches language.
     */
    public static void setBundle(ResourceBundle bundle) {
        AppContext.bundle = bundle;
    }

    /**
     * Returns {@code true} after {@link #initialize} has been called
     * successfully.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "AppContext has not been initialized yet. "
                    + "Call AppContext.initialize() during application startup.");
        }
    }

    /**
     * Resets the context. <b>Only for use in unit tests.</b>
     */
    static void resetForTesting() {
        noteDAO = null;
        folderDAO = null;
        tagDAO = null;
        noteService = null;
        folderService = null;
        tagService = null;
        bundle = null;
        initialized = false;
    }
}
