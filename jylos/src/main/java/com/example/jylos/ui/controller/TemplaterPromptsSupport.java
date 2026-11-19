package com.example.jylos.ui.controller;

import java.util.List;
import java.util.Optional;

import com.example.jylos.ui.UiDialogs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;

/**
 * The questions a Templater template asks ({@code tp.system.prompt} and
 * {@code tp.system.suggester}), in Jylos' dialogs: empty when the user cancels.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class TemplaterPromptsSupport {

    private static final String TITLE = "Templater";

    private TemplaterPromptsSupport() {
    }

    /** What the user writes for {@code question}, {@code defaultValue} written to start with. */
    static Optional<String> prompt(String question, String defaultValue, boolean multiline) {
        if (!multiline) {
            TextInputDialog dialog = new TextInputDialog(defaultValue != null ? defaultValue : "");
            dialog.setTitle(TITLE);
            dialog.setHeaderText(question);
            UiDialogs.apply(dialog);
            return dialog.showAndWait();
        }
        TextArea area = new TextArea(defaultValue != null ? defaultValue : "");
        area.setWrapText(true);
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setHeaderText(question);
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? area.getText() : null);
        UiDialogs.apply(dialog);
        return dialog.showAndWait();
    }

    /** The item the user picks by its text in {@code texts}. */
    static Optional<Object> suggest(List<String> texts, List<Object> items, String placeholder) {
        if (texts.isEmpty()) {
            return Optional.empty();
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(texts.getFirst(), texts);
        dialog.setTitle(TITLE);
        dialog.setHeaderText(placeholder != null && !placeholder.isBlank() ? placeholder : null);
        UiDialogs.apply(dialog);
        return dialog.showAndWait().map(texts::indexOf).filter(i -> i >= 0 && i < items.size()).map(items::get);
    }
}
