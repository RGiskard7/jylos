package com.example.jylos.ui.controller;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.service.RichLinkService;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.ui.components.MarkdownEditorView;
import com.example.jylos.util.RichLinks;

import javafx.concurrent.Task;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;

/**
 * The editor's formatting commands: bold, headings, lists, code blocks, and the link, rich
 * link and image dialogs. Each one edits the source at the cursor or selection, runs the
 * plugins' insert hooks over what a dialog inserts, and tells the editor it was edited.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
class EditorFormattingSupport {

    private final Supplier<MarkdownEditorView> area;
    private final UnaryOperator<String> insertHooks;
    private final Runnable afterEdit;
    private final BinaryOperator<String> i18n;
    private final RichLinkService richLinkService = new RichLinkService();

    /** Cuts the selection of {@code title} when it has the focus, else of the editor. */
    void cut(TextInputControl title) {
        if (title != null && title.isFocused()) {
            title.cut();
        } else if (area.get() != null) {
            area.get().cut();
        }
    }

    /** Copies the selection of {@code title} when it has the focus, else of the editor. */
    void copy(TextInputControl title) {
        if (title != null && title.isFocused()) {
            title.copy();
        } else if (area.get() != null) {
            area.get().copy();
        }
    }

    /** Pastes in {@code title} when it has the focus, else in the editor. */
    void paste(TextInputControl title) {
        if (title != null && title.isFocused()) {
            title.paste();
        } else if (area.get() != null) {
            area.get().paste();
        }
    }

    /** Opens the editor's search bar, with its replace field when {@code replace}. */
    void openSearch(boolean replace) {
        MarkdownEditorView editor = area.get();
        if (editor != null && replace) {
            editor.openReplace();
        } else if (editor != null) {
            editor.openSearch();
        }
    }

    /**
     * @param area        the source editor (read on each command; it is injected after construction)
     * @param insertHooks the plugins' insert hooks, applied to what a dialog inserts
     * @param afterEdit   runs after each edit, so the editor re-checks its unsaved state
     * @param i18n        a translation by key, with the fallback text
     */
    EditorFormattingSupport(Supplier<MarkdownEditorView> area, UnaryOperator<String> insertHooks, Runnable afterEdit,
            BinaryOperator<String> i18n) {
        this.area = area;
        this.insertHooks = insertHooks;
        this.afterEdit = afterEdit;
        this.i18n = i18n;
    }

    private MarkdownEditorView area() {
        return area.get();
    }

    /**
     * Applies a formatting action (bold, a heading, a list, a link…) at the cursor or
     * selection. Other action types are ignored.
     *
     * @param type the formatting action
     */
    void apply(SystemActionEvent.ActionType type) {
        if (type == null) {
            return;
        }
        switch (type) {
            case BOLD      -> insertMarkdownFormat("**", "**");
            case ITALIC    -> insertMarkdownFormat("*", "*");
            case UNDERLINE -> insertMarkdownFormat("<u>", "</u>");
            case STRIKE    -> insertMarkdownFormat("~~", "~~");
            case HIGHLIGHT -> insertMarkdownFormat("==", "==");
            case HEADING1  -> insertLinePrefix("# ");
            case HEADING2  -> insertLinePrefix("## ");
            case HEADING3  -> insertLinePrefix("### ");
            case BULLET_LIST   -> insertLinePrefix("- ");
            case NUMBERED_LIST -> insertLinePrefix("1. ");
            case TODO_LIST -> insertTodoList();
            case QUOTE     -> insertLinePrefix("> ");
            case CODE      -> insertCodeBlock();
            case LINK      -> handleLinkDialog();
            case RICH_LINK -> handleRichLinkDialog();
            case IMAGE     -> handleImageDialog();
            default        -> { /* not a formatting action */ }
        }
    }

    private void insertMarkdownFormat(String prefix, String suffix) {
        MarkdownEditorView area = area();
        if (area == null) return;
        String sel = area.getSelectedText();
        if (sel != null && !sel.isEmpty()) {
            area.replaceSelection(prefix + sel + suffix);
        } else {
            area.replaceSelection(prefix + suffix, prefix.length());
        }
        area.requestFocus();
        afterEdit.run();
    }

    private void insertLinePrefix(String prefix) {
        MarkdownEditorView area = area();
        if (area == null) return;
        int pos = area.getCaretPosition();
        String t = Objects.toString(area.getText(), "");
        int lineStart = t.lastIndexOf('\n', pos - 1) + 1;
        if (t.substring(lineStart, pos).trim().isEmpty() && lineStart == pos) {
            area.replaceRange(pos, pos, prefix, pos + prefix.length());
        } else {
            String insertion = "\n" + prefix;
            area.replaceRange(pos, pos, insertion, pos + insertion.length());
        }
        area.requestFocus();
        afterEdit.run();
    }

    private void insertTodoList() {
        MarkdownEditorView area = area();
        if (area == null) return;
        int pos = area.getCaretPosition();
        String t = Objects.toString(area.getText(), "");
        String item = insertHooks.apply("- [ ] ");
        int lineStart = t.lastIndexOf('\n', pos - 1) + 1;
        if (t.substring(lineStart, pos).trim().isEmpty()) {
            area.replaceRange(pos, pos, item, pos + item.length());
        } else {
            String insertion = "\n" + item;
            area.replaceRange(pos, pos, insertion, pos + insertion.length());
        }
        area.requestFocus();
        afterEdit.run();
    }

    // Always inserts a fenced code block, matching the toolbar tooltip
    // ("Code block") — inline code has its own affordance via backticks typed
    // directly, so this button no longer silently falls back to inline code
    // for a single-line or empty selection.
    private void insertCodeBlock() {
        MarkdownEditorView area = area();
        if (area == null) return;
        String sel = area.getSelectedText();
        if (sel != null && !sel.isEmpty()) {
            area.replaceSelection("```\n" + sel + "\n```");
        } else {
            String insertion = "```\n\n```";
            area.replaceSelection(insertion, 4);
        }
        area.requestFocus();
        afterEdit.run();
    }

    private void handleLinkDialog() {
        MarkdownEditorView area = area();
        if (area == null) return;
        TextInputDialog d = new TextInputDialog(i18n.apply("dialog.link.default_url", "https://"));
        d.setTitle(i18n.apply("dialog.link.title", "Insert Link"));
        d.setHeaderText(i18n.apply("dialog.link.header", "Enter URL:"));
        d.setContentText(i18n.apply("dialog.link.content", "URL:"));
        UiDialogs.show(d).filter(s -> !s.trim().isEmpty()).ifPresent(url -> {
            String sel = area.getSelectedText();
            String label = (sel != null && !sel.isEmpty()) ? sel
                    : i18n.apply("dialog.link.default_text", "link text");
            String link = insertHooks.apply("[" + label + "](" + url.trim() + ")");
            if (sel != null && !sel.isEmpty()) area.replaceSelection(link);
            else {
                int pos = area.getCaretPosition();
                area.replaceRange(pos, pos, link, pos + link.length());
            }
            area.requestFocus();
            afterEdit.run();
        });
    }

    /**
     * Prompts for a URL, fetches its metadata off the FX thread, and inserts a
     * {@code ::: rich-link} block at the caret. Fetching never blocks the UI; on any
     * failure the service returns a minimal card (URL + host) so a block is still
     * inserted.
     */
    private void handleRichLinkDialog() {
        MarkdownEditorView area = area();
        if (area == null) return;
        TextInputDialog d = new TextInputDialog("https://");
        d.setTitle(i18n.apply("dialog.rich_link.title", "Insert rich link"));
        d.setHeaderText(i18n.apply("dialog.rich_link.header", "Paste a URL:"));
        d.setContentText(i18n.apply("dialog.rich_link.content", "URL:"));
        UiDialogs.show(d)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .ifPresent(this::fetchAndInsertRichLink);
    }

    private void fetchAndInsertRichLink(String url) {
        Task<RichLinks.RichLink> task = new Task<>() {
            @Override
            protected RichLinks.RichLink call() {
                return richLinkService.fetch(url);
            }
        };
        task.setOnSucceeded(e -> insertRichLinkBlock(task.getValue()));
        task.setOnFailed(e -> insertRichLinkBlock(
                new RichLinks.RichLink(url, "", "", "", RichLinks.hostOf(url))));
        Thread thread = new Thread(task, "rich-link-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    /** Inserts the rich-link block on its own paragraph at the caret. */
    private void insertRichLinkBlock(RichLinks.RichLink link) {
        MarkdownEditorView area = area();
        if (area == null || link == null) return;
        String block = RichLinks.toMarkdown(link);
        int pos = area.getCaretPosition();
        String text = Objects.toString(area.getText(), "");
        String before = text.substring(0, pos);
        // Keep the block as its own paragraph (CommonMark needs a blank line around it).
        String lead = before.isEmpty() || before.endsWith("\n\n") ? "" : before.endsWith("\n") ? "\n" : "\n\n";
        String insertion = lead + block + "\n";
        area.replaceRange(pos, pos, insertion, pos + insertion.length());
        area.requestFocus();
        afterEdit.run();
    }

    private void handleImageDialog() {
        MarkdownEditorView area = area();
        if (area == null) return;
        TextInputDialog d = new TextInputDialog("");
        d.setTitle(i18n.apply("dialog.image.title", "Insert Image"));
        d.setHeaderText(i18n.apply("dialog.image.header", "Enter image URL or path:"));
        d.setContentText(i18n.apply("dialog.image.content", "Image:"));
        UiDialogs.show(d).filter(s -> !s.trim().isEmpty()).ifPresent(path -> {
            String sel = area.getSelectedText();
            String alt = (sel != null && !sel.isEmpty()) ? sel
                    : i18n.apply("dialog.image.default_alt", "image");
            String img = insertHooks.apply("![" + alt + "](" + path.trim() + ")");
            if (sel != null && !sel.isEmpty()) area.replaceSelection(img);
            else {
                int pos = area.getCaretPosition();
                area.replaceRange(pos, pos, img, pos + img.length());
            }
            area.requestFocus();
            afterEdit.run();
        });
    }
}
