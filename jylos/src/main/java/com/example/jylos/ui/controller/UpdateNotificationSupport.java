package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.config.VersionConfig;
import com.example.jylos.service.UpdateChecker;
import com.example.jylos.ui.UiNodes;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Tells the user a newer Jylos is out: checks GitHub Releases at start-up and on demand, shows
 * a toast in the bottom-right corner of the window (with "install" and "download" links) and
 * keeps a status-bar indicator that brings the toast back. Installing is
 * {@link UpdateInstallSupport}'s job.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class UpdateNotificationSupport {

    private static final Duration TOAST_SHOWN_FOR = Duration.seconds(12);

    private final Supplier<CompletableFuture<UpdateChecker.CheckResult>> check;
    private final ExecutorService executor;

    private StackPane centerStack;
    private Label indicator;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> { };
    private Consumer<String> openUrl = url -> { };
    private Consumer<UpdateChecker.ReleaseInfo> install = release -> { };

    private UpdateChecker.ReleaseInfo available;
    private Node toast;

    /** Checks the running version against GitHub Releases. */
    UpdateNotificationSupport() {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jylos-update-checker");
            t.setDaemon(true);
            return t;
        }));
    }

    private UpdateNotificationSupport(ExecutorService executor) {
        this(() -> new UpdateChecker().checkLatestAsync(VersionConfig.getVersion(), executor), executor);
    }

    /** With {@code check} answering whether a newer release is out (tests). */
    UpdateNotificationSupport(Supplier<CompletableFuture<UpdateChecker.CheckResult>> check, ExecutorService executor) {
        this.check = check;
        this.executor = executor;
    }

    /**
     * @param centerStack where the toast is shown
     * @param indicator   the status-bar label shown while an update is available, or {@code null}
     * @param install     installs a release in the app
     */
    void wire(StackPane centerStack, Label indicator, Function<String, String> i18n, Consumer<String> status,
            Consumer<String> openUrl, Consumer<UpdateChecker.ReleaseInfo> install) {
        this.centerStack = centerStack;
        this.indicator = indicator;
        this.i18n = i18n != null ? i18n : key -> key;
        this.status = status != null ? status : message -> { };
        this.openUrl = openUrl != null ? openUrl : url -> { };
        this.install = install != null ? install : release -> { };
    }

    /** Checks quietly: only a newer release is shown. */
    void checkAtStartup() {
        check.get().thenAccept(result -> result.release().ifPresent(release -> Platform.runLater(() -> show(release))));
    }

    /** Checks and always says the outcome in the status bar (or shows the release). */
    void checkManually() {
        status.accept(i18n.apply("update.checking"));
        check.get().thenAccept(result -> Platform.runLater(() -> {
            if (result.failed()) {
                status.accept(i18n.apply("update.failed"));
            } else if (result.release().isPresent()) {
                show(result.release().get());
            } else {
                status.accept(i18n.apply("update.none"));
            }
        }));
    }

    /** The status-bar indicator was clicked: shows the toast of the release found again. */
    void onIndicatorClicked() {
        if (available != null) {
            show(available);
        }
    }

    void show(UpdateChecker.ReleaseInfo release) {
        if (centerStack == null || release == null) {
            return;
        }
        available = release;
        showIndicator(release);
        if (toast != null) {
            centerStack.getChildren().remove(toast);
        }

        HBox shown = new HBox(12);
        shown.getStyleClass().add("update-toast");
        shown.setAlignment(Pos.CENTER_LEFT);
        shown.setMaxWidth(420);
        shown.setMaxHeight(Region.USE_PREF_SIZE);
        shown.setMinHeight(Region.USE_PREF_SIZE);
        shown.setPickOnBounds(false);

        Label icon = new Label("!");
        icon.getStyleClass().add("update-toast-icon");

        VBox content = new VBox(4);
        content.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(MessageFormat.format(i18n.apply("update.available.title"), release.tagName()));
        title.getStyleClass().add("update-toast-title");
        Label message = new Label(i18n.apply("update.available.message"));
        message.getStyleClass().add("update-toast-message");

        HBox links = new HBox(12);
        links.setAlignment(Pos.CENTER_LEFT);

        // REMOVABLE: in-app updater (see docs/PACKAGING.md, "In-app updater (unsigned
        // builds)"). To revert to the old behavior, delete this Hyperlink and its
        // "links" HBox wrapper, and go back to adding the plain "link" below directly
        // to content.getChildren() as the toast's only action.
        Hyperlink installLink = new Hyperlink(i18n.apply("update.install.button"));
        installLink.getStyleClass().add("update-toast-link");
        installLink.setOnAction(event -> {
            hide(shown);
            install.accept(release);
        });

        Hyperlink link = new Hyperlink(i18n.apply("update.available.download"));
        link.getStyleClass().add("update-toast-link");
        link.setOnAction(event -> openUrl.accept(release.htmlUrl()));

        links.getChildren().addAll(installLink, link);
        content.getChildren().addAll(title, message, links);
        HBox.setHgrow(content, Priority.ALWAYS);

        Button closeButton = UiNodes.keepWhole(new Button("×"));
        closeButton.getStyleClass().addAll("toolbar-btn", "update-toast-close");
        closeButton.setOnAction(event -> hide(shown));

        shown.getChildren().addAll(icon, content, closeButton);
        StackPane.setAlignment(shown, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(shown, new Insets(0, 18, 18, 0));
        centerStack.getChildren().add(shown);
        toast = shown;

        PauseTransition autoHide = new PauseTransition(TOAST_SHOWN_FOR);
        autoHide.setOnFinished(event -> hide(shown));
        autoHide.play();
    }

    private void showIndicator(UpdateChecker.ReleaseInfo release) {
        if (indicator == null) {
            return;
        }
        UiNodes.show(indicator, true);
        indicator.setTooltip(new Tooltip(MessageFormat.format(
                i18n.apply("update.indicator.tooltip"), release.tagName())));
    }

    private void hide(Node shown) {
        if (centerStack != null && shown != null) {
            centerStack.getChildren().remove(shown);
        }
        if (toast == shown) {
            toast = null;
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
