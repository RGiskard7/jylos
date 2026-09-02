package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import com.example.jylos.ui.preferences.UiPreferencesStore;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;

/**
 * Focus / writing mode: hides everything but the editor (sidebar, notes list, right
 * panel, toolbar and status bar) and restores the previous layout on exit.
 *
 * <p>The sidebar and notes-list panes are removed from their SplitPanes (rather than
 * just hidden) so the editor truly fills the width; the exact item lists are
 * snapshotted and restored, which also respects panels the user had already collapsed
 * before entering focus mode. Owns the focus state, extracted from
 * {@code MainController}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class FocusModeSupport {

    private SplitPane mainSplitPane;
    private SplitPane contentSplitPane;
    private Supplier<Node> toolbar;
    private Node statusBar;
    private Region rightPanel;
    private Supplier<Node> editorContainer;
    private Preferences prefs;
    private Function<String, String> i18n;
    private Consumer<String> status;

    private boolean focusMode;
    private boolean savedRightVisible;
    private boolean savedToolbarVisible;
    private boolean savedStatusVisible;
    private List<Node> savedMainItems;
    private List<Node> savedContentItems;

    void wire(SplitPane mainSplitPane, SplitPane contentSplitPane, Supplier<Node> toolbar,
            Node statusBar, Region rightPanel, Supplier<Node> editorContainer, Preferences prefs,
            Function<String, String> i18n, Consumer<String> status) {
        this.mainSplitPane = mainSplitPane;
        this.contentSplitPane = contentSplitPane;
        this.toolbar = toolbar;
        this.statusBar = statusBar;
        this.rightPanel = rightPanel;
        this.editorContainer = editorContainer;
        this.prefs = prefs;
        this.i18n = i18n;
        this.status = status;
    }

    boolean isActive() {
        return focusMode;
    }

    void toggle() {
        if (!focusMode) {
            enter();
        } else {
            exit();
        }
    }

    private void enter() {
        Node tb = toolbar != null ? toolbar.get() : null;
        savedRightVisible = isRightPanelExpanded();
        savedToolbarVisible = tb != null && tb.isVisible();
        savedStatusVisible = statusBar != null && statusBar.isVisible();
        savedMainItems = mainSplitPane != null ? new ArrayList<>(mainSplitPane.getItems()) : null;
        savedContentItems = contentSplitPane != null ? new ArrayList<>(contentSplitPane.getItems()) : null;

        setShown(tb, false);
        setShown(statusBar, false);
        // The right panel lives inside a SplitPane (editor | right panel), not a plain
        // Box — a SplitPane still reserves that item's divided region even once it's
        // set invisible/unmanaged, since collapsing happens via divider position, not
        // the normal managed-layout pass. Collapsing it here has to use the exact same
        // technique the rest of the app already uses to hide THIS panel (squeeze its
        // width to 0, `UiLayout.toggleRightPanel`'s "collapsed" branch below) — that's
        // what actually empties its divided region, instead of just hiding content
        // inside a region that stays as wide as it was, showing as an empty gap.
        collapseRightPanel();
        if (mainSplitPane != null && contentSplitPane != null) {
            mainSplitPane.getItems().setAll(contentSplitPane);
        }
        Node editor = editorContainer != null ? editorContainer.get() : null;
        if (contentSplitPane != null && editor != null) {
            contentSplitPane.getItems().setAll(editor);
        }

        focusMode = true;
        updateStatus(getString("status.focus_on"));
    }

    private void exit() {
        if (mainSplitPane != null && savedMainItems != null) {
            mainSplitPane.getItems().setAll(savedMainItems);
        }
        if (contentSplitPane != null && savedContentItems != null) {
            contentSplitPane.getItems().setAll(savedContentItems);
        }
        // Restore divider proportions (setAll resets them) from the persisted values.
        if (mainSplitPane != null && !mainSplitPane.getDividers().isEmpty()) {
            mainSplitPane.setDividerPositions(
                    prefs.getDouble(UiPreferencesStore.SPLIT_MAIN_KEY, UiPreferencesStore.DEFAULT_SPLIT_MAIN));
        }
        if (contentSplitPane != null && !contentSplitPane.getDividers().isEmpty()) {
            contentSplitPane.setDividerPositions(
                    prefs.getDouble(UiPreferencesStore.SPLIT_CONTENT_KEY, UiPreferencesStore.DEFAULT_SPLIT_CONTENT));
        }
        setShown(toolbar != null ? toolbar.get() : null, savedToolbarVisible);
        setShown(statusBar, savedStatusVisible);
        if (savedRightVisible) {
            expandRightPanel();
        }
        // If it was already collapsed before entering focus mode, leave it collapsed —
        // enter() already squeezed it to 0 and nothing since has changed that.

        focusMode = false;
        updateStatus(getString("status.focus_off"));
    }

    private static void setShown(Node node, boolean shown) {
        if (node != null) {
            node.setVisible(shown);
            node.setManaged(shown);
        }
    }

    /** Same "collapsed" check {@code UiLayout.toggleRightPanel} itself uses. */
    private boolean isRightPanelExpanded() {
        return rightPanel != null && rightPanel.isManaged() && rightPanel.isVisible()
                && rightPanel.getPrefWidth() >= 10;
    }

    /** Matches {@code UiLayout.toggleRightPanel}'s collapsed branch exactly — visible/managed
     *  always stay true for this panel; only its width collapses to 0. */
    private void collapseRightPanel() {
        if (rightPanel != null) {
            rightPanel.setMinWidth(0);
            rightPanel.setMaxWidth(0);
            rightPanel.setPrefWidth(0);
        }
    }

    /** Matches {@code UiLayout.toggleRightPanel}'s expanded branch. */
    private void expandRightPanel() {
        if (rightPanel != null) {
            rightPanel.setMinWidth(240);
            rightPanel.setMaxWidth(Double.MAX_VALUE);
            rightPanel.setPrefWidth(300);
        }
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
