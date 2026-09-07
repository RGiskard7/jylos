package com.example.jylos.plugin.builtin.publish;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;

/**
 * Exports the whole vault as a self-contained static HTML/CSS/JS site — one page
 * per note, wiki-links preserved as real relative links, a small JS graph viewer
 * — ready to serve from GitHub Pages, Vercel, or any plain static host. The
 * actual export logic lives in {@link VaultExporter}; this class is the UI shell
 * around it (folder picker, progress, result summary), following the same
 * pattern as the built-in Auto Backup plugin's own export flow.
 *
 * @author Edu Díaz (RGiskard7)
 */
public final class PublishPlugin implements Plugin {

    private static final String ID = "publish";
    private static final String NAME = "Publish";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Export the vault as a static website (wiki-links, graph, ready for GitHub Pages/Vercel)";
    private static final String AUTHOR = "Jylos Team";

    private static final String NOTE_MENU_LABEL = "Publish this note...";

    /**
     * Where the "Publish this note" right-click action updates — the last
     * folder a full "Publish Vault as Static Site..." export was written to.
     *
     * <p>Deliberately not per-vault (this app can have several backends/vaults
     * over its lifetime; a real per-vault key would need a stable vault
     * identifier this plugin has no access to today) — one global "last publish
     * location", matching what a single-vault user actually experiences.
     * Switching vaults just means the very next single-note publish picks up
     * the previous vault's directory once, same as picking the wrong folder by
     * hand would; nothing is silently corrupted by it, since the whole site
     * being updated is still resolved by content, not assumed.</p>
     */
    private static final String PREFS_KEY_LAST_OUTPUT_DIR = "lastOutputDir";

    private PluginContext context;

    // Loaded once, lazily — getDescription() can be called by the Plugin Manager before
    // initialize() ever runs, so this cannot wait for that. See PluginI18n's own doc for
    // why Locale.getDefault() alone (no PluginContext involved) is enough to pick the
    // right language: Main/AppSettings keep it in sync with the user's Settings choice.
    private static java.util.ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(PublishPlugin.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return tr("plugin.description", DESCRIPTION);
    }

    @Override
    public String getAuthor() {
        return AUTHOR;
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;

        context.registerCommand(
                "Publish: Export Vault as Static Site", // command IDs, not shown as-is — kept in English so
                                                          // unregisterCommand() below keeps matching it
                tr("command.description", "Export every note as a static HTML site with wiki-links and a graph, "
                        + "ready to publish"),
                this::publishVault);

        context.registerMenuItem(tr("menuCategory.utilities", "Utilities"), tr("menu.publishVault", "Publish Vault as Static Site..."),
                this::publishVault);
        context.registerNoteContextMenuItem(tr("menu.publishNote", NOTE_MENU_LABEL), this::publishSingleNote);

        context.log("Publish plugin initialized");
    }

    @Override
    public void shutdown() {
        context.unregisterCommand("Publish: Export Vault as Static Site");
        context.log("Publish plugin shutdown");
    }

