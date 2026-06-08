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

import com.example.jylos.git.GitChange;
import com.example.jylos.git.GitCommit;
import com.example.jylos.git.GitResult;
import com.example.jylos.git.GitService;
import com.example.jylos.git.GitStatus;
import com.example.jylos.ui.UiDialogs;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

    public void sync() {
        runGitAsync(vault -> gitService.sync(vault, gitCommitMessage()), getString("status.git_syncing"));
    }

    public void commitPush() {
        final String message = gitCommitMessage();
        runGitAsync(vault -> {
            GitResult commit = gitService.commit(vault, message);
            return commit.ok() ? gitService.push(vault) : commit;
        }, getString("status.git_syncing"));
    }

    public void pull() {
        runGitAsync(gitService::pull, getString("status.git_pulling"));
    }

    public void init() {
        runGitAsync(gitService::init, getString("status.git_initializing"));
    }

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

    /** Commit dialog: write a message and commit (optionally pushing). */
    public void showCommitDialog() {
        if (gitVaultPath() == null) {
            updateStatus(getString("status.git_no_vault"));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.setTitle(getString("dialog.git_commit.title"));
        dialog.setHeaderText(getString("dialog.git_commit.header"));
        ButtonType commitType = new ButtonType(getString("git.commit"), ButtonBar.ButtonData.OK_DONE);
        ButtonType commitPushType = new ButtonType(getString("git.commit_push"), ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(commitType, commitPushType, ButtonType.CANCEL);

        TextArea messageArea = new TextArea(gitCommitMessage());
        messageArea.setPrefRowCount(3);
        messageArea.setWrapText(true);
        VBox box = new VBox(8, messageArea);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(choice -> {
            String message = messageArea.getText().isBlank() ? gitCommitMessage() : messageArea.getText().trim();
            if (choice == commitType) {
                runGitAsync(v -> gitService.commit(v, message), getString("status.git_syncing"));
            } else if (choice == commitPushType) {
                runGitAsync(v -> {
                    GitResult commit = gitService.commit(v, message);
                    return commit.ok() ? gitService.push(v) : commit;
                }, getString("status.git_syncing"));
            }
        });
    }

    /**
     * Changes dialog (IDE-style): a "staged" section on top, a separator, and a
     * "not staged" section below. The {@code +}/{@code −} button on each row moves it
     * between sections.
     */
    public void showChangesDialog() {
        Path vault = gitVaultPath();
        if (vault == null) {
            updateStatus(getString("status.git_no_vault"));
            return;
        }
        ListView<GitChange> stagedList = new ListView<>();
        ListView<GitChange> unstagedList = new ListView<>();
        Label stagedHeader = new Label();
        Label unstagedHeader = new Label();
        stagedHeader.getStyleClass().add("git-section-header");
        unstagedHeader.getStyleClass().add("git-section-header");
        stagedList.setPlaceholder(new Label(getString("git.none_staged")));
        unstagedList.setPlaceholder(new Label(getString("git.no_changes")));

        Runnable reload = () -> reloadChangeSections(vault, stagedList, unstagedList, stagedHeader, unstagedHeader);
        stagedList.setCellFactory(lv -> new GitChangeCell(vault, reload));
        unstagedList.setCellFactory(lv -> new GitChangeCell(vault, reload));

        VBox.setVgrow(stagedList, Priority.ALWAYS);
        VBox.setVgrow(unstagedList, Priority.ALWAYS);
        Separator separator = new Separator();
        separator.getStyleClass().add("git-section-separator");

        VBox box = new VBox(6, stagedHeader, stagedList, separator, unstagedHeader, unstagedList);
        box.setPadding(new Insets(8));

        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.setTitle(getString("git.changes_title"));
        ButtonType commitType = new ButtonType(getString("git.commit"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(commitType, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefSize(480, 560);

        reload.run();
        dialog.showAndWait().ifPresent(choice -> {
            if (choice == commitType) {
                showCommitDialog();
            }
        });
    }

    private void reloadChangeSections(Path vault, ListView<GitChange> staged,
            ListView<GitChange> unstaged, Label stagedHeader, Label unstagedHeader) {
        Task<List<GitChange>> task = new Task<>() {
            @Override
            protected List<GitChange> call() {
                return gitService.listChanges(vault);
            }
        };
        task.setOnSucceeded(e -> {
            List<GitChange> all = task.getValue();
            List<GitChange> stagedItems = all.stream().filter(GitChange::staged).toList();
            List<GitChange> unstagedItems = all.stream().filter(c -> !c.staged()).toList();
            staged.getItems().setAll(stagedItems);
            unstaged.getItems().setAll(unstagedItems);
            stagedHeader.setText(MessageFormat.format(getString("git.section_staged"), stagedItems.size()));
            unstagedHeader.setText(MessageFormat.format(getString("git.section_unstaged"), unstagedItems.size()));
        });
        runDaemon(task, "git-changes");
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

    private String formatChangeStats(GitChange change) {
        StringBuilder sb = new StringBuilder(change.status());
        if (change.added() >= 0) {
            sb.append("   +").append(change.added());
        }
        if (change.deleted() > 0) {
            sb.append(" −").append(change.deleted());
        }
        return sb.toString();
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }

    private static void setNodeVisible(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private static void runDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** List cell for a changed file: title, path, +added/−deleted and a stage toggle. */
    private final class GitChangeCell extends ListCell<GitChange> {
        private final Path vault;
        private final Runnable onChanged;

        GitChangeCell(Path vault, Runnable onChanged) {
            this.vault = vault;
            this.onChanged = onChanged;
        }

        @Override
        protected void updateItem(GitChange change, boolean empty) {
            super.updateItem(change, empty);
            if (empty || change == null) {
                setGraphic(null);
                return;
            }
            Label title = new Label(change.displayTitle());
            title.getStyleClass().add("git-change-title");
            Label path = new Label(change.fileName());
            path.getStyleClass().add("git-change-path");
            Label stats = new Label(formatChangeStats(change));
            stats.getStyleClass().add("git-change-stats");
            VBox texts = new VBox(2, title, path, stats);

            Button stageBtn = new Button(change.staged() ? "−" : "+");
            stageBtn.getStyleClass().add("git-stage-btn");
            stageBtn.setTooltip(new Tooltip(getString(change.staged() ? "git.unstage" : "git.stage")));
            stageBtn.setOnAction(e -> {
                Task<GitResult> task = new Task<>() {
                    @Override
                    protected GitResult call() {
                        return change.staged()
                                ? gitService.unstage(vault, change.relativePath())
                                : gitService.stage(vault, change.relativePath());
                    }
                };
                task.setOnSucceeded(ev -> onChanged.run());
                runDaemon(task, "git-stage");
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(8, texts, spacer, stageBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
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
