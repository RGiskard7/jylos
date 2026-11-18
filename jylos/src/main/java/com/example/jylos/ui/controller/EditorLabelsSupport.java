package com.example.jylos.ui.controller;

import java.util.Map;
import java.util.function.BinaryOperator;

import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.ui.components.PageTextMenu;
import com.example.jylos.ui.web.HtmlView;

/**
 * The editor's own texts (its right-click menu and its search panel) and the reading view's
 * right-click menu, in the app's language.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class EditorLabelsSupport {

    private EditorLabelsSupport() {
    }

    /**
     * @param text a translation by key, with the English text when the key is missing
     */
    static void apply(MarkdownEditorView editor, HtmlView reading, BinaryOperator<String> text) {
        PageTextMenu.install(reading, text.apply("action.copy", "Copy"), text.apply("action.select_all", "Select All"));
        if (editor == null) {
            return;
        }
        editor.setEditorLabels(Map.ofEntries(
                Map.entry("undo", text.apply("action.undo", "Undo")),
                Map.entry("redo", text.apply("action.redo", "Redo")),
                Map.entry("cut", text.apply("action.cut", "Cut")),
                Map.entry("copy", text.apply("action.copy", "Copy")),
                Map.entry("paste", text.apply("action.paste", "Paste")),
                Map.entry("selectAll", text.apply("action.select_all", "Select All")),
                Map.entry("Find", text.apply("editor.search.find", "Find")),
                Map.entry("Replace", text.apply("editor.search.replace", "Replace")),
                Map.entry("next", text.apply("editor.search.next", "next")),
                Map.entry("previous", text.apply("editor.search.previous", "previous")),
                Map.entry("all", text.apply("editor.search.all", "all")),
                Map.entry("match case", text.apply("editor.search.match_case", "match case")),
                Map.entry("regexp", text.apply("editor.search.regexp", "regular expression")),
                Map.entry("by word", text.apply("editor.search.by_word", "whole word")),
                Map.entry("replace", text.apply("editor.search.replace_action", "replace")),
                Map.entry("replace all", text.apply("editor.search.replace_all", "replace all")),
                Map.entry("close", text.apply("action.close", "Close")),
                Map.entry("Go to line", text.apply("editor.search.go_to_line", "Go to line")),
                Map.entry("go", text.apply("editor.search.go", "go")),
                Map.entry("current match", text.apply("editor.search.current_match", "current match")),
                Map.entry("on line", text.apply("editor.search.on_line", "on line")),
                Map.entry("replaced $ matches", text.apply("editor.search.replaced_matches", "replaced $ matches")),
                Map.entry("replaced match on line $", text.apply("editor.search.replaced_match_on_line",
                        "replaced match on line $"))));
    }
}
