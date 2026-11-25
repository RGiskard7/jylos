package com.example.jylos.ui.components;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.util.FuzzySearchUtils;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;

/**
 * Suggestions in a list under a text field, as it is typed in: Jylos' counterpart of Obsidian's
 * {@code AbstractInputSuggest}, which its settings use to choose a file or a folder. What the
 * suggestions are is {@code getSuggestions(typed)}; at most {@link #limit} show. Up and down
 * move through them, Enter or Tab picks one (it is written in the field and given to
 * {@link #onSelect}), a click picks one, Escape closes the list (and only the list). The field keeps what is typed
 * when nothing is picked, so a path that is not among the suggestions can still be written.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.7.0
 */
public final class InputSuggest {

    /** As Obsidian's default. */
    public static final int DEFAULT_LIMIT = 100;
    private static final int VISIBLE_ROWS = 8;
    private static final double ROW_HEIGHT = 28;

    private final TextField field;
    private final Function<String, List<String>> getSuggestions;
    private final Popup popup = new Popup();
    private final ListView<String> list = new ListView<>();
    private int limit = DEFAULT_LIMIT;
    private Consumer<String> onSelect = value -> {
    };
    /** While a pick writes the field: that change is not new typing. */
    private boolean picking;

    public InputSuggest(TextField field, Function<String, List<String>> getSuggestions) {
        this.field = field;
        this.getSuggestions = getSuggestions;
        list.getStyleClass().add("autocomplete-list");
        list.setFocusTraversable(false);
        list.setCellFactory(view -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            // On press, before the field loses its focus to the list's window.
            cell.setOnMousePressed(e -> {
                if (!cell.isEmpty()) {
                    pick(cell.getItem());
                }
            });
            return cell;
        });
        popup.getContent().add(list);
        // A click elsewhere hides it; Escape too (hideOnEscape), and the popup takes that key:
        // the window the field is in (Preferences, say) is not closed with the suggestions.
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        field.textProperty().addListener((o, was, text) -> {
            if (!picking && (field.isFocused() || popup.isShowing())) {
                update();
            }
        });
        field.focusedProperty().addListener((o, was, focused) -> {
            if (focused) {
                update();
            } else {
                Platform.runLater(popup::hide);
            }
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        field.getProperties().put(InputSuggest.class, this);
    }

    /** The suggestions attached to {@code field}, if any. */
    public static Optional<InputSuggest> of(TextField field) {
        return field != null && field.getProperties().get(InputSuggest.class) instanceof InputSuggest suggest
                ? Optional.of(suggest) : Optional.empty();
    }

    /**
     * Suggestions from {@code items} that fuzzy-match what is typed, as the quick switcher
     * matches (the best first; equal ones, the shortest first), or all of them, in their order,
     * when nothing is typed.
     */
    public static Function<String, List<String>> fuzzy(Supplier<List<String>> items) {
        return typed -> {
            String query = typed != null ? typed.strip() : "";
            if (query.isEmpty()) {
                return items.get();
            }
            return items.get().stream()
                    .map(item -> new Scored(item, FuzzySearchUtils.fuzzyScore(query, item)))
                    .filter(scored -> scored.score() > 0)
                    .sorted(Comparator.comparingInt(Scored::score).reversed()
                            .thenComparingInt(scored -> scored.item().length())
                            .thenComparing(Scored::item, String.CASE_INSENSITIVE_ORDER))
                    .map(Scored::item)
                    .toList();
        };
    }

    private record Scored(String item, int score) {
    }

    /** At most {@code limit} suggestions show ({@value #DEFAULT_LIMIT} by default). */
    public InputSuggest limit(int limit) {
        this.limit = Math.max(1, limit);
        return this;
    }

    /** Called with a suggestion picked (after it is written in the field). */
    public InputSuggest onSelect(Consumer<String> onSelect) {
        this.onSelect = onSelect != null ? onSelect : value -> {
        };
        return this;
    }

    /** Shows the suggestions for what the field holds now. */
    public void show() {
        update();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    /** The suggestions for what the field holds, shown under it while it is in a window. */
    public List<String> shown() {
        return List.copyOf(list.getItems());
    }

    private void update() {
        List<String> suggestions = getSuggestions.apply(field.getText());
        List<String> shown = suggestions.size() > limit ? suggestions.subList(0, limit) : suggestions;
        list.getItems().setAll(shown);
        if (shown.isEmpty() || field.getScene() == null || field.getScene().getWindow() == null) {
            popup.hide(); // nothing to suggest, or nowhere to show it
            return;
        }
        list.getSelectionModel().selectFirst();
        list.scrollTo(0);
        list.setPrefWidth(field.getWidth());
        list.setPrefHeight(Math.min(shown.size(), VISIBLE_ROWS) * ROW_HEIGHT + 4);
        if (!popup.isShowing()) {
            // The popup is its own window: it takes the stylesheets (theme, font) of the field's.
            popup.getScene().getStylesheets().setAll(field.getScene().getStylesheets());
            Bounds bounds = field.localToScreen(field.getBoundsInLocal());
            if (bounds != null) {
                popup.show(field, bounds.getMinX(), bounds.getMaxY());
            }
        }
    }

    private void onKey(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN, UP -> {
                if (!popup.isShowing()) {
                    update();
                } else {
                    int index = list.getSelectionModel().getSelectedIndex() + (event.getCode() == KeyCode.DOWN ? 1 : -1);
                    index = Math.max(0, Math.min(list.getItems().size() - 1, index));
                    list.getSelectionModel().select(index);
                    list.scrollTo(index);
                }
                event.consume();
            }
            case ENTER, TAB -> {
                String selected = list.getSelectionModel().getSelectedItem();
                if (popup.isShowing() && selected != null) {
                    pick(selected);
                    event.consume();
                }
            }
            default -> {
                // Typing: the text listener updates the list.
            }
        }
    }

    private void pick(String value) {
        picking = true;
        try {
            field.setText(value);
            field.positionCaret(value.length());
        } finally {
            picking = false;
        }
        popup.hide();
        onSelect.accept(value);
    }
}
