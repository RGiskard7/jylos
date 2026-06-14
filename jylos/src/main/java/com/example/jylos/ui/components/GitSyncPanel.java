package com.example.jylos.ui.components;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.git.GitChange;
import com.example.jylos.git.GitResult;
import com.example.jylos.git.GitService;
import com.example.jylos.git.GitStatus;
import com.example.jylos.ui.UiDialogs;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Consolidated, IDE-style "Git Sync" panel for the Markdown vault: a single window
 * that shows the repository state (branch, remote, ahead/behind, conflicts), a unified
 * list of working-tree changes with {@code M / A / D / ?? / UU} prefixes, a commit
 * message field, an activity log, and the full set of safe operations — refresh, stage
 * all, unstage all, commit, pull, push and one-shot sync.
 *
 * <h3>Philosophy</h3>
 * <em>Your notes, your repository, your control.</em> Every action is explicit: nothing
 * destructive runs automatically, there is no force push, and conflicts are surfaced for
 * manual resolution rather than auto-merged.
 *
 * <h3>Threading</h3>
 * All Git work runs off the JavaFX Application Thread on short-lived daemon {@code Task}s;
 * while one runs the action buttons are disabled and an indeterminate progress bar shows.
 * This is a pure view over {@link GitService}; it holds no Git logic of its own.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class GitSyncPanel {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GitService git;
    private final Path vault;
    private final Function<String, String> i18n;
    private final Scene owner;

    private final Dialog<Void> dialog = new Dialog<>();

    // Repository state header.
    private final Label branchLabel = new Label();
    private final Label remoteLabel = new Label();
    private final Label summaryLabel = new Label();
    private final Label conflictBanner = new Label();

    // Working-tree changes.
    private final ListView<GitChange> changesList = new ListView<>();

    // Commit + operations.
    private final TextArea commitMessage = new TextArea();
    private final Button refreshBtn = new Button();
    private final Button stageAllBtn = new Button();
    private final Button unstageAllBtn = new Button();
    private final Button commitBtn = new Button();
    private final Button pullBtn = new Button();
    private final Button pushBtn = new Button();
    private final Button syncBtn = new Button();
    private final Button setRemoteBtn = new Button();

    // Setup state (not a repository yet).
    private final Button initBtn = new Button();
    private final Label setupHint = new Label();

    // Feedback.
    private final ProgressBar progress = new ProgressBar();
    private final TextArea logArea = new TextArea();

    // State containers, toggled per refresh.
    private final VBox repoView = new VBox(10);
    private final VBox setupView = new VBox(12);
    private final VBox unavailableView = new VBox(12);
    private final StackPane stateStack = new StackPane(repoView, setupView, unavailableView);

    /** All action buttons, for bulk enable/disable while an operation runs. */
    private final List<Button> actionButtons = List.of(
            refreshBtn, stageAllBtn, unstageAllBtn, commitBtn, pullBtn, pushBtn, syncBtn, setRemoteBtn, initBtn);

    public GitSyncPanel(GitService git, Path vault, Function<String, String> i18n, Scene owner) {
        this.git = git;
        this.vault = vault;
        this.i18n = i18n;
        this.owner = owner;
        build();
    }

    /** Builds, themes and shows the panel modally, kicking off an initial refresh. */
    public void show() {
        if (owner != null) {
            dialog.initOwner(owner.getWindow());
        }
        UiDialogs.apply(dialog);
        refresh();
        dialog.showAndWait();
    }

    // ── Construction ────────────────────────────────────────────────────────────

    private void build() {
        dialog.setTitle(str("git.panel.title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(560, 680);

        buildRepoView();
        buildSetupView();
        buildUnavailableView();

        progress.setVisible(false);
        progress.setManaged(false);
        progress.setMaxWidth(Double.MAX_VALUE);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(5);
        logArea.getStyleClass().add("git-log-area");
        logArea.setFocusTraversable(false);

        Label logHeader = new Label(str("git.panel.log"));
        logHeader.getStyleClass().add("git-section-header");

        VBox root = new VBox(10, stateStack, progress, logHeader, logArea);
        VBox.setVgrow(stateStack, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(root);
    }

    private void buildRepoView() {
        branchLabel.getStyleClass().add("git-status-branch");
        remoteLabel.getStyleClass().add("git-status-remote");
        remoteLabel.setMaxWidth(Double.MAX_VALUE); // ellipsize long remote URLs instead of overflowing
        summaryLabel.getStyleClass().add("git-status-summary");
        conflictBanner.getStyleClass().add("git-conflict-banner");
        conflictBanner.setWrapText(true);
        conflictBanner.setVisible(false);
        conflictBanner.setManaged(false);

        // Branch (bold) on the left with the change summary aligned right; the remote URL
        // sits on its own muted line below so mismatched label sizes never share a baseline.
        HBox topLine = new HBox(8, branchLabel, spacer(), summaryLabel);
        topLine.setAlignment(Pos.CENTER_LEFT);
        VBox headerLine = new VBox(4, topLine, remoteLabel);

        Label changesHeader = new Label(str("git.panel.changes_section"));
        changesHeader.getStyleClass().add("git-section-header");

        changesList.setPlaceholder(new Label(str("git.panel.no_changes")));
        changesList.setCellFactory(lv -> new GitChangeCell());
        VBox.setVgrow(changesList, Priority.ALWAYS);

        configureButton(refreshBtn, "git.panel.refresh", this::refresh);
        configureButton(stageAllBtn, "git.panel.stage_all", () -> runOp("git.panel.stage_all", () -> git.stageAll(vault)));
        configureButton(unstageAllBtn, "git.panel.unstage_all",
                () -> runOp("git.panel.unstage_all", () -> git.unstageAll(vault)));
        configureButton(setRemoteBtn, "git.panel.add_remote", this::promptSetRemote);

        HBox stagingRow = new HBox(8, refreshBtn, stageAllBtn, unstageAllBtn, spacer(), setRemoteBtn);
        stagingRow.setAlignment(Pos.CENTER_LEFT);

        Label commitHeader = new Label(str("git.panel.commit_message"));
        commitHeader.getStyleClass().add("git-section-header");
        commitMessage.setPromptText(str("git.panel.commit_placeholder"));
        commitMessage.setWrapText(true);
        commitMessage.setPrefRowCount(3);

        configureButton(commitBtn, "git.panel.commit", this::doCommit);
        commitBtn.getStyleClass().add("git-primary-btn");
        configureButton(pullBtn, "git.panel.pull", () -> runOp("git.panel.pull", () -> git.pull(vault)));
        configureButton(pushBtn, "git.panel.push", () -> runOp("git.panel.push", () -> git.push(vault)));
        configureButton(syncBtn, "git.panel.sync", this::doSync);
        syncBtn.getStyleClass().add("git-primary-btn");

        HBox opsRow = new HBox(8, commitBtn, spacer(), pullBtn, pushBtn, syncBtn);
        opsRow.setAlignment(Pos.CENTER_LEFT);

        repoView.getChildren().setAll(headerLine, conflictBanner, changesHeader, changesList,
                stagingRow, new Separator(), commitHeader, commitMessage, opsRow);
    }

    private void buildSetupView() {
        Label title = new Label(str("git.panel.not_repo"));
        title.getStyleClass().add("git-section-header");
        title.setWrapText(true);
        setupHint.setText(str("git.panel.init_hint"));
        setupHint.getStyleClass().add("git-change-path");
        setupHint.setWrapText(true);
        configureButton(initBtn, "git.panel.init", () -> runOp("git.panel.init", () -> git.init(vault)));
        initBtn.getStyleClass().add("git-primary-btn");
        setupView.setAlignment(Pos.CENTER_LEFT);
        setupView.getChildren().setAll(title, setupHint, initBtn);
    }

    private void buildUnavailableView() {
        Label msg = new Label(str("git.panel.git_unavailable"));
        msg.getStyleClass().add("git-conflict-banner");
        msg.setWrapText(true);
        unavailableView.getChildren().setAll(msg);
    }

    private void configureButton(Button button, String key, Runnable action) {
        button.setText(str(key));
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ── Refresh ─────────────────────────────────────────────────────────────────

    /** Reloads availability, repository state and the change list off the FX thread. */
    private void refresh() {
        setBusy(true);
        Task<Snapshot> task = new Task<>() {
            @Override
            protected Snapshot call() {
                if (!git.isGitAvailable()) {
                    return new Snapshot(false, false, GitStatus.none(), List.of(), null);
                }
                if (!git.isRepository(vault)) {
                    return new Snapshot(true, false, GitStatus.none(), List.of(), null);
                }
                return new Snapshot(true, true, git.status(vault), git.listChanges(vault), git.getRemoteUrl(vault));
            }
        };
        task.setOnSucceeded(e -> {
            applySnapshot(task.getValue());
            setBusy(false);
        });
        task.setOnFailed(e -> {
            appendLog(str("git.panel.git_unavailable"));
            setBusy(false);
        });
        runDaemon(task, "git-panel-refresh");
    }

    private void applySnapshot(Snapshot snap) {
        showState(snap.available() ? (snap.repo() ? repoView : setupView) : unavailableView);
        if (!snap.available() || !snap.repo()) {
            return;
        }
        GitStatus status = snap.status();
        branchLabel.setText(str("git.panel.branch") + ": "
                + (status.branch() == null || status.branch().isBlank() ? "—" : status.branch()));

        String remoteText = snap.remoteUrl() != null
                ? str("git.panel.remote") + ": " + snap.remoteUrl()
                : str("git.panel.no_remote_short");
        remoteLabel.setText(remoteText);

        long conflicts = snap.changes().stream().filter(GitSyncPanel::isConflicted).count();
        StringBuilder summary = new StringBuilder(MessageFormat.format(str("git.panel.summary"), snap.changes().size()));
        if (status.ahead() > 0 || status.behind() > 0) {
            summary.append("   ").append(MessageFormat.format(str("git.panel.ahead_behind"),
                    status.ahead(), status.behind()));
        }
        summaryLabel.setText(summary.toString());

        if (conflicts > 0) {
            conflictBanner.setText(MessageFormat.format(str("git.panel.conflicts"), conflicts));
        }
        conflictBanner.setVisible(conflicts > 0);
        conflictBanner.setManaged(conflicts > 0);

        // Conflicts first, then staged, then the rest — the order a user resolves them in.
        List<GitChange> ordered = snap.changes().stream()
                .sorted((a, b) -> Integer.compare(rank(a), rank(b)))
                .toList();
        changesList.getItems().setAll(ordered);

        boolean hasRemote = snap.remoteUrl() != null;
        pullBtn.setDisable(!hasRemote);
        pushBtn.setDisable(!hasRemote);
        // Sync still works locally (commit only) but is most useful with a remote.
    }

    private static int rank(GitChange c) {
        if (isConflicted(c)) {
            return 0;
        }
        return c.staged() ? 1 : 2;
    }

    private void showState(VBox active) {
        for (VBox view : List.of(repoView, setupView, unavailableView)) {
            boolean on = view == active;
            view.setVisible(on);
            view.setManaged(on);
        }
    }

    // ── Operations ──────────────────────────────────────────────────────────────

    private void doCommit() {
        String message = commitMessageOrDefault();
        runOp("git.panel.commit", () -> {
            GitResult r = git.commit(vault, message);
            if (r.ok()) {
                javafx.application.Platform.runLater(commitMessage::clear);
            }
            return r;
        });
    }

    private void doSync() {
        String message = commitMessageOrDefault();
        runOp("git.panel.sync", () -> {
            GitResult r = git.sync(vault, message);
            if (r.ok()) {
                javafx.application.Platform.runLater(commitMessage::clear);
            }
            return r;
        });
    }

    private void promptSetRemote() {
        TextInputDialog input = new TextInputDialog(git.getRemoteUrl(vault));
        input.setTitle(str("dialog.git_remote.title"));
        input.setHeaderText(str("dialog.git_remote.header"));
        input.setContentText(str("dialog.git_remote.content"));
        if (owner != null) {
            input.initOwner(owner.getWindow());
        }
        UiDialogs.apply(input);
        input.showAndWait().filter(url -> !url.isBlank())
                .ifPresent(url -> runOp("git.panel.add_remote", () -> git.setRemote(vault, url.trim())));
    }

    /**
     * Runs a Git operation off the FX thread: disables the controls, shows progress,
     * logs the running line and the outcome, then refreshes the view.
     */
    private void runOp(String runningKey, Supplier<GitResult> op) {
        setBusy(true);
        appendLog("▸ " + str(runningKey));
        Task<GitResult> task = new Task<>() {
            @Override
            protected GitResult call() {
                return op.get();
            }
        };
        task.setOnSucceeded(e -> {
            appendLog(describe(task.getValue()));
            refresh(); // re-enables controls on completion
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            appendLog("✖ " + (ex != null ? ex.getMessage() : str("status.git_error")));
            setBusy(false);
        });
        runDaemon(task, "git-panel-op");
    }

    /** Formats a result for the log: a glyph for the category plus the service message. */
    private String describe(GitResult result) {
        if (result == null) {
            return "✖ " + str("status.git_error");
        }
        String glyph = switch (result.status()) {
            case OK -> "✔";
            case NOTHING_TO_DO -> "•";
            case NO_REMOTE -> "•";
            case CONFLICT -> "⚠";
            default -> "✖";
        };
        String message = result.message() != null ? result.message().replaceAll("\\s+", " ").trim() : "";
        return glyph + " " + (message.isEmpty() ? result.status().name() : message);
    }

    // ── UI helpers ──────────────────────────────────────────────────────────────

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        progress.setManaged(busy);
        progress.setProgress(busy ? ProgressBar.INDETERMINATE_PROGRESS : 0);
        for (Button b : actionButtons) {
            b.setDisable(busy);
        }
    }

    private void appendLog(String line) {
        String stamp = LocalTime.now().format(LOG_TIME);
        logArea.appendText(stamp + "  " + line + "\n");
    }

    private String commitMessageOrDefault() {
        String text = commitMessage.getText();
        return (text == null || text.isBlank())
                ? "Jylos sync " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now())
                : text.trim();
    }

    private String str(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private static boolean isConflicted(GitChange c) {
        return "conflicted".equals(c.status());
    }

    /** Two-character VCS prefix shown in the change list. */
    private static String prefix(GitChange c) {
        return switch (c.status()) {
            case "modified" -> "M";
            case "added" -> "A";
            case "deleted" -> "D";
            case "renamed" -> "R";
            case "copied" -> "C";
            case "untracked" -> "??";
            case "conflicted" -> "UU";
            default -> "•";
        };
    }

    private static void runDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Immutable view-model of one panel refresh. */
    private record Snapshot(boolean available, boolean repo, GitStatus status,
            List<GitChange> changes, String remoteUrl) {
    }

    /**
     * Row in the change list: a status badge, the file name + path, line stats, and an
     * inline stage/unstage toggle. Conflicted rows show a non-actionable "conflict" tag
     * instead — they must be resolved on disk before they can be staged.
     */
    private final class GitChangeCell extends ListCell<GitChange> {
        @Override
        protected void updateItem(GitChange change, boolean empty) {
            super.updateItem(change, empty);
            if (empty || change == null) {
                setGraphic(null);
                return;
            }
            Label badge = new Label(prefix(change));
            badge.getStyleClass().add("git-change-badge");
            if (isConflicted(change)) {
                badge.getStyleClass().add("git-change-badge-conflict");
            } else if (change.staged()) {
                badge.getStyleClass().add("git-change-badge-staged");
            }

            Label title = new Label(change.displayTitle());
            title.getStyleClass().add("git-change-title");
            Label path = new Label(change.relativePath());
            path.getStyleClass().add("git-change-path");
            VBox texts = new VBox(2, title, path);

            Label stats = new Label(stats(change));
            stats.getStyleClass().add("git-change-stats");

            HBox row = new HBox(10, badge, texts, spacer(), stats, trailingControl(change));
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }

        private Region trailingControl(GitChange change) {
            if (isConflicted(change)) {
                Label tag = new Label(str("git.panel.conflict_tag"));
                tag.getStyleClass().add("git-change-conflict-tag");
                return tag;
            }
            Button toggle = new Button(change.staged() ? "−" : "+");
            toggle.getStyleClass().add("git-stage-btn");
            toggle.setFocusTraversable(false);
            toggle.setTooltip(new Tooltip(str(change.staged() ? "git.unstage" : "git.stage")));
            toggle.setOnAction(e -> runOp(change.staged() ? "git.unstage" : "git.stage",
                    () -> change.staged() ? git.unstage(vault, change.relativePath())
                            : git.stage(vault, change.relativePath())));
            return toggle;
        }

        private String stats(GitChange change) {
            StringBuilder sb = new StringBuilder();
            if (change.added() >= 0) {
                sb.append("+").append(change.added());
            }
            if (change.deleted() > 0) {
                sb.append(sb.length() > 0 ? " " : "").append("−").append(change.deleted());
            }
            return sb.toString();
        }
    }
}
