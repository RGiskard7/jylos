package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import java.util.ResourceBundle;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.util.FuzzySearchUtils;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Quick Switcher component - similar to Obsidian/VS Code.
 * Provides fast navigation between notes using fuzzy search.
 * Uses universal text symbols for cross-platform compatibility.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.1.0
 */
public class QuickSwitcher {
    
    private static final Logger logger = LoggerConfig.getLogger(QuickSwitcher.class);
    
    private final Stage parentStage;
    private Stage switcherStage;
    private TextField searchField;
    private ListView<Note> noteListView;
    private Label statusLabel;
    private List<Note> allNotes = new ArrayList<>();
    private Consumer<Note> onNoteSelected;
    private ResourceBundle bundle;
    
    public QuickSwitcher(Stage parentStage) {
        this.parentStage = parentStage;
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** Resolves an i18n string, falling back to the key if the bundle is unavailable. */
    private String i18n(String key) {
        try {
            return bundle != null ? bundle.getString(key) : key;
        } catch (Exception e) {
            return key;
        }
    }
    
    /** Replaces the full note list used for search; a defensive copy is stored to prevent external mutation. */
    public void setNotes(List<Note> notes) {
        this.allNotes = notes != null ? new ArrayList<>(notes) : new ArrayList<>();
    }
    
    /** Registers the callback invoked (on the FX thread) when the user confirms a note selection. */
    public void setOnNoteSelected(Consumer<Note> callback) {
        this.onNoteSelected = callback;
    }
    
    /** Shows the quick-switcher overlay; brings it to front if already showing, otherwise creates and animates the stage. */
    public void show() {
        if (switcherStage != null && switcherStage.isShowing()) {
            switcherStage.toFront();
            searchField.requestFocus();
            return;
        }
        
        createSwitcherStage();
        switcherStage.show();
        animateEntrance();
        
        Platform.runLater(() -> {
            searchField.requestFocus();
            searchField.selectAll();
            noteListView.getItems().setAll(allNotes);
            updateStatusLabel();
            if (!allNotes.isEmpty()) {
                noteListView.getSelectionModel().selectFirst();
            }
        });
    }
    
    /** Animates the exit, then hides the switcher stage. */
    public void hide() {
        if (switcherStage != null && switcherStage.isShowing()) {
            animateExit(() -> switcherStage.hide());
        }
    }
    
    /** Builds the transparent overlay stage with the search field, note list, and footer status bar. */
    private void createSwitcherStage() {
        switcherStage = new Stage();
        switcherStage.initOwner(parentStage);
        switcherStage.initModality(Modality.APPLICATION_MODAL);
        switcherStage.initStyle(StageStyle.TRANSPARENT);

        VBox mainContainer = new VBox(0);
        mainContainer.setMaxWidth(580);
        mainContainer.setMaxHeight(420);
        mainContainer.getStyleClass().addAll("overlay-panel", "quick-switcher-panel");
        
        HBox searchContainer = new HBox(12);
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        searchContainer.setPadding(new Insets(16, 20, 16, 20));
        searchContainer.getStyleClass().add("overlay-search-row");
        
        Label searchIcon = new Label("/");
        searchIcon.getStyleClass().add("overlay-search-icon");
        
        searchField = new TextField();
        searchField.setPromptText(i18n("switcher.search_placeholder"));
        searchField.getStyleClass().add("overlay-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchContainer.getChildren().addAll(searchIcon, searchField);
        
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.getStyleClass().add("overlay-divider");
        
        noteListView = new ListView<>();
        noteListView.getStyleClass().add("overlay-list-view");
        noteListView.setCellFactory(lv -> createNoteCell());
        noteListView.setFixedCellSize(64);
        VBox.setVgrow(noteListView, Priority.ALWAYS);
        
        HBox footer = new HBox(20);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 16, 10, 16));
        footer.getStyleClass().add("overlay-footer");
        
        statusLabel = new Label();
        statusLabel.getStyleClass().add("overlay-status");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        Label helpHint = new Label(i18n("switcher.hint"));
        helpHint.getStyleClass().add("overlay-hint");
        
        footer.getChildren().addAll(statusLabel, helpHint);
        
        mainContainer.getChildren().addAll(searchContainer, separator, noteListView, footer);
        
        StackPane overlay = new StackPane(mainContainer);
        overlay.setAlignment(Pos.TOP_CENTER);
        overlay.setPadding(new Insets(80, 0, 0, 0));
        overlay.getStyleClass().addAll("overlay-backdrop", "quick-switcher-overlay");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                hide();
            }
        });
        
        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        UiDialogs.apply(scene);
        switcherStage.setScene(scene);
        
        switcherStage.setWidth(parentStage.getWidth());
        switcherStage.setHeight(parentStage.getHeight());
        switcherStage.setX(parentStage.getX());
        switcherStage.setY(parentStage.getY());
        
        setupEventHandlers();
    }
    
    private ListCell<Note> createNoteCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                if (!getStyleClass().contains("overlay-list-cell")) {
                    getStyleClass().addAll("overlay-list-cell", "quick-switcher-cell");
                }
                
                if (empty || note == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox container = new HBox(14);
                    container.setAlignment(Pos.CENTER_LEFT);
                    container.setPadding(new Insets(10, 12, 10, 12));
                    container.getStyleClass().add("overlay-item");
                    
                    String iconText = note.isFavorite() ? "*" : "#";
                    
                    Label iconLabel = new Label(iconText);
                    iconLabel.setMinWidth(32);
                    iconLabel.setAlignment(Pos.CENTER);
                    iconLabel.getStyleClass().add("overlay-item-icon");
                    if (note.isFavorite()) {
                        iconLabel.getStyleClass().add("overlay-item-icon-favorite");
                    }
                    
                    VBox textContainer = new VBox(3);
                    
                    String title = note.getTitle() != null ? note.getTitle() : "Untitled";
                    Label titleLabel = new Label(title);
                    titleLabel.getStyleClass().add("overlay-item-title");
                    
                    String content = note.getContent();
                    String preview = "";
                    if (content != null && !content.trim().isEmpty()) {
                        String[] lines = content.split("\n");
                        String firstLine = lines[0].replaceAll("^#+\\s*", "").trim();
                        preview = firstLine.length() > 70 ? firstLine.substring(0, 70) + "..." : firstLine;
                    }
                    
                    Label previewLabel = new Label(preview);
                    previewLabel.getStyleClass().add("overlay-item-subtitle");
                    
                    textContainer.getChildren().addAll(titleLabel, previewLabel);
                    HBox.setHgrow(textContainer, Priority.ALWAYS);
                    
                    container.getChildren().addAll(iconLabel, textContainer);
                    
                    String modified = note.getModifiedDate();
                    if (modified != null && !modified.isEmpty()) {
                        String dateStr = modified.contains("T") ? modified.substring(0, 10) : modified;
                        Label dateLabel = new Label(dateStr);
                        dateLabel.getStyleClass().add("overlay-item-meta");
                        container.getChildren().add(dateLabel);
                    }
                    
                    setGraphic(container);
                }
            }
        };
    }
    
    /** Wires the search-field text listener, keyboard handlers, and double-click-to-select on the note list. */
    private void setupEventHandlers() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterNotes(newVal);
            updateStatusLabel();
        });
        
        searchField.setOnKeyPressed(this::handleKeyPress);
        noteListView.setOnKeyPressed(this::handleKeyPress);
        noteListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                selectNote();
            }
        });
    }
    
    /** Routes ESCAPE, ENTER, UP and DOWN key events to their respective switcher actions. */
    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE:
                hide();
                event.consume();
                break;
            case ENTER:
                selectNote();
                event.consume();
                break;
            case UP:
                navigateUp();
                event.consume();
                break;
            case DOWN:
                navigateDown();
                event.consume();
                break;
            default:
                break;
        }
    }
    
    /** Filters {@link #allNotes} by fuzzy-matching {@code query} against title and content, sorted by relevance then alphabetically. */
    private void filterNotes(String query) {
        if (query == null || query.trim().isEmpty()) {
            noteListView.getItems().setAll(allNotes);
        } else {
            String searchLower = query.toLowerCase().trim();
            List<Note> filtered = allNotes.stream()
                .filter(note -> fuzzyMatch(note, searchLower))
                .sorted((a, b) -> compareRelevance(a, b, searchLower))
                .collect(Collectors.toList());
            noteListView.getItems().setAll(filtered);
        }
        
        if (!noteListView.getItems().isEmpty()) {
            noteListView.getSelectionModel().selectFirst();
        }
    }
    
    /** Returns {@code true} if {@code query} produces a positive fuzzy score against the note's title or content. */
    private boolean fuzzyMatch(Note note, String query) {
        String title = note.getTitle() != null ? note.getTitle() : "";
        String content = note.getContent() != null ? note.getContent() : "";

        return FuzzySearchUtils.fuzzyScore(query, title) > 0
                || FuzzySearchUtils.fuzzyScore(query, content) > 0;
    }

    /** Compares two notes by descending fuzzy title score, with alphabetical title as tiebreaker. */
    private int compareRelevance(Note a, Note b, String query) {
        int scoreA = FuzzySearchUtils.fuzzyScore(query, a.getTitle() != null ? a.getTitle() : "");
        int scoreB = FuzzySearchUtils.fuzzyScore(query, b.getTitle() != null ? b.getTitle() : "");
        // Higher score first
        if (scoreA != scoreB) {
            return Integer.compare(scoreB, scoreA);
        }
        // Tiebreak: alphabetical
        String titleA = a.getTitle() != null ? a.getTitle().toLowerCase() : "";
        String titleB = b.getTitle() != null ? b.getTitle().toLowerCase() : "";
        return titleA.compareTo(titleB);
    }
    
    /** Selects the previous note in the list, scrolling to keep it in view. */
    private void navigateUp() {
        int idx = noteListView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            noteListView.getSelectionModel().select(idx - 1);
            noteListView.scrollTo(idx - 1);
        }
    }
    
    /** Selects the next note in the list, scrolling to keep it in view. */
    private void navigateDown() {
        int idx = noteListView.getSelectionModel().getSelectedIndex();
        if (idx < noteListView.getItems().size() - 1) {
            noteListView.getSelectionModel().select(idx + 1);
            noteListView.scrollTo(idx + 1);
        }
    }
    
    /** Hides the switcher and fires {@link #onNoteSelected} on the FX thread with the currently selected note. */
    private void selectNote() {
        Note selected = noteListView.getSelectionModel().getSelectedItem();
        if (selected != null && onNoteSelected != null) {
            hide();
            Platform.runLater(() -> {
                try {
                    onNoteSelected.accept(selected);
                    logger.info("Selected note: " + selected.getTitle());
                } catch (Exception e) {
                    logger.severe("Error selecting note: " + e.getMessage());
                }
            });
        }
    }
    
    /** Updates the footer status label to show "N notes" or "N of M notes" when a filter is active. */
    private void updateStatusLabel() {
        int showing = noteListView.getItems().size();
        int total = allNotes.size();
        if (showing == total) {
            statusLabel.setText(java.text.MessageFormat.format(i18n("info.notes_count"), total));
        } else {
            statusLabel.setText(java.text.MessageFormat.format(i18n("switcher.notes_filtered"), showing, total));
        }
    }
    
    /** Plays a 150 ms fade-in + scale-up entrance animation on the main switcher container. */
    private void animateEntrance() {
        VBox container = (VBox) ((StackPane) switcherStage.getScene().getRoot()).getChildren().get(0);
        
        FadeTransition fade = new FadeTransition(Duration.millis(150), container);
        fade.setFromValue(0);
        fade.setToValue(1);
        
        ScaleTransition scale = new ScaleTransition(Duration.millis(150), container);
        scale.setFromX(0.95);
        scale.setFromY(0.95);
        scale.setToX(1);
        scale.setToY(1);
        
        fade.play();
        scale.play();
    }
    
    /** Plays a 100 ms fade-out animation, then invokes {@code onFinished} to hide or close the stage. */
    private void animateExit(Runnable onFinished) {
        VBox container = (VBox) ((StackPane) switcherStage.getScene().getRoot()).getChildren().get(0);
        
        FadeTransition fade = new FadeTransition(Duration.millis(100), container);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> onFinished.run());
        fade.play();
    }
}
