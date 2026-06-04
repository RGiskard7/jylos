package com.example.jylos.plugin.builtin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;

/**
 * Daily Notes Plugin - Creates and manages daily notes.
 * 
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>Automatic creation of daily notes</li>
 *   <li>Quick access to today's note</li>
 *   <li>Navigation to previous/next day notes</li>
 *   <li>Optional "Daily Notes" folder organization</li>
 * </ul>
 * 
 * <h2>Commands:</h2>
 * <ul>
 *   <li><b>Daily Notes: Open Today</b> - Opens or creates today's daily note</li>
 *   <li><b>Daily Notes: Open Yesterday</b> - Opens yesterday's daily note</li>
 *   <li><b>Daily Notes: Open Specific Date</b> - Opens a note for a specific date</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class DailyNotesPlugin implements Plugin {
    
    private static final String ID = "daily-notes";
    private static final String NAME = "Daily Notes";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Create and manage daily notes with automatic dating";
    private static final String AUTHOR = "Jylos Team";
    
    private static final String DAILY_NOTES_FOLDER = "Daily Notes";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    
    private PluginContext context;
    
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
            "Daily Notes: Open Today",
            "Open or create today's daily note",
            "Ctrl+Alt+D",
            () -> openDailyNote(LocalDate.now())
        );
        
        context.registerCommand(
            "Daily Notes: Open Yesterday",
            "Open yesterday's daily note",
            null,
            () -> openDailyNote(LocalDate.now().minusDays(1))
        );
        
        context.registerCommand(
            "Daily Notes: Open Tomorrow",
            "Open or create tomorrow's daily note",
            null,
            () -> openDailyNote(LocalDate.now().plusDays(1))
        );
        
        context.registerCommand(
            "Daily Notes: This Week",
            "Show all daily notes from this week",
            null,
            this::showThisWeekNotes
        );
        
        // Register menu items (dynamic plugin menu)
        context.registerMenuItem("Productivity", "Open Today's Note", "Ctrl+Alt+D", () -> openDailyNote(LocalDate.now()));
        context.registerMenuItem("Productivity", "Open Yesterday's Note", () -> openDailyNote(LocalDate.now().minusDays(1)));
        context.registerMenuItem("Productivity", "Open Tomorrow's Note", () -> openDailyNote(LocalDate.now().plusDays(1)));
        context.registerMenuItem("Productivity", "This Week Overview", this::showThisWeekNotes);
        
        context.log("Daily Notes Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        // Unregister commands
        context.unregisterCommand("Daily Notes: Open Today");
        context.unregisterCommand("Daily Notes: Open Yesterday");
        context.unregisterCommand("Daily Notes: Open Tomorrow");
        context.unregisterCommand("Daily Notes: This Week");
        
        context.log("Daily Notes Plugin shutdown");
    }
    
    /**
     * Opens or creates a daily note for the specified date.
     * 
     * @param date The date for the daily note
     */
    private void openDailyNote(LocalDate date) {
        String noteTitle = formatDailyNoteTitle(date);
        
        // Search for existing note with this title
        Optional<Note> existingNote = findNoteByTitle(noteTitle);
        
        if (existingNote.isPresent()) {
            // Note exists, open it directly
            Note note = existingNote.get();
            context.requestOpenNote(note);
            context.log("Opened existing daily note: " + note.getTitle());
        } else {
            // Create new daily note
            createDailyNote(date);
        }
    }
    
    /**
     * Creates a new daily note for the specified date.
     */
    private void createDailyNote(LocalDate date) {
        String title = formatDailyNoteTitle(date);
        String content = generateDailyNoteTemplate(date);
        
        try {
            // Get or create the Daily Notes folder
            Folder folder = getOrCreateDailyNotesFolder();
            
            // Create the note
            Note note;
            if (folder != null) {
                note = context.getNoteService().createNoteInFolder(title, content, folder);
            } else {
                note = context.getNoteService().createNote(title, content);
            }
            
            // Open the newly created note in the editor
            context.requestOpenNote(note);
            context.requestRefreshNotes();
            
            context.log("Created and opened daily note: " + title);
        } catch (Exception e) {
            context.logError("Failed to create daily note", e);
            context.showError("Daily Notes Error", "Failed to create daily note: " + e.getMessage());
        }
    }
    
    /**
     * Shows all daily notes from the current week.
     */
    private void showThisWeekNotes() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Daily Notes This Week:\n\n");
        
        int foundCount = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate date = startOfWeek.plusDays(i);
            String title = formatDailyNoteTitle(date);
            Optional<Note> note = findNoteByTitle(title);
            
            String status = note.isPresent() ? "[x]" : "[ ]";
            String dayName = date.getDayOfWeek().toString();
            sb.append(String.format("%s %s - %s\n", status, dayName.substring(0, 3), date.format(DATE_FORMATTER)));
            
            if (note.isPresent()) {
                foundCount++;
            }
        }
        
        sb.append(String.format("\nNotes found: %d/7", foundCount));
        
        context.showInfo("Daily Notes - This Week", null, sb.toString());
    }
    
    /**
     * Formats the title for a daily note.
     */
    private String formatDailyNoteTitle(LocalDate date) {
        return "Daily Note - " + date.format(DATE_FORMATTER);
    }
    
    /**
     * Generates the template content for a new daily note.
     */
    private String generateDailyNoteTemplate(LocalDate date) {
        return String.format(
            "# %s\n\n" +
            "## Tasks\n\n" +
            "- [ ] \n\n" +
            "## Notes\n\n" +
            "\n\n" +
            "## Reflection\n\n" +
            "\n\n" +
            "---\n" +
            "*Created with Daily Notes Plugin*\n",
            date.format(DISPLAY_FORMATTER)
        );
    }
    
    /**
     * Finds a note by its exact title.
     */
    private Optional<Note> findNoteByTitle(String title) {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        return allNotes.stream()
            .filter(n -> title.equals(n.getTitle()))
            .findFirst();
    }
    
    /**
     * Gets or creates the Daily Notes folder.
     */
    private Folder getOrCreateDailyNotesFolder() {
        try {
            List<Folder> folders = context.getFolderService().getAllFolders();
            Optional<Folder> existing = folders.stream()
                .filter(f -> DAILY_NOTES_FOLDER.equals(f.getTitle()))
                .findFirst();
            
            if (existing.isPresent()) {
                return existing.get();
            }
            
            // Create the folder
            return context.getFolderService().createFolder(DAILY_NOTES_FOLDER);
        } catch (Exception e) {
            context.logError("Failed to get/create Daily Notes folder", e);
            return null;
        }
    }
    
}
