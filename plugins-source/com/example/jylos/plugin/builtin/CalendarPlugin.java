package com.example.jylos.plugin.builtin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Calendar Plugin - Shows a mini calendar in the side panel.
 * 
 * <p>This plugin demonstrates UI modification capabilities (Obsidian-style):</p>
 * <ul>
 *   <li>Adds a collapsible calendar widget to the right sidebar</li>
 *   <li>Highlights days that have daily notes</li>
 *   <li>Click on a date to open/create a daily note</li>
 *   <li>Navigate between months</li>
 * </ul>
 * 
 * <h2>Commands:</h2>
 * <ul>
 *   <li><b>Calendar: Show/Hide Panel</b> - Toggle calendar visibility</li>
 *   <li><b>Calendar: Go to Today</b> - Navigate to current month</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public class CalendarPlugin implements Plugin {
    
    private static final String ID = "calendar";
    private static final String NAME = "Calendar";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Mini calendar in sidebar with daily notes integration";
    private static final String AUTHOR = "Jylos Team";
    
    private static final String PANEL_ID = "calendar-panel";
    private static final DateTimeFormatter DAILY_NOTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private PluginContext context;
    private YearMonth currentMonth;
    private VBox calendarContent;
    private Label monthLabel;
    private GridPane calendarGrid;
    private Set<LocalDate> datesWithNotes = new HashSet<>();
    
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
        this.currentMonth = YearMonth.now();
        
        // Register commands
        context.registerCommand(
            "Calendar: Show/Hide Panel",
            "Toggle calendar panel visibility",
            "Ctrl+Shift+C",
            this::toggleCalendarPanel
        );
        
        context.registerCommand(
            "Calendar: Go to Today",
            "Navigate calendar to current month",
            null,
            this::goToToday
        );
        
        context.registerCommand(
            "Calendar: Refresh",
            "Refresh calendar to show notes with dates",
            null,
            this::refreshCalendar
        );
        
        // Register menu items
        context.registerMenuItem("Calendar", "Show/Hide Calendar", "Ctrl+Shift+C", this::toggleCalendarPanel);
        context.registerMenuItem("Calendar", "Go to Today", this::goToToday);
        context.registerMenuItem("Calendar", "Refresh", this::refreshCalendar);
        
        // Create and register the side panel
        Platform.runLater(() -> {
            createCalendarPanel();
            context.registerSidePanel(PANEL_ID, "Calendar", calendarContent, "fth-calendar");
        });
        
        context.log("Calendar Plugin initialized");
    }
    
    @Override
    public void shutdown() {
        context.unregisterCommand("Calendar: Show/Hide Panel");
        context.unregisterCommand("Calendar: Go to Today");
        context.unregisterCommand("Calendar: Refresh");
        context.removeSidePanel(PANEL_ID);
        context.log("Calendar Plugin shutdown");
    }
    
    /**
     * Creates the calendar panel UI.
     */
    private void createCalendarPanel() {
        calendarContent = new VBox();
        calendarContent.setSpacing(8);
        calendarContent.setPadding(new Insets(8));
        calendarContent.setStyle("-fx-background-color: transparent;");
        
        // Navigation header
        HBox navHeader = new HBox();
        navHeader.setAlignment(Pos.CENTER);
        navHeader.setSpacing(8);
        
        Button prevBtn = new Button();
        prevBtn.getStyleClass().addAll("icon-btn-small", "calendar-nav-btn");
        FontIcon prevIcon = new FontIcon("fth-chevron-left");
        prevIcon.setIconSize(12);
        prevBtn.setGraphic(prevIcon);
        prevBtn.setOnAction(e -> navigateMonth(-1));
        
        monthLabel = new Label();
        monthLabel.getStyleClass().add("calendar-month-label");
        HBox.setHgrow(monthLabel, Priority.ALWAYS);
        monthLabel.setMaxWidth(Double.MAX_VALUE);
        monthLabel.setAlignment(Pos.CENTER);
        
        Button nextBtn = new Button();
        nextBtn.getStyleClass().addAll("icon-btn-small", "calendar-nav-btn");
        FontIcon nextIcon = new FontIcon("fth-chevron-right");
        nextIcon.setIconSize(12);
        nextBtn.setGraphic(nextIcon);
        nextBtn.setOnAction(e -> navigateMonth(1));
        
        Button todayBtn = new Button();
        todayBtn.getStyleClass().addAll("icon-btn-small", "calendar-today-btn");
        FontIcon todayIcon = new FontIcon("fth-target");
        todayIcon.setIconSize(12);
        todayBtn.setGraphic(todayIcon);
        todayBtn.setOnAction(e -> goToToday());
        
        navHeader.getChildren().addAll(prevBtn, monthLabel, nextBtn, todayBtn);
        
        // Calendar grid
        calendarGrid = new GridPane();
        calendarGrid.setHgap(2);
        calendarGrid.setVgap(2);
        calendarGrid.setAlignment(Pos.CENTER);
        
        calendarContent.getChildren().addAll(navHeader, calendarGrid);
        
        // Initial render
        updateCalendar();
    }
    
    /**
     * Updates the calendar display.
     */
    private void updateCalendar() {
        if (calendarGrid == null || monthLabel == null) return;
        
        // Update month label
        String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        monthLabel.setText(monthName + " " + currentMonth.getYear());
        
        // Clear grid
        calendarGrid.getChildren().clear();
        
        // Add day headers
        String[] dayNames = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(dayNames[i]);
            dayLabel.getStyleClass().add("calendar-day-name");
            calendarGrid.add(dayLabel, i, 0);
        }
        
        // Load dates with notes
        loadDatesWithNotes();
        
        // Calculate first day position
        LocalDate firstOfMonth = currentMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue(); // Monday = 1
        int daysInMonth = currentMonth.lengthOfMonth();
        
        LocalDate today = LocalDate.now();
        
        // Add day buttons
        int row = 1;
        int col = dayOfWeek - 1;
        
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            Button dayBtn = createDayButton(date, today);
            calendarGrid.add(dayBtn, col, row);
            
            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
    }
    
    /**
     * Creates a day button for the calendar.
     */
    private Button createDayButton(LocalDate date, LocalDate today) {
        Button btn = new Button(String.valueOf(date.getDayOfMonth()));
        btn.getStyleClass().add("calendar-day");
        
        boolean isToday = date.equals(today);
        boolean hasNote = datesWithNotes.contains(date);
        boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || 
                           date.getDayOfWeek() == DayOfWeek.SUNDAY;
        
        if (isToday) btn.getStyleClass().add("calendar-today");
        if (hasNote) btn.getStyleClass().add("calendar-has-note");
        if (isWeekend) btn.getStyleClass().add("calendar-weekend");
        
        // Click to open/create daily note
        btn.setOnAction(e -> openDailyNote(date));
        
        return btn;
    }
    
    /**
     * Loads dates that have corresponding daily notes.
     */
    private void loadDatesWithNotes() {
        datesWithNotes.clear();
        try {
            List<Note> allNotes = context.getNoteService().getAllNotes();
            for (Note note : allNotes) {
                String title = note.getTitle();
                if (title != null && title.startsWith("Daily Note - ")) {
                    try {
                        String dateStr = title.substring("Daily Note - ".length());
                        LocalDate date = LocalDate.parse(dateStr, DAILY_NOTE_FORMAT);
                        datesWithNotes.add(date);
                    } catch (Exception ignored) {
                        // Not a valid daily note format
                    }
                }
            }
        } catch (Exception e) {
            context.logError("Failed to load dates with notes", e);
        }
    }
    
    /**
     * Opens or creates a daily note for the specified date.
     */
    private void openDailyNote(LocalDate date) {
        String noteTitle = "Daily Note - " + date.format(DAILY_NOTE_FORMAT);
        
        try {
            List<Note> allNotes = context.getNoteService().getAllNotes();
            Optional<Note> existingNote = allNotes.stream()
                .filter(n -> noteTitle.equals(n.getTitle()))
                .findFirst();
            
            if (existingNote.isPresent()) {
                context.requestOpenNote(existingNote.get());
                context.log("Opened daily note: " + noteTitle);
            } else {
                // Create new daily note
                String content = generateDailyNoteContent(date);
                Note newNote = context.getNoteService().createNote(noteTitle, content);
                context.requestOpenNote(newNote);
                context.requestRefreshNotes();
                
                // Update calendar to show new note
                datesWithNotes.add(date);
                Platform.runLater(this::updateCalendar);
                
                context.log("Created daily note: " + noteTitle);
            }
        } catch (Exception e) {
            context.logError("Failed to open/create daily note", e);
            context.showError("Calendar Plugin", "Failed to open daily note: " + e.getMessage());
        }
    }
    
    /**
     * Generates content for a new daily note.
     */
    private String generateDailyNoteContent(LocalDate date) {
        DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        return String.format(
            "# %s\n\n" +
            "## Tasks\n\n" +
            "- [ ] \n\n" +
            "## Notes\n\n" +
            "\n\n" +
            "## Reflection\n\n" +
            "\n\n" +
            "---\n" +
            "*Created via Calendar Plugin*\n",
            date.format(displayFormat)
        );
    }
    
    /**
     * Navigates to a different month.
     */
    private void navigateMonth(int delta) {
        currentMonth = currentMonth.plusMonths(delta);
        Platform.runLater(this::updateCalendar);
    }
    
    /**
     * Goes to the current month.
     */
    private void goToToday() {
        currentMonth = YearMonth.now();
        Platform.runLater(this::updateCalendar);
    }
    
    /**
     * Refreshes the calendar.
     */
    private void refreshCalendar() {
        Platform.runLater(this::updateCalendar);
        context.showInfo("Calendar", null, "Calendar refreshed!");
    }
    
    /**
     * Toggles calendar panel visibility.
     */
    private void toggleCalendarPanel() {
        boolean isVisible = context.isPluginPanelsVisible();
        context.setPluginPanelsVisible(!isVisible);
    }
}
