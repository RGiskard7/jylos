package com.example.jylos.plugin.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Table of Contents Plugin - Generates TOC from Markdown headers.
 * 
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>Automatic TOC generation from Markdown headers</li>
 *   <li>Insertion at cursor position or top of document</li>
 *   <li>Supports # to ###### headers</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class TableOfContentsPlugin implements Plugin {
    
    private static final String ID = "table-of-contents";
    private static final String NAME = "Table of Contents";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Generate table of contents from Markdown headers";
    private static final String AUTHOR = "Jylos Team";
    
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    private PluginContext context;
    
    /**
     * Represents a header entry in the TOC.
     */
    private static class TocEntry {
        final int level;
        final String text;
        final String anchor;
        
        TocEntry(int level, String text) {
            this.level = level;
            this.text = text;
            // Create URL-friendly anchor
            this.anchor = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
        }
    }
    
    @Override
    public String getId() { return ID; }
    
    @Override
    public String getName() { return NAME; }
    
    @Override
    public String getVersion() { return VERSION; }
    
    @Override
    public String getDescription() { return DESCRIPTION; }
    
    @Override
    public String getAuthor() { return AUTHOR; }
    
    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        
        // Register commands
        context.registerCommand(
            "TOC: Generate Table of Contents",
            "Generate a table of contents from headers in the current note",
            "Ctrl+Shift+O",
            this::generateTocForCurrentNote
        );
        
        context.registerCommand(
            "TOC: Preview Table of Contents",
            "Preview the table of contents without inserting",
            null,
            this::previewToc
        );
        
        context.registerCommand(
            "TOC: Generate Numbered TOC",
            "Generate a numbered table of contents",
            null,
            this::generateNumberedToc
        );
        
        // Register menu items (dynamic plugin menu)
        context.registerMenuItem("Productivity", "Generate TOC", "Ctrl+Shift+O", this::generateTocForCurrentNote);
        context.registerMenuItem("Productivity", "Preview TOC", this::previewToc);
        context.registerMenuItem("Productivity", "Numbered TOC", this::generateNumberedToc);
        
        context.log("Table of Contents Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        context.unregisterCommand("TOC: Generate Table of Contents");
        context.unregisterCommand("TOC: Preview Table of Contents");
        context.unregisterCommand("TOC: Generate Numbered TOC");
        context.log("Table of Contents Plugin shutdown");
    }
    
    /**
     * Generates TOC for the current note and shows it in a dialog.
     */
    private void generateTocForCurrentNote() {
        showNoteSelectionDialog("Generate Table of Contents", false);
    }
    
    /**
     * Previews the TOC without inserting.
     */
    private void previewToc() {
        showNoteSelectionDialog("Preview Table of Contents", false);
    }
    
    /**
     * Generates a numbered TOC.
     */
    private void generateNumberedToc() {
        showNoteSelectionDialog("Generate Numbered TOC", true);
    }
    
    /**
     * Shows a dialog to select a note and generate TOC.
     */
    private void showNoteSelectionDialog(String title, boolean numbered) {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        if (allNotes.isEmpty()) {
            context.showInfo("Table of Contents", "No Notes", 
                "Create a note first to generate a table of contents.");
            return;
        }
        
        Platform.runLater(() -> {
            // Create custom dialog
            Dialog<Note> dialog = new Dialog<>();
            dialog.setTitle(title);
            dialog.setHeaderText("Select a note to generate TOC for:");
            
            // Set up buttons
            ButtonType generateButton = new ButtonType("Generate", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(generateButton, ButtonType.CANCEL);
            
            // Create ComboBox with proper cell factory
            ComboBox<Note> noteCombo = new ComboBox<>();
            noteCombo.getItems().addAll(allNotes);
            noteCombo.setValue(allNotes.get(0));
            noteCombo.setPrefWidth(350);
            
            // Configure how notes are displayed
            StringConverter<Note> converter = new StringConverter<Note>() {
                @Override
                public String toString(Note note) {
                    if (note == null) return "";
                    String noteTitle = note.getTitle();
                    return noteTitle != null ? noteTitle : "Untitled";
                }
                
                @Override
                public Note fromString(String string) {
                    return null;
                }
            };
            
            noteCombo.setConverter(converter);
            noteCombo.setButtonCell(new ListCell<Note>() {
                @Override
                protected void updateItem(Note note, boolean empty) {
                    super.updateItem(note, empty);
                    if (empty || note == null) {
                        setText("");
                    } else {
                        String noteTitle = note.getTitle();
                        setText(noteTitle != null ? noteTitle : "Untitled");
                    }
                }
            });
            noteCombo.setCellFactory(lv -> new ListCell<Note>() {
                @Override
                protected void updateItem(Note note, boolean empty) {
                    super.updateItem(note, empty);
                    if (empty || note == null) {
                        setText("");
                    } else {
                        String noteTitle = note.getTitle();
                        setText(noteTitle != null ? noteTitle : "Untitled");
                    }
                }
            });
            
            // Layout
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 20, 10, 20));
            grid.add(new Label("Note:"), 0, 0);
            grid.add(noteCombo, 1, 0);
            
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().setPrefWidth(450);
            
            // Convert result
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == generateButton) {
                    return noteCombo.getValue();
                }
                return null;
            });
            
            // Show and process result
            Optional<Note> result = dialog.showAndWait();
            result.ifPresent(note -> {
                String toc = generateToc(note.getContent(), numbered);
                if (toc.isEmpty()) {
                    showAlert("No Headers Found", 
                        "No Markdown headers (# to ######) found in this note.\n\n" +
                        "Headers must be on their own line starting with # symbols.");
                } else {
                    showTocResult(note.getTitle(), toc);
                }
            });
        });
    }
    
    /**
     * Shows a simple alert dialog.
     */
    private void showAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Table of Contents");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Generates TOC from content.
     */
    private String generateToc(String content, boolean numbered) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        List<TocEntry> entries = extractHeaders(content);
        
        if (entries.isEmpty()) {
            return "";
        }
        
        StringBuilder toc = new StringBuilder();
        toc.append("## Table of Contents\n\n");
        
        int[] counters = new int[6]; // For numbered TOC
        
        for (TocEntry entry : entries) {
            // Indent based on level
            String indent = "  ".repeat(entry.level - 1);
            
            if (numbered) {
                // Update counters
                counters[entry.level - 1]++;
                // Reset lower level counters
                for (int i = entry.level; i < 6; i++) {
                    counters[i] = 0;
                }
                
                // Build number string
                StringBuilder number = new StringBuilder();
                for (int i = 0; i < entry.level; i++) {
                    if (counters[i] > 0) {
                        if (number.length() > 0) number.append(".");
                        number.append(counters[i]);
                    }
                }
                
                toc.append(String.format("%s%s. [%s](#%s)\n", 
                    indent, number, entry.text, entry.anchor));
            } else {
                toc.append(String.format("%s- [%s](#%s)\n", 
                    indent, entry.text, entry.anchor));
            }
        }
        
        toc.append("\n---\n");
        
        return toc.toString();
    }
    
    /**
     * Extracts headers from Markdown content.
     */
    private List<TocEntry> extractHeaders(String content) {
        List<TocEntry> entries = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(content);
        
        while (matcher.find()) {
            int level = matcher.group(1).length();
            String text = matcher.group(2).trim();
            
            // Skip if it's the TOC header itself
            if (!text.equalsIgnoreCase("Table of Contents")) {
                entries.add(new TocEntry(level, text));
            }
        }
        
        return entries;
    }
    
    /**
     * Shows the generated TOC in a dialog for copying.
     */
    private void showTocResult(String noteTitle, String toc) {
        Platform.runLater(() -> {
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Generated Table of Contents");
            dialog.setHeaderText("TOC for: " + noteTitle);
            
            ButtonType copyButton = new ButtonType("Copy to Clipboard", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(copyButton, ButtonType.CLOSE);
            
            TextArea textArea = new TextArea(toc);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefRowCount(15);
            textArea.setPrefColumnCount(50);
            textArea.setStyle("-fx-font-family: monospace;");
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            content.getChildren().addAll(
                new Label("Copy this TOC and paste it at the top of your note:"),
                textArea
            );
            
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefSize(500, 400);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == copyButton) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent clipContent = new ClipboardContent();
                    clipContent.putString(toc);
                    clipboard.setContent(clipContent);
                    context.log("TOC copied to clipboard");
                }
                return null;
            });
            
            dialog.showAndWait();
        });
    }
}
