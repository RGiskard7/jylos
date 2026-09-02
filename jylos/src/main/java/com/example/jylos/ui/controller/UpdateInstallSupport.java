package com.example.jylos.ui.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.service.UpdateChecker;
import com.example.jylos.service.UpdateInstaller;
import com.example.jylos.ui.UiDialogs;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;

/**
 * REMOVABLE: in-app updater — see {@link UpdateInstaller}'s class docs ("Removing
 * this later") for the full removal checklist and why this exists in the first
 * place (Jylos releases are not code-signed). This class does not repeat that here,
 * it only implements the confirmation dialogs and error handling around it.
 *
 * <p>UI flow: confirms with the user, downloads the release asset for this platform
 * in the background, verifies it against GitHub's own checksum, and — only once the
 * user confirms again — closes Jylos and hands off to the native installer. The
 * download/verification mechanics live in {@link UpdateInstaller}; this class
 * follows the standard feature-support pattern ({@code wire(...)} then call),
 * matching {@link ImportSupport}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 */
final class UpdateInstallSupport {

    private static final Logger logger = LoggerConfig.getLogger(UpdateInstallSupport.class);

    private final UpdateInstaller installer = new UpdateInstaller();

    private Function<String, String> i18n;
    private Consumer<String> status;
    private Consumer<String> openBrowser;
    /** Runs the app's normal close sequence (unsaved-note prompt, plugin/DB shutdown); returns whether it went ahead. */
    private Supplier<Boolean> requestApplicationClose;
    private Runnable exitApplication;

    void wire(Function<String, String> i18n, Consumer<String> status, Consumer<String> openBrowser,
            Supplier<Boolean> requestApplicationClose, Runnable exitApplication) {
        this.i18n = i18n;
        this.status = status;
        this.openBrowser = openBrowser;
        this.requestApplicationClose = requestApplicationClose;
        this.exitApplication = exitApplication;
    }

    /** Entry point: called when the user clicks "Install now" on the update toast/indicator. */
    void installUpdate(UpdateChecker.ReleaseInfo release) {
        if (release == null) {
            return;
        }
        Optional<UpdateChecker.AssetInfo> assetOpt = UpdateInstaller.pickAssetForCurrentPlatform(release.assets());
        if (assetOpt.isEmpty()) {
            // Most commonly: macOS today (CI does not publish a macOS asset yet), or a
            // Linux distro this app doesn't recognize as Debian/RHEL-family. Either way,
            // there is nothing to auto-install — fall back to the normal browser download.
            showInfo(getString("update.install.no_asset.title"),
                    MessageFormat.format(getString("update.install.no_asset.content"), release.tagName()));
            if (openBrowser != null) {
                openBrowser.accept(release.htmlUrl());
            }
            return;
        }
        UpdateChecker.AssetInfo asset = assetOpt.get();
        if (!confirmDownload(release.tagName(), asset)) {
            return;
        }
        runDownloadAndVerify(release, asset);
    }

