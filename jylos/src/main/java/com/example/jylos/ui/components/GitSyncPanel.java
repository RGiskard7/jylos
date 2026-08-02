package com.example.jylos.ui.components;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * Consolidated, IDE-style "Git Sync" panel for the Markdown vault: a single window
 * that shows the repository state (branch, remote, ahead/behind, conflicts), a unified
 * list of working-tree changes with {@code M / A / D / ?? / UU} prefixes, a commit
 * message field, an activity log, local branch controls, and the full set of
 * safe operations — refresh, stage all, unstage all, commit, pull, push and one-shot sync.
 *
 * <h2>Philosophy</h2>
 * <em>Your notes, your repository, your control.</em> Every action is explicit: nothing
 * destructive runs automatically, there is no force push, and conflicts are surfaced for
 * manual resolution rather than auto-merged.
 *
 * <h2>Threading</h2>
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
    private final Runnable refreshVaultAfterRemoteUpdate;

    private final Dialog<Void> dialog = new Dialog<>();

    // Repository state header.
    private final Label remoteLabel = new Label();
    private final Label summaryLabel = new Label();
    private final Label conflictBanner = new Label();
    private final Label workflowHint = new Label();
    private final MenuButton branchMenu = new MenuButton();

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
    private final Label operationLabel = new Label();
    private final Button cancelBtn = new Button();
    private final TextArea logArea = new TextArea();
    private final AtomicBoolean progressUpdatePending = new AtomicBoolean();

    /** Latest remote-transfer line, coalesced before it reaches the JavaFX thread. */
    private volatile String pendingProgress = "";

    private FocusTarget focusTarget = FocusTarget.OVERVIEW;
    private boolean initialFocusPending;
    private boolean busy;
    private boolean gitAvailable;
    private boolean repositoryReady;
    private boolean remoteConfigured;
    private GitStatus displayedStatus = GitStatus.none();

    // State containers, toggled per refresh.
    private final VBox repoView = new VBox(10);
    private final VBox setupView = new VBox(12);
    private final VBox unavailableView = new VBox(12);
    private final VBox loadingView = new VBox(12);
    private final StackPane stateStack = new StackPane(repoView, setupView, unavailableView, loadingView);

    /** All action buttons, for bulk enable/disable while an operation runs. */
    private final List<Button> actionButtons = List.of(
            refreshBtn, stageAllBtn, unstageAllBtn, commitBtn, pullBtn, pushBtn, syncBtn, setRemoteBtn, initBtn);

    public GitSyncPanel(GitService git, Path vault, Function<String, String> i18n, Scene owner,
            Runnable refreshVaultAfterRemoteUpdate) {
        this.git = git;
        this.vault = vault;
        this.i18n = i18n;
        this.owner = owner;
        this.refreshVaultAfterRemoteUpdate = refreshVaultAfterRemoteUpdate;
        build();
    }

    /** Builds, themes and shows the panel modally, kicking off an initial refresh. */
    public void show() {
        show(FocusTarget.OVERVIEW);
    }

    /** Opens the workspace focused on a specific Git task from the status bar. */
    public void show(FocusTarget target) {
        focusTarget = target != null ? target : FocusTarget.OVERVIEW;
        initialFocusPending = true;
        if (owner != null) {
            dialog.initOwner(owner.getWindow());
        }
        UiDialogs.apply(dialog);
        // Opening the workspace must be immediate and never initiate network I/O.
        // The explicit Refresh action fetches the remote when the user requests it.
        refresh(false);
        dialog.showAndWait();
    }

    // ── Construction ────────────────────────────────────────────────────────────

    /** Assembles the dialog: title, close button, state stack, progress bar, and log area. */
    private void build() {
        dialog.setTitle(str("git.panel.title"));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStyleClass().add("git-sync-dialog");
        dialog.getDialogPane().setPrefSize(880, 720);
        dialog.getDialogPane().setMinSize(680, 560);
        dialog.setOnShown(e -> sizeWindowForWorkspace());
        dialog.setOnCloseRequest(e -> {
            if (busy) {
                git.cancelActiveOperation();
            }
        });

        buildRepoView();
        buildSetupView();
        buildUnavailableView();
        buildLoadingView();

        progress.setVisible(false);
        progress.setManaged(false);
        progress.setMaxWidth(Double.MAX_VALUE);
        operationLabel.getStyleClass().add("git-operation-status");
        operationLabel.setText(str("git.panel.ready"));
        configureButton(cancelBtn, "git.panel.cancel_operation", this::cancelRunningOperation);
        cancelBtn.getStyleClass().add("git-cancel-operation-btn");
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(7);
        logArea.setMinHeight(120);
        logArea.getStyleClass().add("git-log-area");
        logArea.setFocusTraversable(false);

        Label logHeader = new Label(str("git.panel.log"));
        logHeader.getStyleClass().add("git-section-header");

        VBox activity = new VBox(6, logHeader, logArea);
        activity.getStyleClass().add("git-sync-activity");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        stateStack.getStyleClass().add("git-sync-state");
        showState(loadingView);
        HBox operationRow = new HBox(8, operationLabel, spacer(), cancelBtn);
        operationRow.setAlignment(Pos.CENTER_LEFT);
        VBox root = new VBox(10, operationRow, progress, stateStack, activity);
        root.getStyleClass().add("git-sync-root");
        VBox.setVgrow(activity, Priority.ALWAYS);
        root.setPadding(new Insets(12, 12, 10, 12));
        dialog.getDialogPane().setContent(root);
    }

    /** Constructs the repository-state view: header, conflict banner, change list, staging controls, commit field, and action row. */
    private void buildRepoView() {
        branchMenu.getStyleClass().add("git-branch-menu");
        branchMenu.setFocusTraversable(false);
        branchMenu.setOnShowing(e -> loadBranchMenu());
        remoteLabel.getStyleClass().add("git-status-remote");
        remoteLabel.setMaxWidth(Double.MAX_VALUE); // ellipsize long remote URLs instead of overflowing
        summaryLabel.getStyleClass().add("git-status-summary");
        conflictBanner.getStyleClass().add("git-conflict-banner");
        conflictBanner.setWrapText(true);
        conflictBanner.setVisible(false);
        conflictBanner.setManaged(false);
        workflowHint.getStyleClass().add("git-workflow-hint");
        workflowHint.setWrapText(true);

        // The branch is an explicit menu. It is not a passive label, so users can see
        // where to create or switch branches without guessing from the status bar.
        HBox topLine = new HBox(8, branchMenu, spacer(), summaryLabel);
        topLine.setAlignment(Pos.CENTER_LEFT);
        VBox headerLine = new VBox(4, topLine, remoteLabel);

        Label changesHeader = new Label(str("git.panel.changes_section"));
        changesHeader.getStyleClass().add("git-section-header");

        changesList.setPlaceholder(new Label(str("git.panel.no_changes")));
        changesList.setCellFactory(lv -> new GitChangeCell());
        changesList.getStyleClass().add("git-sync-changes");
        changesList.setPrefHeight(155);
        changesList.setMinHeight(110);
        changesList.setMaxHeight(190);

        configureButton(refreshBtn, "git.panel.refresh", () -> refresh(true));
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
        configureButton(pullBtn, "git.panel.pull",
                () -> runOp("git.panel.pull", progressListener -> git.pull(vault, progressListener), true));
        configureButton(pushBtn, "git.panel.push",
                () -> runOp("git.panel.push", progressListener -> git.push(vault, progressListener), false));
        configureButton(syncBtn, "git.panel.sync", this::doSync);
        syncBtn.getStyleClass().add("git-primary-btn");

        HBox opsRow = new HBox(8, commitBtn, spacer(), pullBtn, pushBtn, syncBtn);
        opsRow.setAlignment(Pos.CENTER_LEFT);

        repoView.getStyleClass().add("git-sync-repository");
        repoView.getChildren().setAll(headerLine, workflowHint, conflictBanner, changesHeader, changesList,
                stagingRow, new Separator(), commitHeader, commitMessage, opsRow);
        commitMessage.setPrefRowCount(2);
        commitMessage.setMinHeight(54);
        commitMessage.setMaxHeight(66);
    }

    /** Constructs the "not a Git repository yet" setup view with an init hint and button. */
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

    /** Constructs the view shown when {@code git} is not installed or cannot be located on the system {@code PATH}. */
    private void buildUnavailableView() {
        Label msg = new Label(str("git.panel.git_unavailable"));
        msg.getStyleClass().add("git-conflict-banner");
        msg.setWrapText(true);
        unavailableView.getChildren().setAll(msg);
    }

    /** Builds the stable initial state shown while the first repository snapshot is loading. */
    private void buildLoadingView() {
        Label loading = new Label(str("git.panel.refreshing"));
        loading.getStyleClass().add("git-operation-status");
        loadingView.setAlignment(Pos.CENTER);
        loadingView.getChildren().setAll(loading);
    }

    /** Sets the button's i18n label, action handler, and removes focus traversal (toolbar-style appearance). */
    private void configureButton(Button button, String key, Runnable action) {
        button.setText(str(key));
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
    }

    /** Creates a greedy-grow spacer that pushes adjacent controls to opposite ends of an {@link HBox}. */
    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ── Refresh ─────────────────────────────────────────────────────────────────

    /** Reloads availability, repository state and the change list off the FX thread. */
    private void refresh(boolean fetchRemote) {
        refresh(fetchRemote, str("git.panel.ready"));
    }

    /** Refreshes state and retains the outcome of the last completed action. */
    private void refresh(boolean fetchRemote, String completionMessage) {
        setBusy(true, str("git.panel.refreshing"));
        Task<Snapshot> task = new Task<>() {
            @Override
            protected Snapshot call() {
                if (!git.isGitAvailable()) {
                    return new Snapshot(false, false, GitStatus.none(), List.of(), null, null);
                }
                if (!git.isRepository(vault)) {
                    return new Snapshot(true, false, GitStatus.none(), List.of(), null, null);
                }
                String remoteUrl = git.getRemoteUrl(vault);
                GitResult fetchResult = fetchRemote && remoteUrl != null ? git.fetchRemote(vault) : null;
                GitStatus status = git.status(vault);
                return new Snapshot(true, true, status, git.listChanges(vault), remoteUrl, fetchResult);
            }
        };
        task.setOnSucceeded(e -> {
            Snapshot snapshot = task.getValue();
            applySnapshot(snapshot);
            String message = snapshot.fetchResult() != null ? describe(snapshot.fetchResult()) : completionMessage;
            if (snapshot.fetchResult() != null) {
                appendLog(message);
            }
            setBusy(false, message);
            if (initialFocusPending) {
                initialFocusPending = false;
                javafx.application.Platform.runLater(this::applyInitialFocus);
            }
        });
        task.setOnFailed(e -> {
            String message = str("git.panel.refresh_failed");
            appendLog("✖ " + message);
            setBusy(false, message);
        });
        runDaemon(task, "git-panel-refresh");
    }

    /** Applies a completed refresh snapshot to the UI: selects the correct state view, fills labels, sorts the change list, and enables/disables remote-dependent buttons. */
    private void applySnapshot(Snapshot snap) {
        gitAvailable = snap.available();
        repositoryReady = snap.repo();
        displayedStatus = snap.status();
        remoteConfigured = snap.remoteUrl() != null;
        showState(snap.available() ? (snap.repo() ? repoView : setupView) : unavailableView);
        if (!snap.available() || !snap.repo()) {
            return;
        }
        GitStatus status = snap.status();
        String branch = status.branch() == null || status.branch().isBlank() ? "—" : status.branch();
        branchMenu.setText(branch);
        branchMenu.setDisable(busy);

        String remoteText = snap.remoteUrl() != null
                ? str("git.panel.remote") + ": " + snap.remoteUrl()
                : str("git.panel.no_remote_short");
        if (snap.remoteUrl() != null && !status.hasUpstream()) {
            remoteText += "  ·  " + str("git.panel.no_upstream");
        }
        remoteLabel.setText(remoteText);

        long conflicts = snap.changes().stream().filter(GitSyncPanel::isConflicted).count();
        long nestedRepositories = snap.changes().stream().filter(GitSyncPanel::isNestedRepositoryDirty).count();
        long stagedChanges = snap.changes().stream().filter(GitChange::staged).count();
        long unstagedChanges = snap.changes().size() - stagedChanges - conflicts - nestedRepositories;
        StringBuilder summary = new StringBuilder(MessageFormat.format(str("git.panel.summary"), status.modified()));
        summary.append("   ").append(MessageFormat.format(str("git.panel.staging_summary"),
                stagedChanges, unstagedChanges));
        if (status.ahead() > 0 || status.behind() > 0) {
            summary.append("   ").append(MessageFormat.format(str("git.panel.ahead_behind"),
                    status.ahead(), status.behind()));
        }
        summaryLabel.setText(summary.toString());

        if (conflicts > 0) {
            conflictBanner.setText(MessageFormat.format(str("git.panel.conflicts"), conflicts));
        } else if (nestedRepositories > 0) {
            conflictBanner.setText(str("git.panel.nested_repository_dirty"));
        }
        boolean hasBlockingChanges = conflicts > 0 || nestedRepositories > 0;
        conflictBanner.setVisible(hasBlockingChanges);
        conflictBanner.setManaged(hasBlockingChanges);

        // Conflicts first, then staged, then the rest — the order a user resolves them in.
        List<GitChange> ordered = snap.changes().stream()
                .sorted((a, b) -> Integer.compare(rank(a), rank(b)))
                .toList();
        changesList.getItems().setAll(ordered);

        updateWorkflowHint(status, remoteConfigured);
    }

    /** Returns a sort priority for a change: blockers first, then staged and unstaged changes. */
    private static int rank(GitChange c) {
        if (isConflicted(c)) {
            return 0;
        }
        if (isNestedRepositoryDirty(c)) {
            return 1;
        }
        return c.staged() ? 2 : 3;
    }

    /** Makes {@code active} visible and managed while hiding every other state view. */
    private void showState(VBox active) {
        for (VBox view : List.of(repoView, setupView, unavailableView, loadingView)) {
            boolean on = view == active;
            view.setVisible(on);
            view.setManaged(on);
        }
    }

    // ── Operations ──────────────────────────────────────────────────────────────

    /** Commits all staged changes using the current commit-message field (or a default timestamp message), then refreshes. */
    private void doCommit() {
        String message = commitMessageOrDefault();
        runOp("git.panel.commit", () -> {
            GitResult r = git.commit(vault, message);
            if (r.ok()) {
                javafx.application.Platform.runLater(commitMessage::clear);
            }
            return r;
        }, false);
    }

    /** Runs the sync cycle (commit staged changes → pull → push) off the FX thread. */
    private void doSync() {
        String message = commitMessageOrDefault();
        runOp("git.panel.sync", progressListener -> {
            GitResult r = git.sync(vault, message, progressListener);
            if (r.ok()) {
                javafx.application.Platform.runLater(commitMessage::clear);
            }
            return r;
        }, true);
    }

    /** Shows a text-input dialog pre-filled with the current remote URL, then calls {@link GitService#setRemote} on confirm. */
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
                .ifPresent(url -> runOp("git.panel.add_remote", () -> git.setRemote(vault, url.trim()), false));
    }

    /**
     * Runs a Git operation off the FX thread: disables the controls, shows progress,
     * logs the running line and the outcome, then refreshes the view.
     */
    private void runOp(String runningKey, Supplier<GitResult> op) {
        runOp(runningKey, op, false);
    }

    private void runOp(String runningKey, Supplier<GitResult> op, boolean refreshVaultOnSuccess) {
        runOp(runningKey, ignored -> op.get(), refreshVaultOnSuccess);
    }

    private void runOp(String runningKey, GitOperation op, boolean refreshVaultOnSuccess) {
        setBusy(true, str(runningKey) + "…");
        appendLog("▸ " + str(runningKey));
        Task<GitResult> task = new Task<>() {
            @Override
            protected GitResult call() {
                return op.run(GitSyncPanel.this::showGitProgress);
            }
        };
        task.setOnSucceeded(e -> {
            GitResult result = task.getValue();
            appendLog(describe(result));
            if (refreshVaultOnSuccess && result != null && result.status() == GitResult.Status.OK
                    && refreshVaultAfterRemoteUpdate != null) {
                refreshVaultAfterRemoteUpdate.run();
            }
            refresh(false, describe(result)); // re-enables controls without another network request
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            appendLog("✖ " + (ex != null ? ex.getMessage() : str("status.git_error")));
            setBusy(false, str("status.git_error"));
        });
        runDaemon(task, "git-panel-op");
    }

    /** Requests cancellation of the current Git subprocess without blocking the JavaFX thread. */
    private void cancelRunningOperation() {
        if (!busy || !git.cancelActiveOperation()) {
            return;
        }
        cancelBtn.setDisable(true);
        operationLabel.setText(str("git.panel.cancelling"));
        appendLog("▸ " + str("git.panel.cancelling"));
    }

    /** Shows the most recent meaningful line emitted by Git during a remote transfer. */
    private void showGitProgress(String output) {
        String latest = "";
        for (String line : output.replace('\r', '\n').split("\\R")) {
            String sanitized = line.replaceAll("\\u001B\\[[;\\d]*m", "").trim();
            if (!sanitized.isEmpty()) {
                latest = sanitized;
            }
        }
        if (latest.isEmpty()) {
            return;
        }
        pendingProgress = latest;
        scheduleProgressUpdate();
    }

    /** Queues at most one JavaFX update while Git streams a large transfer. */
    private void scheduleProgressUpdate() {
        if (!progressUpdatePending.compareAndSet(false, true)) {
            return;
        }
        javafx.application.Platform.runLater(() -> {
            String message = pendingProgress;
            if (busy) {
                operationLabel.setText(message);
            }
            progressUpdatePending.set(false);
            // A chunk may arrive while this update was queued. Render the newest one,
            // without queueing every byte range emitted by Git.
            if (busy && !message.equals(pendingProgress)) {
                scheduleProgressUpdate();
            }
        });
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

    /** Shows operation state and prevents concurrent Git actions while a task is running. */
    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        progress.setVisible(busy);
        progress.setManaged(busy);
        progress.setProgress(busy ? ProgressBar.INDETERMINATE_PROGRESS : 0);
        operationLabel.setText(message);
        cancelBtn.setVisible(busy);
        cancelBtn.setManaged(busy);
        cancelBtn.setDisable(!busy);
        operationLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"),
                !busy && message.startsWith("✖"));
        if (busy) {
            for (Button b : actionButtons) {
                b.setDisable(true);
            }
            branchMenu.setDisable(true);
            changesList.setDisable(true);
            commitMessage.setDisable(true);
            return;
        }
        applyActionAvailability();
    }

    /** Restores controls from the latest Git snapshot instead of enabling them indiscriminately. */
    private void applyActionAvailability() {
        initBtn.setDisable(!gitAvailable || repositoryReady);
        if (!repositoryReady) {
            for (Button button : List.of(refreshBtn, stageAllBtn, unstageAllBtn, commitBtn,
                    pullBtn, pushBtn, syncBtn, setRemoteBtn)) {
                button.setDisable(true);
            }
            branchMenu.setDisable(true);
            changesList.setDisable(true);
            commitMessage.setDisable(true);
            return;
        }
        refreshBtn.setDisable(false);
        stageAllBtn.setDisable(false);
        unstageAllBtn.setDisable(false);
        commitBtn.setDisable(false);
        pullBtn.setDisable(!remoteConfigured || !displayedStatus.hasUpstream());
        pushBtn.setDisable(!remoteConfigured);
        syncBtn.setDisable(false);
        setRemoteBtn.setDisable(false);
        initBtn.setDisable(true);
        branchMenu.setDisable(false);
        changesList.setDisable(false);
        commitMessage.setDisable(false);
    }

    /** Applies a compact initial size while keeping the workspace user-resizable. */
    private void sizeWindowForWorkspace() {
        Window window = dialog.getDialogPane().getScene() != null
                ? dialog.getDialogPane().getScene().getWindow()
                : null;
        if (window == null) {
            return;
        }
        Screen screen = owner != null && owner.getWindow() != null
                ? Screen.getScreensForRectangle(owner.getWindow().getX(), owner.getWindow().getY(),
                        owner.getWindow().getWidth(), owner.getWindow().getHeight()).stream()
                        .findFirst().orElse(Screen.getPrimary())
                : Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
        window.setWidth(Math.min(880, Math.max(680, bounds.getWidth() - 64)));
        window.setHeight(Math.min(720, Math.max(560, bounds.getHeight() - 96)));
        window.centerOnScreen();
    }

    /** Appends a timestamped line to the activity log text area. */
    private void appendLog(String line) {
        String stamp = LocalTime.now().format(LOG_TIME);
        logArea.appendText(stamp + "  " + line + "\n");
        logArea.positionCaret(logArea.getLength());
    }

    /** Applies the intent of the status-bar entry after the workspace is visible. */
    private void applyInitialFocus() {
        switch (focusTarget) {
            case CHANGES -> changesList.requestFocus();
            case COMMIT -> commitMessage.requestFocus();
            case BRANCH -> {
                loadBranchMenu();
                branchMenu.requestFocus();
                branchMenu.show();
            }
            case REMOTE -> {
                setRemoteBtn.requestFocus();
                promptSetRemote();
            }
            case OVERVIEW -> {
                // The overview deliberately remains passive and explains the next safe step.
            }
        }
    }

    /** Loads local branches asynchronously so opening the menu never blocks JavaFX. */
    private void loadBranchMenu() {
        if (busy) {
            return;
        }
        branchMenu.getItems().setAll(disabledMenuItem(str("git.panel.branch_loading")));
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return git.branches(vault);
            }
        };
        task.setOnSucceeded(e -> populateBranchMenu(task.getValue()));
        task.setOnFailed(e -> populateBranchMenu(List.of()));
        runDaemon(task, "git-branches");
    }

    /** Builds the branch menu from local branches plus a clear create action. */
    private void populateBranchMenu(List<String> branches) {
        branchMenu.getItems().clear();
        if (!git.supportsBranchOperations(vault)) {
            branchMenu.getItems().add(disabledMenuItem(str("git.panel.branch_scope_hint")));
            return;
        }
        String current = branchMenu.getText();
        for (String branch : branches) {
            MenuItem item = new MenuItem(branch.equals(current) ? branch + " ✓" : branch);
            item.setDisable(branch.equals(current));
            item.setOnAction(e -> runOp("git.panel.switch_branch",
                    () -> git.switchBranch(vault, branch), true));
            branchMenu.getItems().add(item);
        }
        if (!branches.isEmpty()) {
            branchMenu.getItems().add(new SeparatorMenuItem());
        }
        MenuItem create = new MenuItem(str("git.panel.new_branch"));
        create.setOnAction(e -> promptCreateBranch());
        branchMenu.getItems().add(create);
    }

    /** Prompts for a new branch name; validation and clean-tree checks stay in GitService. */
    private void promptCreateBranch() {
        TextInputDialog input = new TextInputDialog();
        input.setTitle(str("git.panel.new_branch"));
        input.setHeaderText(str("git.panel.new_branch_header"));
        input.setContentText(str("git.panel.new_branch_name"));
        if (owner != null) {
            input.initOwner(owner.getWindow());
        }
        UiDialogs.apply(input);
        input.showAndWait().map(String::trim).filter(name -> !name.isEmpty())
                .ifPresent(name -> runOp("git.panel.create_branch", () -> git.createBranch(vault, name), true));
    }

    /** Explains the next safe action instead of making the remote/push workflow implicit. */
    private void updateWorkflowHint(GitStatus status, boolean hasRemote) {
        long staged = changesList.getItems().stream().filter(GitChange::staged).count();
        String hint;
        if (changesList.getItems().stream().anyMatch(GitSyncPanel::isNestedRepositoryDirty)) {
            hint = str("git.panel.workflow_nested_repository");
        } else if (!hasRemote) {
            hint = str("git.panel.workflow_no_remote");
        } else if (!status.hasUpstream()) {
            hint = MessageFormat.format(str("git.panel.workflow_first_push"), status.branch());
        } else if (status.behind() > 0) {
            hint = str("git.panel.workflow_pull_first");
        } else if (staged > 0) {
            hint = MessageFormat.format(str("git.panel.workflow_staged"), staged);
        } else if (status.isDirty()) {
            hint = str("git.panel.workflow_stage");
        } else if (status.ahead() > 0) {
            hint = MessageFormat.format(str("git.panel.workflow_push"), status.ahead());
        } else {
            hint = str("git.panel.workflow_clean");
        }
        workflowHint.setText(hint);
    }

    private static MenuItem disabledMenuItem(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    /** Returns the commit-message field text, or a default "Jylos sync yyyy-MM-dd HH:mm" string when it is blank. */
    private String commitMessageOrDefault() {
        String text = commitMessage.getText();
        return (text == null || text.isBlank())
                ? "Jylos sync " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now())
                : text.trim();
    }

    /** Resolves an i18n key via the injected function, returning the key itself if the function is null. */
    private String str(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    /** Returns {@code true} when the change has the "conflicted" status (requires manual resolution before staging). */
    private static boolean isConflicted(GitChange c) {
        return "conflicted".equals(c.status());
    }

    /** Returns whether the row represents work inside a tracked nested Git repository. */
    private static boolean isNestedRepositoryDirty(GitChange c) {
        return "nested_repository_dirty".equals(c.status());
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
            case "nested_repository_dirty" -> "M";
            default -> "•";
        };
    }

    /** Starts {@code task} on a new daemon thread so it does not block the JVM from exiting. */
    private static void runDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Intent used by status-bar entries to focus the unified Git workspace. */
    public enum FocusTarget {
        OVERVIEW,
        CHANGES,
        COMMIT,
        BRANCH,
        REMOTE
    }

    /** Immutable view-model of one panel refresh. */
    private record Snapshot(boolean available, boolean repo, GitStatus status,
            List<GitChange> changes, String remoteUrl, GitResult fetchResult) {
    }

    /** Functional boundary for an asynchronous Git action that may report transfer progress. */
    @FunctionalInterface
    private interface GitOperation {
        GitResult run(Consumer<String> progressListener);
    }

    /**
     * Row in the change list: a status badge, the file name + path, line stats, and an
     * explicit inline stage/unstage action. Conflicted rows show a non-actionable "conflict" tag
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
            if (isConflicted(change) || isNestedRepositoryDirty(change)) {
                badge.getStyleClass().add("git-change-badge-conflict");
            } else if (change.staged()) {
                badge.getStyleClass().add("git-change-badge-staged");
            }

            Label title = new Label(change.displayTitle());
            title.getStyleClass().add("git-change-title");
            title.setMaxWidth(Double.MAX_VALUE);
            Label path = new Label(change.relativePath());
            path.getStyleClass().add("git-change-path");
            path.setMaxWidth(Double.MAX_VALUE);
            String stateKey = isNestedRepositoryDirty(change)
                    ? "git.panel.nested_repository_state"
                    : change.staged() ? "git.staged" : "git.unstaged";
            Label state = new Label(str(stateKey));
            state.getStyleClass().add("git-change-state");
            if (change.staged()) {
                state.getStyleClass().add("git-change-state-staged");
            }
            VBox texts = new VBox(2, title, path, state);
            texts.setMinWidth(0);
            HBox.setHgrow(texts, Priority.ALWAYS);

            Label stats = new Label(stats(change));
            stats.getStyleClass().add("git-change-stats");

            HBox row = new HBox(10, badge, texts, stats, trailingControl(change));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            setGraphic(row);
        }

        private Region trailingControl(GitChange change) {
            if (isConflicted(change)) {
                Label tag = new Label(str("git.panel.conflict_tag"));
                tag.getStyleClass().add("git-change-conflict-tag");
                return tag;
            }
            if (isNestedRepositoryDirty(change)) {
                Label tag = new Label(str("git.panel.nested_repository_tag"));
                tag.getStyleClass().add("git-change-conflict-tag");
                return tag;
            }
            Button toggle = new Button(str(change.staged() ? "git.panel.unstage" : "git.panel.stage"));
            toggle.getStyleClass().add("git-stage-btn");
            toggle.setFocusTraversable(false);
            toggle.setTooltip(new Tooltip(str(change.staged() ? "git.unstage" : "git.stage")));
            toggle.setOnAction(e -> runOp(change.staged() ? "git.unstage" : "git.stage",
                    () -> change.staged() ? git.unstage(vault, change.relativePath())
                            : git.stage(vault, change.relativePath())));
            return toggle;
        }

        /** Formats the line-change statistics as "+N −M" (omitting zero values). */
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
