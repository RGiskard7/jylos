package com.example.jylos.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import com.example.jylos.git.GitCommit;
import com.example.jylos.git.GitResult;
import com.example.jylos.git.GitService;
import com.example.jylos.git.GitStatus;
import com.example.jylos.ui.UiDialogs;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Owns the status-bar Git UI (the {@code git} strip in vault mode) and all Git
 * operations: status refresh, init, remote, commit, push/pull/sync, the changes and
 * history dialogs.
 *
 * <p>Extracted from {@code MainController} to keep that class focused on shell
 * coordination. The host wires the status-bar nodes and a few callbacks via
 * {@link #wire}, then forwards its (FXML-bound) click handlers to the public action
 * methods here. This is the standard "feature support" pattern for the UI layer.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class GitController {

    private final GitService gitService = new GitService();

    // Status-bar nodes (owned by MainView.fxml, injected into MainController).
    private Separator gitSeparator;
    private HBox gitBar;
    private Label gitInitLabel;
    private Label gitRemoteLabel;
    private Label gitChangesLabel;
    private Label gitCommitLabel;
    private Label gitSyncLabel;
    private Label gitHistoryLabel;

    // Host collaborators.
    private Preferences prefs;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Scene> sceneSupplier;

    /** Wires the status-bar nodes and host callbacks. Call once after FXML load. */
    public void wire(Separator gitSeparator, HBox gitBar, Label gitInitLabel, Label gitRemoteLabel,
            Label gitChangesLabel, Label gitCommitLabel, Label gitSyncLabel, Label gitHistoryLabel,
            Preferences prefs, Function<String, String> i18n, Consumer<String> status,
            Supplier<Scene> sceneSupplier) {
        this.gitSeparator = gitSeparator;
        this.gitBar = gitBar;
        this.gitInitLabel = gitInitLabel;
        this.gitRemoteLabel = gitRemoteLabel;
        this.gitChangesLabel = gitChangesLabel;
        this.gitCommitLabel = gitCommitLabel;
        this.gitSyncLabel = gitSyncLabel;
        this.gitHistoryLabel = gitHistoryLabel;
        this.prefs = prefs;
        this.i18n = i18n;
        this.status = status;
        this.sceneSupplier = sceneSupplier;
    }

    // ------------------------------------------------------------------
    // Public actions (called from MainController's FXML/menu handlers)
    // ------------------------------------------------------------------

    /** Runs a full vault sync (stage all → commit → pull → push) asynchronously and updates the status bar on completion. */
    public void sync() {
        runGitAsync(vault -> gitService.sync(vault, gitCommitMessage()), getString("status.git_syncing"));
    }

    /** Commits all staged changes with a timestamp message and, if successful, immediately pushes to the remote. */
    public void commitPush() {
        final String message = gitCommitMessage();
        runGitAsync(vault -> {
            GitResult commit = gitService.commit(vault, message);
            return commit.ok() ? gitService.push(vault) : commit;
        }, getString("status.git_syncing"));
    }

    /** Pulls changes from the remote into the vault asynchronously. */
    public void pull() {
        runGitAsync(gitService::pull, getString("status.git_pulling"));
    }

    /** Initializes a Git repository in the vault directory ({@code git init}) asynchronously. */
    public void init() {
        runGitAsync(gitService::init, getString("status.git_initializing"));
    }

    /** Prompts the user for a remote URL and sets it on the vault's Git repository asynchronously. */
    public void addRemote() {
        Path vault = gitVaultPath();
        if (vault == null) {
            updateStatus(getString("status.git_no_vault"));
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(getString("dialog.git_remote.title"));
        dialog.setHeaderText(getString("dialog.git_remote.header"));
        dialog.setContentText(getString("dialog.git_remote.content"));
        styleDialog(dialog);
        dialog.showAndWait().filter(url -> !url.isBlank()).ifPresent(url ->
                runGitAsync(v -> gitService.setRemote(v, url.trim()), getString("status.git_syncing")));
    }

    /**
     * Opens the consolidated Git Sync panel (status, changes, commit, pull/push/sync and
     * an activity log) for the current vault. No-op outside vault mode.
     */
    public void showSyncPanel() {
        Path vault = gitVaultPath();
        if (vault == null) {
            updateStatus(getString("status.git_no_vault"));
            return;
        }
        Scene scene = sceneSupplier != null ? sceneSupplier.get() : null;
        new com.example.jylos.ui.components.GitSyncPanel(gitService, vault, this::getString, scene).show();
        refreshStatus();
    }

    /** Refreshes the status-bar Git segments from the vault state (off the FX thread). */
    public void refreshStatus() {
        if (gitBar == null) {
            return;
        }
        Path vault = gitVaultPath();
        if (vault == null) {
            setNodeVisible(gitBar, false);
            setNodeVisible(gitSeparator, false);
            return;
        }
        Task<GitStatus> task = new Task<>() {
            @Override
            protected GitStatus call() {
                return gitService.status(vault);
            }
        };
        task.setOnSucceeded(e -> applyGitStatus(task.getValue()));
        task.setOnFailed(e -> {
            setNodeVisible(gitBar, false);
            setNodeVisible(gitSeparator, false);
        });
        runDaemon(task, "git-status");
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Resolves the vault directory eligible for Git, or null (SQLite / no path). */
    private Path gitVaultPath() {
        String type = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        if (!"filesystem".equalsIgnoreCase(type)) {
            return null;
        }
        String path = prefs.get("filesystem_path", "");
        if (path.isBlank()) {
            return null;
        }
        Path dir = Paths.get(path);
        return Files.isDirectory(dir) ? dir : null;
    }

    /** Generates a default commit message in the form "Jylos sync yyyy-MM-dd HH:mm". */
    private String gitCommitMessage() {
        return "Jylos sync " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now());
    }

    /** Runs a Git operation off the FX thread, then reports it and refreshes status. */
    private void runGitAsync(Function<Path, GitResult> op, String runningMessage) {
        Path vault = gitVaultPath();
        if (vault == null) {
            updateStatus(getString("status.git_no_vault"));
            return;
        }
        updateStatus(runningMessage);
        Task<GitResult> task = new Task<>() {
            @Override
            protected GitResult call() {
                return op.apply(vault);
            }
        };
        task.setOnSucceeded(e -> {
            updateStatus(describeGitResult(task.getValue()));
            refreshStatus();
        });
        task.setOnFailed(e -> updateStatus(getString("status.git_error")));
        runDaemon(task, "git-op");
    }

    /** Maps a {@link GitResult} to an i18n status label, appending the raw Git message on failure (truncated at 160 chars). */
    private String describeGitResult(GitResult result) {
        if (result == null) {
            return getString("status.git_error");
        }
        String key = switch (result.status()) {
            case OK -> "status.git_ok";
            case NOTHING_TO_DO -> "status.git_nothing";
            case NO_REMOTE -> "status.git_no_remote";
            case REJECTED -> "status.git_rejected";
            case AUTH_ERROR -> "status.git_auth";
            case NETWORK_ERROR -> "status.git_network";
            case CONFLICT -> "status.git_conflict";
            case GIT_UNAVAILABLE -> "status.git_unavailable";
            default -> "status.git_error";
        };
        String label = getString(key);
        boolean failure = !result.ok() && result.status() != GitResult.Status.NO_REMOTE;
        if (failure && result.message() != null && !result.message().isBlank()) {
            String detail = result.message().replaceAll("\\s+", " ").trim();
            if (detail.length() > 160) {
                detail = detail.substring(0, 157) + "…";
            }
            return label + ": " + detail;
        }
        return label;
    }

    /** Updates the five Git segments (remote · changes · commit · sync · history). */
    private void applyGitStatus(GitStatus status) {
        if (gitBar == null || status == null) {
            return;
        }
        setNodeVisible(gitBar, true);
        setNodeVisible(gitSeparator, true);

        boolean repo = status.repository();
        setNodeVisible(gitInitLabel, !repo);
        setNodeVisible(gitRemoteLabel, repo);
        setNodeVisible(gitChangesLabel, repo);
        setNodeVisible(gitCommitLabel, repo);
        setNodeVisible(gitSyncLabel, repo);
        setNodeVisible(gitHistoryLabel, repo);

        if (!repo) {
            gitInitLabel.setText(getString("git.initialize"));
            gitInitLabel.setTooltip(new Tooltip(getString("git.tooltip_init")));
            return;
        }

        gitRemoteLabel.setText(status.hasRemote() ? getString("git.remote") : getString("git.no_remote"));
        gitRemoteLabel.setTooltip(new Tooltip(getString("git.tooltip_remote")));

        gitChangesLabel.setText(MessageFormat.format(getString("git.changes"), status.modified()));
        gitChangesLabel.setTooltip(new Tooltip(getString("git.tooltip_changes")));

        gitCommitLabel.setText(getString("git.commit"));
        gitCommitLabel.setTooltip(new Tooltip(getString("git.tooltip_commit")));

        StringBuilder sync = new StringBuilder(status.branch().isBlank() ? "git" : status.branch());
        if (status.ahead() > 0) {
            sync.append("  ↑").append(status.ahead());
        }
        if (status.behind() > 0) {
            sync.append("  ↓").append(status.behind());
        }
        if (!status.needsSync()) {
            sync.append("  ✓");
        }
        gitSyncLabel.setText(sync.toString());
        gitSyncLabel.setTooltip(new Tooltip(getString("git.tooltip_sync")));

        gitHistoryLabel.setText(getString("git.history"));
        gitHistoryLabel.setTooltip(new Tooltip(getString("git.tooltip_history")));
    }

    /** Applies the active theme stylesheet to a dialog and sets its owner window. */
    private void styleDialog(Dialog<?> dialog) {
        Scene scene = sceneSupplier != null ? sceneSupplier.get() : null;
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        UiDialogs.apply(dialog);
    }

    /** History dialog: recent commits across all branches. */
    public void showHistoryDialog() {
        Path vault = gitVaultPath();
        if (vault == null) {
            updateStatus(getString("status.git_no_vault"));
            return;
        }
        ListView<GitCommit> list = new ListView<>();
        list.setPrefSize(560, 460);
        list.setPlaceholder(new Label(getString("git.no_history")));
        list.setCellFactory(lv -> new GitCommitCell());

        Dialog<Void> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.setTitle(getString("git.history_title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox box = new VBox(8, list);
        box.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefSize(600, 520);

        Task<List<GitCommit>> task = new Task<>() {
            @Override
            protected List<GitCommit> call() {
                return gitService.history(vault, 200);
            }
        };
        task.setOnSucceeded(e -> list.getItems().setAll(task.getValue()));
        runDaemon(task, "git-history");
        dialog.showAndWait();
    }

    /** Resolves an i18n key via the injected function, returning the key itself as a fallback. */
    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    /** Forwards a status message to the host's status-bar consumer if one is wired. */
    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }

    /** Sets both {@code visible} and {@code managed} on {@code node} so the layout does not reserve space when hidden. */
    private static void setNodeVisible(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    /** Starts {@code task} on a new daemon thread so Git I/O never blocks the JavaFX Application Thread. */
    private static void runDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** List cell for a commit: subject + hash · author · date · refs. */
    private static final class GitCommitCell extends ListCell<GitCommit> {
        @Override
        protected void updateItem(GitCommit commit, boolean empty) {
            super.updateItem(commit, empty);
            if (empty || commit == null) {
                setGraphic(null);
                return;
            }
            Label subject = new Label(commit.message());
            subject.getStyleClass().add("git-commit-subject");
            subject.setWrapText(true);
            String meta = commit.shortHash() + "  ·  " + commit.author() + "  ·  " + commit.shortDate()
                    + (commit.refs() != null && !commit.refs().isBlank() ? "  ·  " + commit.refs() : "");
            Label metaLabel = new Label(meta);
            metaLabel.getStyleClass().add("git-commit-meta");
            setGraphic(new VBox(3, subject, metaLabel));
        }
    }
}