    private boolean confirmDownload(String tagName, UpdateChecker.AssetInfo asset) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(getString("update.install.confirm.title"));
        alert.setHeaderText(MessageFormat.format(getString("update.install.confirm.header"), tagName));
        alert.setContentText(MessageFormat.format(getString("update.install.confirm.content"), asset.name()));
        ButtonType downloadButton = new ButtonType(getString("update.install.confirm.button"));
        ButtonType cancelButton = new ButtonType(getString("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(downloadButton, cancelButton);
        UiDialogs.apply(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == downloadButton;
    }

    private void runDownloadAndVerify(UpdateChecker.ReleaseInfo release, UpdateChecker.AssetInfo asset) {
        Path destination = UpdateInstaller.stagingPath(asset.name());

        Alert progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle(getString("update.install.confirm.title"));
        progressDialog.setHeaderText(MessageFormat.format(getString("update.install.downloading"), asset.name()));
        ProgressBar progressBar = new ProgressBar(asset.size() > 0 ? 0 : ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(320);
        progressDialog.getDialogPane().setContent(progressBar);
        ButtonType cancelButton = new ButtonType(getString("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getButtonTypes().setAll(cancelButton);
        UiDialogs.apply(progressDialog.getDialogPane());

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                installer.downloadWithProgress(URI.create(asset.browserDownloadUrl()), destination, downloaded -> {
                    if (asset.size() > 0) {
                        updateProgress(downloaded, asset.size());
                    }
                });
                if (isCancelled()) {
                    return null;
                }
                javafx.application.Platform.runLater(() -> progressDialog.setHeaderText(getString("update.install.verifying")));
                if (!UpdateInstaller.verifyDigest(destination, asset.digest())) {
                    Files.deleteIfExists(destination);
                    throw new java.security.GeneralSecurityException("Downloaded update failed checksum verification");
                }
                return destination;
            }
        };
        if (asset.size() > 0) {
            progressBar.progressProperty().bind(task.progressProperty());
        }

        Button cancelBtn = (Button) progressDialog.getDialogPane().lookupButton(cancelButton);
        cancelBtn.addEventFilter(ActionEvent.ACTION, evt -> task.cancel());

        task.setOnSucceeded(e -> {
            progressDialog.close();
            Path downloaded = task.getValue();
            if (downloaded != null) {
                // Deferred to the next pulse (not called directly here): this callback
                // runs while progressDialog's own showAndWait() nested event loop is
                // still unwinding, and opening another showAndWait() modal in the same
                // callback that closes the previous one is a known JavaFX glitch — the
                // new dialog renders as a blank white window (nothing laid out yet).
                // Platform.runLater lets that loop finish exiting first.
                javafx.application.Platform.runLater(() -> offerToInstall(release.tagName(), downloaded));
            }
        });
        task.setOnCancelled(e -> progressDialog.close());
        task.setOnFailed(e -> {
            progressDialog.close();
            Throwable ex = task.getException();
            logger.log(Level.WARNING, "Update download/verification failed", ex);
            // Same deferral as setOnSucceeded above, same reason: showError() opens
            // another modal Alert right after this one closes.
            javafx.application.Platform.runLater(() -> {
                if (ex instanceof java.security.GeneralSecurityException) {
                    showError(getString("update.install.verify_failed.title"), getString("update.install.verify_failed.content"));
                } else {
                    showError(getString("update.install.download_failed.title"), getString("update.install.download_failed.content"));
                }
            });
        });

        Thread thread = new Thread(task, "jylos-update-download");
        thread.setDaemon(true);
        thread.start();
        // Blocks this (FX) call, but showAndWait() runs a nested event loop that still
        // pumps Platform.runLater — the task's own onSucceeded/onFailed/onCancelled
        // (dispatched that way internally) fire normally and close this dialog.
        UiDialogs.show(progressDialog);
    }

    private void offerToInstall(String tagName, Path installerFile) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(getString("update.install.ready.title"));
        alert.setHeaderText(MessageFormat.format(getString("update.install.ready.header"), tagName));
        alert.setContentText(getString("update.install.ready.content"));
        ButtonType installButton = new ButtonType(getString("update.install.ready.button"));
        ButtonType cancelButton = new ButtonType(getString("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(installButton, cancelButton);
        UiDialogs.apply(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != installButton) {
            updateStatus(MessageFormat.format(getString("update.install.postponed"), tagName));
            return;
        }

        // Close (and fully shut down) Jylos BEFORE launching the installer, not after —
        // on Windows in particular, an installer trying to replace files a still-running
        // Jylos process holds open would fail. If the user backs out here (e.g. an
        // unsaved-note prompt they cancel), the installer is never started at all.
        if (requestApplicationClose == null || !Boolean.TRUE.equals(requestApplicationClose.get())) {
            return;
        }
        try {
            installer.launch(installerFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not launch the downloaded installer", e);
            showError(getString("update.install.launch_failed.title"),
                    MessageFormat.format(getString("update.install.launch_failed.content"),
                            installerFile.toAbsolutePath()));
            return;
        }
        if (exitApplication != null) {
            exitApplication.run();
        }
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        UiDialogs.show(alert);
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        UiDialogs.show(alert);
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
