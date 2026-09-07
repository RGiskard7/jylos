package com.example.jylos.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;

/**
 * Reading Time Plugin - Estimates reading time for notes.
 * 
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>Estimated reading time for the current note</li>
 *   <li>Speaking time estimate (for presentations)</li>
 *   <li>Total reading time across all notes</li>
 *   <li>Configurable words-per-minute rate</li>
 * </ul>
 * 
 * <h2>Commands:</h2>
 * <ul>
 *   <li><b>Reading Time: Current Note</b> - Shows reading time for current note</li>
 *   <li><b>Reading Time: All Notes</b> - Shows total reading time</li>
 * </ul>
 * 
 * <p>Default reading speeds:</p>
 * <ul>
 *   <li>Average reading: 200 words per minute</li>
 *   <li>Speed reading: 400 words per minute</li>
 *   <li>Speaking: 150 words per minute</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class ReadingTimePlugin implements Plugin {
    
    private static final String ID = "reading-time";
    private static final String NAME = "Reading Time";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Estimates reading and speaking time for notes";
    private static final String AUTHOR = "Jylos Team";
    
    // Reading speeds in words per minute
    private static final int SLOW_WPM = 150;       // Careful reading
    private static final int AVERAGE_WPM = 200;    // Normal reading
    private static final int FAST_WPM = 400;       // Speed reading
    private static final int SPEAKING_WPM = 150;   // Speaking/presentation
    
    private PluginContext context;
    private Note currentNote;
    private List<EventBus.Subscription> subscriptions = new ArrayList<>();

    // Loaded once, lazily — getDescription() can be called by the Plugin Manager before
    // initialize() ever runs, so this cannot wait for that.
    private static java.util.ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(ReadingTimePlugin.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

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
        return tr("readingtime.plugin.description", DESCRIPTION);
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
            "Reading Time: Current Note",
            tr("readingtime.command.current.description", "Show estimated reading time for current note"),
            "Ctrl+Shift+R",
            this::showCurrentNoteReadingTime
        );

        context.registerCommand(
            "Reading Time: All Notes",
            tr("readingtime.command.all.description", "Show total reading time across all notes"),
            null,
            this::showAllNotesReadingTime
        );

        context.registerCommand(
            "Reading Time: Quick Estimate",
            tr("readingtime.command.quick.description", "Show quick time estimate for current note"),
            null,
            this::showQuickEstimate
        );

        // Register menu items (dynamic plugin menu)
        context.registerMenuItem(tr("menuCategory.core", "Core"), tr("readingtime.menu.current", "Reading Time"), "Ctrl+Shift+R", this::showCurrentNoteReadingTime);
        context.registerMenuItem(tr("menuCategory.core", "Core"), tr("readingtime.menu.quickEstimate", "Quick Estimate"), this::showQuickEstimate);
        
        // Subscribe to note selection events
        EventBus.Subscription sub = context.subscribe(NoteEvents.NoteSelectedEvent.class, event -> {
            this.currentNote = event.getNote();
        });
        subscriptions.add(sub);
        
        context.log("Reading Time Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        // Unsubscribe from all events
        for (EventBus.Subscription sub : subscriptions) {
            sub.cancel();
        }
        subscriptions.clear();
        
        // Unregister commands
        context.unregisterCommand("Reading Time: Current Note");
        context.unregisterCommand("Reading Time: All Notes");
        context.unregisterCommand("Reading Time: Quick Estimate");
        
        context.log("Reading Time Plugin shutdown");
    }
    
    /**
     * Shows detailed reading time for the current note.
     */
    private void showCurrentNoteReadingTime() {
        if (currentNote == null) {
            showAlert(tr("readingtime.title", "Reading Time"), tr("readingtime.alert.noNote.header", "No note selected"),
                    tr("readingtime.alert.noNote.body", "Please select a note first."));
            return;
        }

        String content = currentNote.getContent();
        if (content == null) {
            content = "";
        }

        int wordCount = countWords(content);

        String message = String.format(
            tr("readingtime.report.body",
                "Note: %s\n" +
                "Words: %,d\n\n" +
                "═══════════════════════════\n" +
                "READING TIME ESTIMATES\n" +
                "═══════════════════════════\n\n" +
                "Slow Reading (%d wpm):\n  %s\n\n" +
                "Average Reading (%d wpm):\n  %s\n\n" +
                "Speed Reading (%d wpm):\n  %s\n\n" +
                "Speaking Time (%d wpm):\n  %s"),
            currentNote.getTitle(),
            wordCount,
            SLOW_WPM, formatTime(calculateMinutes(wordCount, SLOW_WPM)),
            AVERAGE_WPM, formatTime(calculateMinutes(wordCount, AVERAGE_WPM)),
            FAST_WPM, formatTime(calculateMinutes(wordCount, FAST_WPM)),
            SPEAKING_WPM, formatTime(calculateMinutes(wordCount, SPEAKING_WPM))
        );

        showAlert(tr("readingtime.title", "Reading Time") + " - " + currentNote.getTitle(), null, message);
    }
    
    /**
     * Shows a quick estimate for the current note.
     */
    private void showQuickEstimate() {
        if (currentNote == null) {
            showAlert(tr("readingtime.title", "Reading Time"), tr("readingtime.alert.noNote.header", "No note selected"),
                    tr("readingtime.alert.noNote.body", "Please select a note first."));
            return;
        }

        String content = currentNote.getContent();
        int wordCount = content != null ? countWords(content) : 0;
        double minutes = calculateMinutes(wordCount, AVERAGE_WPM);

        String timeStr = formatTimeShort(minutes);
        String message = String.format(
            tr("readingtime.quickEstimate.body", "%s\n\n📖 %s read (%,d words)"),
            currentNote.getTitle(),
            timeStr,
            wordCount
        );

        showAlert(tr("readingtime.quickEstimate.title", "Quick Estimate"), null, message);
    }
    
    /**
     * Shows total reading time across all notes.
     */
    private void showAllNotesReadingTime() {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        int totalWords = 0;
        for (Note note : allNotes) {
            String content = note.getContent();
            if (content != null) {
                totalWords += countWords(content);
            }
        }
        
        String message = String.format(
            tr("readingtime.allNotes.body",
                "Total Notes: %,d\n" +
                "Total Words: %,d\n\n" +
                "═══════════════════════════\n" +
                "TOTAL READING TIME\n" +
                "═══════════════════════════\n\n" +
                "Average Reading (%d wpm):\n  %s\n\n" +
                "Speed Reading (%d wpm):\n  %s\n\n" +
                "If read aloud (%d wpm):\n  %s"),
            allNotes.size(),
            totalWords,
            AVERAGE_WPM, formatTime(calculateMinutes(totalWords, AVERAGE_WPM)),
            FAST_WPM, formatTime(calculateMinutes(totalWords, FAST_WPM)),
            SPEAKING_WPM, formatTime(calculateMinutes(totalWords, SPEAKING_WPM))
        );

        showAlert(tr("readingtime.allNotes.title", "Reading Time - All Notes"), null, message);
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
     * Calculates reading time in minutes.
     */
    private double calculateMinutes(int words, int wordsPerMinute) {
        return (double) words / wordsPerMinute;
    }
    
    /**
     * Formats time in a readable format (X hours Y minutes).
     */
    private String formatTime(double totalMinutes) {
        if (totalMinutes < 1) {
            int seconds = (int) Math.round(totalMinutes * 60);
            return seconds + " " + tr("readingtime.unit.seconds", "seconds");
        }

        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        int seconds = (int) Math.round((totalMinutes - Math.floor(totalMinutes)) * 60);

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(hours == 1 ? " " + tr("readingtime.unit.hour", "hour") + " " : " " + tr("readingtime.unit.hours", "hours") + " ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append(minutes == 1 ? " " + tr("readingtime.unit.minute", "minute") : " " + tr("readingtime.unit.minutes", "minutes"));
        }
        if (hours == 0 && minutes < 5 && seconds > 0) {
            sb.append(" ").append(seconds).append(" ").append(tr("readingtime.unit.seconds", "seconds"));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Formats time in a short format (Xm or Xh Ym).
     */
    private String formatTimeShort(double totalMinutes) {
        if (totalMinutes < 1) {
            return tr("readingtime.unit.lessThanMin", "< 1 min");
        }

        int hours = (int) (totalMinutes / 60);
        int minutes = (int) Math.ceil(totalMinutes % 60);

        if (hours > 0) {
            return String.format(tr("readingtime.unit.hoursMinutesShort", "%dh %dm"), hours, minutes);
        }
        return String.format(tr("readingtime.unit.minutesShort", "%d min"), minutes);
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
            com.example.jylos.ui.UiDialogs.show(alert);
        });
    }
}
