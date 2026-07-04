package com.example.jylos.ui.controller;

import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.ui.components.CommandPalette;
import com.example.jylos.ui.components.QuickSwitcher;

import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * Command palette / quick switcher creation and global keyboard shortcuts.
 */
class CommandUI {

    private static final Logger logger = LoggerConfig.getLogger(CommandUI.class);

    record CommandUiComponents(CommandPalette commandPalette, QuickSwitcher quickSwitcher) {
    }

    CommandUiComponents ensureCommandUiComponents(
            Stage stage,
            CommandPalette existingPalette,
            QuickSwitcher existingSwitcher,
            Consumer<String> commandHandler,
            Consumer<Note> noteSelectionHandler,
            ResourceBundle bundle) {
        if (stage == null) {
            return new CommandUiComponents(existingPalette, existingSwitcher);
        }

        CommandPalette palette = existingPalette;
        if (palette == null) {
            palette = new CommandPalette(stage);
            if (commandHandler != null) {
                palette.setCommandHandler(commandHandler);
            }
        }
        palette.setBundle(bundle);

        QuickSwitcher switcher = existingSwitcher;
        if (switcher == null) {
            switcher = new QuickSwitcher(stage);
            if (noteSelectionHandler != null) {
                switcher.setOnNoteSelected(noteSelectionHandler);
            }
        }
        switcher.setBundle(bundle);

        return new CommandUiComponents(palette, switcher);
    }

    void initializeKeyboardShortcuts(Scene scene, Runnable openPaletteAction, Runnable openQuickSwitcherAction) {
        if (scene == null) {
            logger.warning("Scene not available for keyboard shortcuts");
            return;
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case P:
                        if (openPaletteAction != null) {
                            openPaletteAction.run();
                            event.consume();
                        }
                        break;
                    case O:
                        if (openQuickSwitcherAction != null) {
                            openQuickSwitcherAction.run();
                            event.consume();
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        logger.info("Keyboard shortcuts initialized (Ctrl+P: Command Palette, Ctrl+O: Quick Switcher)");
    }
}
