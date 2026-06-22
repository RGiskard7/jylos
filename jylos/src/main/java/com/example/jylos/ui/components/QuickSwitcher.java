package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import java.util.ResourceBundle;

import com.example.jylos.config.AppContext;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
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
    private boolean isDarkTheme = false;
    
    public QuickSwitcher(Stage parentStage) {
        this.parentStage = parentStage;
    }

    /** Resolves an i18n string, falling back to the key if the bundle is unavailable. */
    private String i18n(String key) {
        try {
            ResourceBundle bundle = AppContext.isInitialized() ? AppContext.getBundle() : null;
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
    
    /** Switches the inline color palette between dark and light before the next {@link #show()} call. */
    public void setDarkTheme(boolean isDark) {
        this.isDarkTheme = isDark;
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
        
        // Colors matching app's dark/light theme (from CSS)
        String bg = isDarkTheme ? "#1e1e1e" : "#ffffff";
        String fg = isDarkTheme ? "#e0e0e0" : "#1e1e1e";
        String border = isDarkTheme ? "#3a3a3a" : "#e0e0e0";
        String searchBg = isDarkTheme ? "#252525" : "#f5f5f5";
        String hoverBg = isDarkTheme ? "#333333" : "#f0f0f0";
        String accentColor = "#7c3aed";
        String mutedColor = isDarkTheme ? "#888888" : "#666666";
        String favoriteColor = "#f39c12";
        
        // Main container
        VBox mainContainer = new VBox(0);
        mainContainer.setMaxWidth(580);
        mainContainer.setMaxHeight(420);
        mainContainer.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 12; " +
            "-fx-border-radius: 12; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 25, 0, 0, 8);",
            bg, border
        ));
        
        // Search container
        HBox searchContainer = new HBox(12);
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        searchContainer.setPadding(new Insets(16, 20, 16, 20));
        searchContainer.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 12 12 0 0;",
            searchBg
        ));
        
        // Search icon
        Label searchIcon = new Label("/");
        searchIcon.setStyle(String.format(
            "-fx-font-size: 18px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: %s;",
            accentColor
        ));
        
        // Search field
        searchField = new TextField();
        searchField.setPromptText(i18n("switcher.search_placeholder"));
        searchField.setStyle(String.format(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 16px; " +
            "-fx-prompt-text-fill: %s; " +
            "-fx-border-width: 0;",
            fg, mutedColor
        ));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchContainer.getChildren().addAll(searchIcon, searchField);
        
        // Separator
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle(String.format("-fx-background-color: %s;", border));
        
        // Notes list
        noteListView = new ListView<>();
        noteListView.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-insets: 0; " +
            "-fx-padding: 8;",
            bg
        ));
        noteListView.setCellFactory(lv -> createNoteCell(bg, fg, hoverBg, accentColor, mutedColor, favoriteColor));
        noteListView.setFixedCellSize(64);
        VBox.setVgrow(noteListView, Priority.ALWAYS);
        
        // Footer
        HBox footer = new HBox(20);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 16, 10, 16));
        footer.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 0 0 12 12; " +
            "-fx-border-color: %s transparent transparent transparent; " +
            "-fx-border-width: 1 0 0 0;",
            searchBg, border
        ));
        
        statusLabel = new Label();
        statusLabel.setStyle(String.format("-fx-font-size: 11px; -fx-text-fill: %s;", mutedColor));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        Label helpHint = new Label(i18n("switcher.hint"));
        helpHint.setStyle(String.format("-fx-font-size: 11px; -fx-text-fill: %s;", mutedColor));
        
        footer.getChildren().addAll(statusLabel, helpHint);
        
        mainContainer.getChildren().addAll(searchContainer, separator, noteListView, footer);
        
        // Overlay
        StackPane overlay = new StackPane(mainContainer);
        overlay.setAlignment(Pos.TOP_CENTER);
        overlay.setPadding(new Insets(80, 0, 0, 0));
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.4);");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                hide();
            }
        });
        
        // Scene
        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        switcherStage.setScene(scene);
        
        switcherStage.setWidth(parentStage.getWidth());
        switcherStage.setHeight(parentStage.getHeight());
        switcherStage.setX(parentStage.getX());
        switcherStage.setY(parentStage.getY());
        
        setupEventHandlers();
    }
    
    private ListCell<Note> createNoteCell(String bg, String fg, String hoverBg, String accentColor, String mutedColor, String favoriteColor) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                
                if (empty || note == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox container = new HBox(14);
                    container.setAlignment(Pos.CENTER_LEFT);
                    container.setPadding(new Insets(10, 12, 10, 12));
                    
                    // Note icon with favorite indicator
                    String iconText = note.isFavorite() ? "*" : "#";
                    String iconColor = note.isFavorite() ? favoriteColor : accentColor;
                    
                    Label iconLabel = new Label(iconText);
                    iconLabel.setMinWidth(32);
                    iconLabel.setAlignment(Pos.CENTER);
                    iconLabel.setStyle(String.format(
                        "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: %s; " +
                        "-fx-background-color: %s; " +
                        "-fx-background-radius: 6; " +
                        "-fx-padding: 6 8;",
                        iconColor, isDarkTheme ? "#333333" : "#f0f0f0"
                    ));
                    
                    // Text container
                    VBox textContainer = new VBox(3);
                    
                    // Title
                    String title = note.getTitle() != null ? note.getTitle() : "Untitled";
                    Label titleLabel = new Label(title);
                    titleLabel.setStyle(String.format(
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: %s;",
                        fg
                    ));
                    
                    // Preview (first line of content)
                    String content = note.getContent();
                    String preview = "";
                    if (content != null && !content.trim().isEmpty()) {
                        String[] lines = content.split("\n");
                        String firstLine = lines[0].replaceAll("^#+\\s*", "").trim();
                        preview = firstLine.length() > 70 ? firstLine.substring(0, 70) + "..." : firstLine;
                    }
                    
                    Label previewLabel = new Label(preview);
                    previewLabel.setStyle(String.format(
                        "-fx-font-size: 11px; " +
                        "-fx-text-fill: %s;",
                        mutedColor
                    ));
                    
                    textContainer.getChildren().addAll(titleLabel, previewLabel);
                    HBox.setHgrow(textContainer, Priority.ALWAYS);
                    
                    container.getChildren().addAll(iconLabel, textContainer);
                    
                    // Modified date
                    String modified = note.getModifiedDate();
                    if (modified != null && !modified.isEmpty()) {
                        String dateStr = modified.contains("T") ? modified.substring(0, 10) : modified;
                        Label dateLabel = new Label(dateStr);
                        dateLabel.setStyle(String.format(
                            "-fx-font-size: 10px; " +
                            "-fx-text-fill: %s;",
                            mutedColor
                        ));
                        container.getChildren().add(dateLabel);
                    }
                    
                    setGraphic(container);
                    
                    // Styles
                    String baseStyle = "-fx-background-color: transparent; -fx-background-radius: 8; -fx-padding: 2;";
                    String selectedStyle = String.format("-fx-background-color: %s; -fx-background-radius: 8; -fx-padding: 2;", accentColor);
                    String hoverStyle = String.format("-fx-background-color: %s; -fx-background-radius: 8; -fx-padding: 2;", hoverBg);
                    
                    if (isSelected()) {
                        setStyle(selectedStyle);
                        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
                        previewLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.8);");
                        // Clear hover handlers so hovering the (white-text) selected row
                        // does not swap in the light hover background and hide the text.
                        setOnMouseEntered(null);
                        setOnMouseExited(null);
                    } else {
                        setStyle(baseStyle);
                        setOnMouseEntered(e -> setStyle(hoverStyle));
                        setOnMouseExited(e -> setStyle(baseStyle));
                    }
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
            statusLabel.setText(total + " notes");
        } else {
            statusLabel.setText(showing + " of " + total + " notes");
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
