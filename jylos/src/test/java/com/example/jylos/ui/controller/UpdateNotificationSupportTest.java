package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.service.UpdateChecker;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * A newer release is announced in a toast and a status-bar indicator; checking by hand always
 * says the outcome. The toast's links install or download, its × closes it, and the
 * indicator brings it back.
 */
class UpdateNotificationSupportTest {

    private static final UpdateChecker.ReleaseInfo RELEASE =
            new UpdateChecker.ReleaseInfo("v9.9.9", "https://example.org/v9.9.9", List.of(), "");

    @Test
    void aManualCheckSaysTheOutcome() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        for (UpdateChecker.CheckResult result : List.of(
                new UpdateChecker.CheckResult(Optional.empty(), false),
                new UpdateChecker.CheckResult(Optional.empty(), true))) {
            List<String> status = new ArrayList<>();
            UpdateNotificationSupport updates = support(result);
            onFx(() -> {
                updates.wire(new StackPane(), new Label(), key -> key, status::add, url -> { }, release -> { });
                updates.checkManually();
                return null;
            });
            waitForFx();
            assertEquals(List.of("update.checking", result.failed() ? "update.failed" : "update.none"), status);
        }
    }

    @Test
    void aNewerReleaseIsShownAndItsLinksAct() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        StackPane stack = onFx(StackPane::new);
        Label indicator = onFx(Label::new);
        List<String> done = new ArrayList<>();
        UpdateNotificationSupport updates = support(new UpdateChecker.CheckResult(Optional.of(RELEASE), false));
        onFx(() -> {
            indicator.setVisible(false);
            updates.wire(stack, indicator, key -> key + " {0}", done::add, url -> done.add("open " + url),
                    release -> done.add("install " + release.tagName()));
            updates.checkAtStartup();
            return null;
        });
        waitForFx();

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            out.add("toasts " + toasts(stack).size() + ", title " + ((Label) toasts(stack).get(0)
                    .lookup(".update-toast-title")).getText());
            out.add("indicator " + indicator.isVisible() + ", " + indicator.getTooltip().getText());
            ((Button) toasts(stack).get(0).lookup(".update-toast-close")).fire();
            out.add("after ×: toasts " + toasts(stack).size());
            updates.onIndicatorClicked();
            out.add("after the indicator: toasts " + toasts(stack).size());
            links(stack).get(1).fire();
            links(stack).get(0).fire();
            out.add("after install: toasts " + toasts(stack).size());
            return out;
        });

        assertEquals(List.of(
                "toasts 1, title update.available.title v9.9.9",
                "indicator true, update.indicator.tooltip v9.9.9",
                "after ×: toasts 0",
                "after the indicator: toasts 1",
                "after install: toasts 0"), seen);
        assertEquals(List.of("open https://example.org/v9.9.9", "install v9.9.9"), done);
    }

    private static UpdateNotificationSupport support(UpdateChecker.CheckResult result) {
        return new UpdateNotificationSupport(() -> CompletableFuture.completedFuture(result),
                Executors.newSingleThreadExecutor());
    }

    private static List<Node> toasts(StackPane stack) {
        return stack.getChildren().stream().filter(n -> n.getStyleClass().contains("update-toast")).toList();
    }

    private static List<Hyperlink> links(StackPane stack) {
        return toasts(stack).get(0).lookupAll(".update-toast-link").stream().map(n -> (Hyperlink) n).toList();
    }

    /** Lets the work queued with Platform.runLater run. */
    private static void waitForFx() throws Exception {
        TimeUnit.MILLISECONDS.sleep(100);
        onFx(() -> null);
    }
}