    private void publishVault() {
        Platform.runLater(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(tr("chooser.title", "Select Output Folder for the Published Site"));
            File lastDir = lastOutputDir().map(Path::toFile).filter(File::isDirectory).orElse(null);
            chooser.setInitialDirectory(lastDir != null ? lastDir : new File(System.getProperty("user.home")));

            File selectedDir = chooser.showDialog(null);
            if (selectedDir == null) {
                return;
            }

            if (!confirmIfNotEmpty(selectedDir)) {
                return;
            }

            Path outputDir = selectedDir.toPath();
            // Pre-fills the dialog with whatever this exact folder was last published
            // with (if anything) — publishing again to the same site should default to
            // "keep doing what I did last time", not reset to a blank config every run.
            VaultExporter.PublishOptions previous = VaultExporter.readManifest(outputDir);
            Optional<VaultExporter.PublishOptions> options = PublishConfigDialog.show(
                    context, previous.siteTitle(), previous.generateGraph());
            if (options.isEmpty()) {
                return;
            }

            runExport(outputDir, options.get());
        });
    }

    /**
     * "Publish this note" — the notes list's right-click action. Updates the
     * already-published site at the last full-vault publish location rather
     * than exporting a new, disconnected mini-site (confirmed with the user:
     * this should behave like "sync just this change", the way Obsidian
     * Publish's own per-note actions do), so it needs somewhere to update —
     * see {@link #PREFS_KEY_LAST_OUTPUT_DIR}'s own doc for what "somewhere"
     * means when there's more than one vault involved.
     */
    private void publishSingleNote(Note note) {
        Optional<Path> outputDir = lastOutputDir();
        if (outputDir.isEmpty() || !Files.isDirectory(outputDir.get())) {
            context.showInfo(tr("summary.note.title", "Publish This Note"),
                    tr("alert.noSite.header", "No Published Site Yet"),
                    tr("alert.noSite.body",
                            "This updates an already-published site, but none has been published yet (or its folder"
                                    + " is no longer there).\n\nRun \"Publish Vault as Static Site...\" once first"
                                    + " (Tools menu, or the command palette) — after that, publishing a single note"
                                    + " will update that same site."));
            return;
        }
        runSingleNoteExport(outputDir.get(), note);
    }

    private Optional<Path> lastOutputDir() {
        String stored = context.getPluginPreferences().get(PREFS_KEY_LAST_OUTPUT_DIR, null);
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Paths.get(stored));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * A non-empty target folder gets files written into it alongside whatever is
     * already there — never deleted, never overwritten except a same-named page
     * this export itself produces. Still worth a heads-up: publishing into an
     * already-populated folder (an old export, or unrelated files) is an easy
     * thing to do by accident when picking a folder from memory.
     */
    private boolean confirmIfNotEmpty(File dir) {
        String[] existing = dir.list();
        if (existing == null || existing.length == 0) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                tr("confirm.notEmpty.body", "This folder already has files in it:\n%s"
                        + "\n\nJylos will add/overwrite the exported site's own files there, "
                        + "but won't delete anything else. Continue?").formatted(dir.getPath()),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle(tr("progress.vault.title", "Publish Vault"));
        alert.setHeaderText(tr("confirm.notEmpty.header", "Output folder is not empty"));
        context.applyTheme(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private void runExport(Path outputDir, VaultExporter.PublishOptions options) {
        Task<VaultExporter.Result> task = new Task<>() {
            @Override
            protected VaultExporter.Result call() throws Exception {
                VaultExporter exporter = new VaultExporter(
                        context.getNoteService(), context.getFolderService(), context.getTagService());
                return exporter.export(outputDir, options, (done, total) -> {
                    if (total > 0) {
                        updateProgress(done, total);
                    }
                });
            }
        };
        // context.runWithProgress owns the dialog/thread ceremony (and the deferred-one-
        // more-Platform.runLater-tick needed to avoid the nested-modal-goes-blank JavaFX
        // quirk) — onSuccess/onFailure below are the only export-specific parts left.
        context.runWithProgress(
                tr("progress.vault.title", "Publish Vault"),
                tr("progress.vault.header", "Exporting notes..."),
                task,
                result -> {
                    context.getPluginPreferences().put(PREFS_KEY_LAST_OUTPUT_DIR, outputDir.toAbsolutePath().toString());
                    showSummary(result);
                },
                ex -> {
                    context.logError("Vault export failed", ex);
                    context.showError(tr("error.title", "Publish Failed"),
                            tr("error.vault.body", "Could not export the vault: %s")
                                    .formatted(ex != null ? ex.getMessage() : tr("error.unknown", "unknown error")));
                });
    }

    private void runSingleNoteExport(Path outputDir, Note note) {
        Task<VaultExporter.SingleNoteResult> task = new Task<>() {
            @Override
            protected VaultExporter.SingleNoteResult call() throws Exception {
                VaultExporter exporter = new VaultExporter(
                        context.getNoteService(), context.getFolderService(), context.getTagService());
                return exporter.exportSingleNote(outputDir, note.getId(), (done, total) -> {
                    if (total > 0) {
                        updateProgress(done, total);
                    }
                });
            }
        };
        context.runWithProgress(
                tr("summary.note.title", "Publish This Note"),
                tr("progress.note.header", "Updating the published site..."),
                task,
                result -> showSingleNoteSummary(note, result),
                ex -> {
                    context.logError("Single-note publish failed", ex);
                    context.showError(tr("error.title", "Publish Failed"),
                            tr("error.note.body", "Could not publish \"%s\": %s")
                                    .formatted(note.getTitle(),
                                            ex != null ? ex.getMessage() : tr("error.unknown", "unknown error")));
                });
    }

    private void showSingleNoteSummary(Note note, VaultExporter.SingleNoteResult result) {
        String message = String.format(
                tr("summary.note.body",
                        "\"%s\" is up to date at:%n%s%n%nPages refreshed: %d (this note plus its linked neighbours)"),
                note.getTitle(), result.outputDir(), result.pagesWritten());
        context.showInfo(tr("summary.note.title", "Publish This Note"), tr("summary.note.header", "Note Published"),
                message);
        context.log("Published note \"" + note.getTitle() + "\" (" + result.pagesWritten()
                + " page(s) refreshed) to " + result.outputDir());
    }

    // Deferred, not shown directly from setOnSucceeded: that callback runs while
    // the progress dialog's own showAndWait() nested event loop is still
    // unwinding, and opening another modal Alert in the same callback that closes
    // the previous one renders as a blank window — the same JavaFX quirk already
    // documented (and fixed) for the in-app updater's own confirm-then-progress
    // dialog chain.
    private void showSummary(VaultExporter.Result result) {
        String message = String.format(
                tr("summary.vault.body",
                        "Site exported to:%n%s%n%nNotes published: %d%nSkipped (private): %d%nSkipped (attachments): %d"
                                + "%nSkipped (errors): %d%nSkipped (not selected): %d"),
                result.outputDir(), result.exportedNotes(), result.skippedPrivate(), result.skippedAttachments(),
                result.skippedErrors(), result.skippedByFilter());
        if (result.skippedErrors() > 0) {
            message += String.format("%n%n" + tr("summary.vault.errorsNote",
                    "Some notes failed to export (e.g. invalid frontmatter) and were "
                            + "skipped — see the app log for which ones."));
        }
        context.showInfo(tr("summary.vault.title", "Publish Complete"), tr("summary.vault.header", "Vault Published"),
                message);
        context.log("Published " + result.exportedNotes() + " notes to " + result.outputDir()
                + (result.skippedErrors() > 0 ? " (" + result.skippedErrors() + " skipped due to errors)" : ""));
    }
}
