package com.example.jylos.ui.controller;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The search shortcut and the interface zoom steps. Where the panels go is
 * {@link UiLayout}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.3.0
 */
class NavigationCommand {

    private Function<String, String> i18nFn;
    private Consumer<String> updateStatus;

    void wire(Function<String, String> i18n, Consumer<String> updateStatus) {
        this.i18nFn = i18n;
        this.updateStatus = updateStatus;
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
}
