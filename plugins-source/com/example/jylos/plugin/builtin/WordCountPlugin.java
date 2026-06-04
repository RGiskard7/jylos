package com.example.jylos.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;

/**
 * Word Count Plugin - Displays word and character statistics for notes.
 * 
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>Word count for the current note</li>
 *   <li>Character count (with and without spaces)</li>
 *   <li>Line count</li>
 *   <li>Paragraph count</li>
 *   <li>Statistics across all notes</li>
 * </ul>
 * 
 * <h2>Commands:</h2>
 * <ul>
 *   <li><b>Word Count: Current Note</b> - Shows statistics for current note</li>
 *   <li><b>Word Count: All Notes</b> - Shows total statistics</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class WordCountPlugin implements Plugin {
    
    private static final String ID = "word-count";
    private static final String NAME = "Word Count";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Displays word and character statistics for notes";
    private static final String AUTHOR = "Jylos Team";
    
    private PluginContext context;
    private Note currentNote;
    private List<EventBus.Subscription> subscriptions = new ArrayList<>();
    
    @Override
    public String getId() {
        return ID;
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getVersion() {
        return VERSION;
    }
    
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
    
    @Override
    public String getAuthor() {
        return AUTHOR;
    }
    
    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        
        // Register commands in Command Palette
        context.registerCommand(
            "Word Count: Current Note",
            "Show word and character statistics for current note",
            "Ctrl+Shift+W",
            this::showCurrentNoteStats
        );
        
        context.registerCommand(
            "Word Count: All Notes",
            "Show total word count across all notes",
            null,
            this::showAllNotesStats
        );
        
        // Register menu items (dynamic plugin menu)
        context.registerMenuItem("Core", "Word Count", "Ctrl+Shift+W", this::showCurrentNoteStats);
        context.registerMenuItem("Core", "All Notes Stats", this::showAllNotesStats);
        
        // Subscribe to note selection events
        EventBus.Subscription sub = context.subscribe(NoteEvents.NoteSelectedEvent.class, event -> {
            this.currentNote = event.getNote();
        });
        subscriptions.add(sub);
        
        context.log("Word Count Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        // Unsubscribe from all events
        for (EventBus.Subscription sub : subscriptions) {
            sub.cancel();
        }
        subscriptions.clear();
        
        // Unregister commands
        context.unregisterCommand("Word Count: Current Note");
        context.unregisterCommand("Word Count: All Notes");
        
        context.log("Word Count Plugin shutdown");
    }
    
    /**
     * Shows word count statistics for the current note.
     */
    private void showCurrentNoteStats() {
        if (currentNote == null) {
            showAlert("Word Count", "No note selected", "Please select a note first.");
            return;
        }
        
        String content = currentNote.getContent();
        if (content == null) {
            content = "";
        }
        
        Statistics stats = calculateStatistics(content);
        
        String message = String.format(
            "Note: %s\n\n" +
            "Words: %,d\n" +
            "Characters (with spaces): %,d\n" +
            "Characters (without spaces): %,d\n" +
            "Lines: %,d\n" +
            "Paragraphs: %,d",
            currentNote.getTitle(),
            stats.words,
            stats.charsWithSpaces,
            stats.charsWithoutSpaces,
            stats.lines,
            stats.paragraphs
        );
        
        showAlert("Word Count - Current Note", null, message);
    }
    
    /**
     * Shows total word count across all notes.
     */
    private void showAllNotesStats() {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        int totalWords = 0;
        int totalChars = 0;
        int totalNotes = allNotes.size();
        
        for (Note note : allNotes) {
            String content = note.getContent();
            if (content != null) {
                totalWords += countWords(content);
                totalChars += content.length();
            }
        }
        
        String message = String.format(
            "Total Notes: %,d\n\n" +
            "Total Words: %,d\n" +
            "Total Characters: %,d\n" +
            "Average Words per Note: %,d",
            totalNotes,
            totalWords,
            totalChars,
            totalNotes > 0 ? totalWords / totalNotes : 0
        );
        
        showAlert("Word Count - All Notes", null, message);
    }
    
    /**
     * Calculates comprehensive statistics for text content.
     */
    private Statistics calculateStatistics(String text) {
        Statistics stats = new Statistics();
        
        if (text == null || text.isEmpty()) {
            return stats;
        }
        
        stats.charsWithSpaces = text.length();
        stats.charsWithoutSpaces = text.replace(" ", "").replace("\t", "").replace("\n", "").replace("\r", "").length();
        stats.words = countWords(text);
        stats.lines = text.split("\n", -1).length;
        stats.paragraphs = countParagraphs(text);
        
        return stats;
    }
    
    /**
     * Counts words in text.
     */
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
    
    /**
     * Counts paragraphs (blocks of text separated by blank lines).
     */
    private int countParagraphs(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // Split by two or more newlines
        String[] paragraphs = text.trim().split("\\n\\s*\\n");
        return paragraphs.length;
    }
    
    /**
     * Shows an information alert dialog.
     */
    private void showAlert(String title, String header, String content) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION
            );
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
    
    /**
     * Statistics container class.
     */
    private static class Statistics {
        int words = 0;
        int charsWithSpaces = 0;
        int charsWithoutSpaces = 0;
        int lines = 0;
        int paragraphs = 0;
    }
}
