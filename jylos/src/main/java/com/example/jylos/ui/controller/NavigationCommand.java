package com.example.jylos.ui.controller;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.service.NoteService;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;

/**
 * Navigation, layout-toggle and refresh commands.
 *
 * <p>Receives i18n and status callbacks from the shell controller; node
 * references are passed in per-call by the controller that owns them.</p>
 */
class NavigationCommand {

    private static final Logger logger = LoggerConfig.getLogger(NavigationCommand.class);

    private Function<String, String> i18nFn;
    private Consumer<String> updateStatus;
    private NoteService noteService;

    void wire(Function<String, String> i18n, Consumer<String> updateStatus, NoteService noteService) {
        this.i18nFn = i18n;
        this.updateStatus = updateStatus;
        this.noteService = noteService;
    }

    private String i18n(String key) {
        return i18nFn.apply(key);
    }

    boolean handleSearch(ToolbarController toolbarController) {
        if (toolbarController == null || toolbarController.getSearchField() == null) {
            return false;
        }
        toolbarController.getSearchField().requestFocus();
        toolbarController.getSearchField().selectAll();
        updateStatus.accept(i18n("status.search_focused"));
        return true;
    }

    boolean toggleSidebar(boolean isStackedLayout, SplitPane navSplitPane, VBox sidebarPane,
            SplitPane mainSplitPane, ToolbarController toolbarController) {
        if (isStackedLayout) {
            if (navSplitPane == null) {
                return false;
            }
            boolean isCollapsed = navSplitPane.getMaxWidth() < 10;
            if (isCollapsed) {
                showStackedNavigation(navSplitPane, mainSplitPane);
                updateStatus.accept(i18n("status.nav_shown"));
                setSidebarToggle(toolbarController, true);
            } else {
                navSplitPane.setMinWidth(0);
                navSplitPane.setMaxWidth(0);
                navSplitPane.setPrefWidth(0);
                updateStatus.accept(i18n("status.nav_hidden"));
                setSidebarToggle(toolbarController, false);
            }
            return true;
        }

        if (sidebarPane == null) {
            return false;
        }
        boolean isCollapsed = sidebarPane.getMaxWidth() < 10;
        if (isCollapsed) {
            showSidebar(sidebarPane, mainSplitPane);
            updateStatus.accept(i18n("status.sidebar_shown"));
            setSidebarToggle(toolbarController, true);
        } else {
            sidebarPane.setMinWidth(0);
            sidebarPane.setMaxWidth(0);
            sidebarPane.setPrefWidth(0);
            updateStatus.accept(i18n("status.sidebar_hidden"));
            setSidebarToggle(toolbarController, false);
        }
        return true;
    }

    boolean toggleNotesPanel(boolean isStackedLayout, VBox notesPanel, SplitPane contentSplitPane,
            ToolbarController toolbarController, Runnable sidebarFallback) {
        if (isStackedLayout) {
            if (sidebarFallback != null) {
                sidebarFallback.run();
                return true;
            }
            return false;
        }

        if (notesPanel == null) {
            return false;
        }

        boolean isCollapsed = notesPanel.getMaxWidth() < 10;
        if (isCollapsed) {
            showNotesPanel(notesPanel, contentSplitPane);
            updateStatus.accept(i18n("status.notes_panel_shown"));
            setNotesToggle(toolbarController, true);
        } else {
            notesPanel.setMinWidth(0);
            notesPanel.setMaxWidth(0);
            notesPanel.setPrefWidth(0);
            updateStatus.accept(i18n("status.notes_panel_hidden"));
            setNotesToggle(toolbarController, false);
        }

        return true;
    }

    void showNavigationPanels(boolean isStackedLayout, SplitPane navSplitPane, VBox sidebarPane,
            SplitPane mainSplitPane, VBox notesPanel, SplitPane contentSplitPane,
            ToolbarController toolbarController) {
        if (isStackedLayout) {
            showStackedNavigation(navSplitPane, mainSplitPane);
            setSidebarToggle(toolbarController, true);
            setNotesToggle(toolbarController, true);
            return;
        }
        showSidebar(sidebarPane, mainSplitPane);
        showNotesPanel(notesPanel, contentSplitPane);
        setSidebarToggle(toolbarController, true);
        setNotesToggle(toolbarController, true);
    }

