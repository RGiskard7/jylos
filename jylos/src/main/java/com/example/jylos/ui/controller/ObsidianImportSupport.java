package com.example.jylos.ui.controller;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * What Jylos takes from Obsidian for a vault both open, read from {@code .obsidian} and never
 * written: its bookmarks as favorites ({@link ObsidianBookmarksSupport}) and its settings as
 * Jylos' own ({@link ObsidianSettingsSupport}), the latter offered once when the vault is opened.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class ObsidianImportSupport {

    private final ObsidianBookmarksSupport bookmarks = new ObsidianBookmarksSupport();
    private final ObsidianSettingsSupport settings = new ObsidianSettingsSupport();

    /** Answers both commands and, once the window is up, offers the open vault's settings. */
    void register(Map<ActionType, Runnable> handlers, Supplier<NoteService> notes, Supplier<VaultSettingsStore> vault,
            Function<String, String> i18n, Consumer<String> status, Runnable refresh, Supplier<Window> owner) {
        bookmarks.register(handlers, notes, i18n, status, refresh, owner);
        settings.register(handlers, notes, vault, i18n, status, owner);
        PauseTransition afterOpening = new PauseTransition(Duration.seconds(2));
        // A dialog cannot wait inside an animation's end: the offer runs on the next pulse.
        afterOpening.setOnFinished(e -> Platform.runLater(settings::offer));
        afterOpening.play();
    }
}