    private void showStackedNavigation(SplitPane navSplitPane, SplitPane mainSplitPane) {
        if (navSplitPane == null) {
            return;
        }
        navSplitPane.setMinWidth(200);
        navSplitPane.setMaxWidth(Double.MAX_VALUE);
        navSplitPane.setPrefWidth(300);
        if (mainSplitPane != null) {
            mainSplitPane.setDividerPositions(0.25);
        }
    }

    private void showSidebar(VBox sidebarPane, SplitPane mainSplitPane) {
        if (sidebarPane == null) {
            return;
        }
        sidebarPane.setMinWidth(200);
        sidebarPane.setMaxWidth(Double.MAX_VALUE);
        sidebarPane.setPrefWidth(250);
        if (mainSplitPane != null) {
            mainSplitPane.setDividerPositions(0.22);
        }
    }

    private void showNotesPanel(VBox notesPanel, SplitPane contentSplitPane) {
        if (notesPanel == null) {
            return;
        }
        notesPanel.setMinWidth(180);
        notesPanel.setMaxWidth(Double.MAX_VALUE);
        notesPanel.setPrefWidth(260);
        if (contentSplitPane != null) {
            contentSplitPane.setDividerPositions(0.25);
        }
    }

    double zoomIn(double currentUiFontSize) {
        return currentUiFontSize + 1.0;
    }

    double zoomOut(double currentUiFontSize) {
        if (currentUiFontSize > 8.0) {
            return currentUiFontSize - 1.0;
        }
        return currentUiFontSize;
    }

    double resetUiZoom() {
        return 13.0;
    }

    /**
     * Puts the sidebar, the notes panel and the editor where {@code mode} wants them:
     * <ul>
     * <li>classic columns: sidebar | notes | editor;</li>
     * <li>classic stacked: (sidebar above notes) | editor;</li>
     * <li>Explorer: sidebar | editor, with the notes panel before the editor only while it
     * shows results ({@code resultsShown}).</li>
     * </ul>
     * Panels collapsed with their toggle are shown again, since the toggle state does not
     * carry over to a different arrangement.
     */
    void arrangeLayout(com.example.jylos.ui.preferences.LayoutMode mode, boolean resultsShown, SplitPane mainSplitPane,
            SplitPane contentSplitPane, SplitPane navSplitPane, Node sidebarPane, Node notesPanel, Node editorArea) {
        if (mainSplitPane == null || contentSplitPane == null || navSplitPane == null || sidebarPane == null
                || notesPanel == null || editorArea == null) {
            return;
        }
        mainSplitPane.getItems().clear();
        contentSplitPane.getItems().clear();
        navSplitPane.getItems().clear();
        expand(sidebarPane, 200);
        expand(notesPanel, 180);

        switch (mode) {
            case CLASSIC_STACKED -> {
                navSplitPane.getItems().addAll(sidebarPane, notesPanel);
                navSplitPane.setDividerPositions(0.5);
                mainSplitPane.getItems().addAll(navSplitPane, editorArea);
                mainSplitPane.setDividerPositions(0.25);
            }
            case EXPLORER -> {
                if (resultsShown) {
                    contentSplitPane.getItems().addAll(notesPanel, editorArea);
                    contentSplitPane.setDividerPositions(0.3);
                } else {
                    contentSplitPane.getItems().add(editorArea);
                }
                mainSplitPane.getItems().addAll(sidebarPane, contentSplitPane);
                mainSplitPane.setDividerPositions(0.22);
            }
            default -> {
                contentSplitPane.getItems().addAll(notesPanel, editorArea);
                contentSplitPane.setDividerPositions(0.3);
                mainSplitPane.getItems().addAll(sidebarPane, contentSplitPane);
                mainSplitPane.setDividerPositions(0.22);
            }
        }
    }

    private static void expand(Node pane, double minWidth) {
        if (pane instanceof javafx.scene.layout.Region region) {
            region.setMinWidth(minWidth);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private void setSidebarToggle(ToolbarController toolbarController, boolean selected) {
        if (toolbarController != null && toolbarController.getSidebarToggleBtn() != null) {
            toolbarController.getSidebarToggleBtn().setSelected(selected);
        }
    }

    private void setNotesToggle(ToolbarController toolbarController, boolean selected) {
        if (toolbarController != null && toolbarController.getNotesPanelToggleBtn() != null) {
            toolbarController.getNotesPanelToggleBtn().setSelected(selected);
        }
    }
}
