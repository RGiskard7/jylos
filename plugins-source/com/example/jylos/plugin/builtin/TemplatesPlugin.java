package com.example.jylos.plugin.builtin;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Templates Plugin - Create notes from predefined templates.
 * 
 * <p>This plugin provides:</p>
 * <ul>
 *   <li>Built-in templates for common note types</li>
 *   <li>Quick creation of structured notes</li>
 *   <li>Meeting notes, project plans, checklists, etc.</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public class TemplatesPlugin implements Plugin {
    
    private static final String ID = "templates";
    private static final String NAME = "Note Templates";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Create notes from predefined templates (meeting notes, checklists, etc.)";
    private static final String AUTHOR = "Jylos Team";
    
    private PluginContext context;
    private final Map<String, Template> templates = new LinkedHashMap<>();
    
    /**
     * Represents a note template.
     */
    public static class Template {
        private final String name;
        private final String description;
        private final String content;
        
        public Template(String name, String description, String content) {
            this.name = name;
            this.description = description;
            this.content = content;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getContent() { return content; }
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
        
        // Initialize built-in templates
        initializeTemplates();
        
        // Register main command
        context.registerCommand(
            "Templates: New from Template",
            "Create a new note from a template",
            "Ctrl+Shift+T",
            this::showTemplateSelector
        );
        
        // Register quick commands for common templates
        context.registerCommand(
            "Templates: Meeting Notes",
            "Create a new meeting notes document",
            null,
            () -> createFromTemplate("meeting")
        );
        
        context.registerCommand(
            "Templates: Project Plan",
            "Create a new project plan",
            null,
            () -> createFromTemplate("project")
        );
        
        context.registerCommand(
            "Templates: Weekly Review",
            "Create a weekly review document",
            null,
            () -> createFromTemplate("weekly-review")
        );
        
        context.registerCommand(
            "Templates: Checklist",
            "Create a new checklist",
            null,
            () -> createFromTemplate("checklist")
        );
        
        // Register menu items (dynamic plugin menu)
        context.registerMenuItem("Productivity", "New from Template...", "Ctrl+Shift+T", this::showTemplateSelector);
        context.addMenuSeparator("Productivity");
        context.registerMenuItem("Productivity", "Meeting Notes", () -> createFromTemplate("meeting"));
        context.registerMenuItem("Productivity", "Project Plan", () -> createFromTemplate("project"));
        context.registerMenuItem("Productivity", "Weekly Review", () -> createFromTemplate("weekly-review"));
        context.registerMenuItem("Productivity", "Checklist", () -> createFromTemplate("checklist"));
        
        context.log("Templates Plugin initialized with " + templates.size() + " templates");
    }
    
    @Override
    public void shutdown() {
        context.unregisterCommand("Templates: New from Template");
        context.unregisterCommand("Templates: Meeting Notes");
        context.unregisterCommand("Templates: Project Plan");
        context.unregisterCommand("Templates: Weekly Review");
        context.unregisterCommand("Templates: Checklist");
        context.log("Templates Plugin shutdown");
    }
    
    /**
     * Initialize built-in templates.
     */
    private void initializeTemplates() {
        // Meeting Notes Template
        templates.put("meeting", new Template(
            "Meeting Notes",
            "Template for taking meeting notes",
            "# Meeting Notes\n\n" +
            "**Date:** {{date}}\n" +
            "**Attendees:** \n\n" +
            "---\n\n" +
            "## Agenda\n\n" +
            "1. \n" +
            "2. \n" +
            "3. \n\n" +
            "## Discussion\n\n" +
            "\n\n" +
            "## Action Items\n\n" +
            "- [ ] \n" +
            "- [ ] \n" +
            "- [ ] \n\n" +
            "## Next Steps\n\n" +
            "\n\n" +
            "---\n" +
            "*Next meeting:*\n"
        ));
        
        // Project Plan Template
        templates.put("project", new Template(
            "Project Plan",
            "Template for project planning",
            "# Project: {{title}}\n\n" +
            "**Start Date:** {{date}}\n" +
            "**Target Date:** \n" +
            "**Status:** Planning\n\n" +
            "---\n\n" +
            "## Overview\n\n" +
            "*Brief description of the project*\n\n" +
            "## Goals\n\n" +
            "- [ ] \n" +
            "- [ ] \n" +
            "- [ ] \n\n" +
            "## Milestones\n\n" +
            "### Phase 1\n" +
            "- [ ] Task 1\n" +
            "- [ ] Task 2\n\n" +
            "### Phase 2\n" +
            "- [ ] Task 1\n" +
            "- [ ] Task 2\n\n" +
            "## Resources\n\n" +
            "- \n\n" +
            "## Notes\n\n" +
            "\n"
        ));
        
        // Weekly Review Template
        templates.put("weekly-review", new Template(
            "Weekly Review",
            "Template for weekly review and planning",
            "# Weekly Review - Week {{week}}\n\n" +
            "**Date:** {{date}}\n\n" +
            "---\n\n" +
            "## Accomplishments\n\n" +
            "*What did I accomplish this week?*\n\n" +
            "- \n" +
            "- \n" +
            "- \n\n" +
            "## Challenges\n\n" +
            "*What challenges did I face?*\n\n" +
            "- \n\n" +
            "## Learnings\n\n" +
            "*What did I learn?*\n\n" +
            "- \n\n" +
            "## Next Week's Focus\n\n" +
            "*Top priorities for next week:*\n\n" +
            "1. \n" +
            "2. \n" +
            "3. \n\n" +
            "## Goals Check\n\n" +
            "- [ ] Goal 1\n" +
            "- [ ] Goal 2\n" +
            "- [ ] Goal 3\n\n" +
            "## Notes\n\n" +
            "\n"
        ));
        
        // Checklist Template
        templates.put("checklist", new Template(
            "Checklist",
            "Simple checklist template",
            "# {{title}}\n\n" +
            "**Created:** {{date}}\n\n" +
            "---\n\n" +
            "## Tasks\n\n" +
            "- [ ] \n" +
            "- [ ] \n" +
            "- [ ] \n" +
            "- [ ] \n" +
            "- [ ] \n\n" +
            "## Notes\n\n" +
            "\n"
        ));
        
        // Cornell Notes Template
        templates.put("cornell", new Template(
            "Cornell Notes",
            "Cornell note-taking method template",
            "# {{title}}\n\n" +
            "**Date:** {{date}}\n" +
            "**Topic:** \n\n" +
            "---\n\n" +
            "## Cues / Questions\n\n" +
            "*Key terms and questions*\n\n" +
            "- \n\n" +
            "---\n\n" +
            "## Notes\n\n" +
            "*Main notes during lecture/reading*\n\n" +
            "\n\n" +
            "---\n\n" +
            "## Summary\n\n" +
            "*Summarize the main points in your own words*\n\n" +
            "\n"
        ));
        
        // Blog Post Template
        templates.put("blog", new Template(
            "Blog Post",
            "Template for writing blog posts",
            "# {{title}}\n\n" +
            "**Draft Date:** {{date}}\n" +
            "**Status:** Draft\n" +
            "**Tags:** \n\n" +
            "---\n\n" +
            "## Introduction\n\n" +
            "*Hook your reader*\n\n" +
            "\n\n" +
            "## Main Content\n\n" +
            "### Section 1\n\n" +
            "\n\n" +
            "### Section 2\n\n" +
            "\n\n" +
            "### Section 3\n\n" +
            "\n\n" +
            "## Conclusion\n\n" +
            "*Call to action*\n\n" +
            "\n\n" +
            "---\n\n" +
            "## Meta\n\n" +
            "- [ ] Proofread\n" +
            "- [ ] Add images\n" +
            "- [ ] SEO optimization\n" +
            "- [ ] Schedule publication\n"
        ));
        
        // Bug Report Template
        templates.put("bug-report", new Template(
            "Bug Report",
            "Template for reporting software bugs",
            "# Bug: {{title}}\n\n" +
            "**Reported:** {{date}}\n" +
            "**Severity:** Medium\n" +
            "**Status:** Open\n\n" +
            "---\n\n" +
            "## Description\n\n" +
            "*Brief description of the bug*\n\n" +
            "\n\n" +
            "## Steps to Reproduce\n\n" +
            "1. \n" +
            "2. \n" +
            "3. \n\n" +
            "## Expected Behavior\n\n" +
            "\n\n" +
            "## Actual Behavior\n\n" +
            "\n\n" +
            "## Environment\n\n" +
            "- OS: \n" +
            "- Version: \n" +
            "- Browser: \n\n" +
            "## Screenshots\n\n" +
            "\n\n" +
            "## Additional Notes\n\n" +
            "\n"
        ));
    }
    
    /**
     * Shows the template selector dialog.
     */
    private void showTemplateSelector() {
        Platform.runLater(() -> {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("New from Template");
            dialog.setHeaderText("Select a template to create a new note");
            
            ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            
            ListView<String> templateList = new ListView<>();
            templateList.setPrefHeight(200);
            
            for (Map.Entry<String, Template> entry : templates.entrySet()) {
                templateList.getItems().add(entry.getValue().getName() + " - " + entry.getValue().getDescription());
            }
            
            templateList.getSelectionModel().selectFirst();
            
            content.getChildren().addAll(
                new Label("Available Templates:"),
                templateList
            );
            
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefSize(400, 300);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == createButton) {
                    int selectedIndex = templateList.getSelectionModel().getSelectedIndex();
                    if (selectedIndex >= 0) {
                        String[] keys = templates.keySet().toArray(new String[0]);
                        return keys[selectedIndex];
                    }
                }
                return null;
            });
            
            com.example.jylos.ui.UiDialogs.show(dialog).ifPresent(this::createFromTemplate);
        });
    }
    
    /**
     * Creates a note from a template.
     */
    private void createFromTemplate(String templateId) {
        Template template = templates.get(templateId);
        if (template == null) {
            context.showError("Template Error", "Template not found: " + templateId);
            return;
        }
        
        Platform.runLater(() -> {
            // Ask for title
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("New " + template.getName());
            dialog.setHeaderText("Enter a title for your new note");
            
            ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));
            
            TextField titleField = new TextField();
            titleField.setPromptText("Note title");
            titleField.setText(template.getName());
            
            grid.add(new Label("Title:"), 0, 0);
            grid.add(titleField, 1, 0);
            
            dialog.getDialogPane().setContent(grid);
            
            Platform.runLater(titleField::requestFocus);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == createButton) {
                    return titleField.getText();
                }
                return null;
            });
            
            com.example.jylos.ui.UiDialogs.show(dialog).ifPresent(title -> {
                if (title != null && !title.trim().isEmpty()) {
                    createNote(title.trim(), template);
                }
            });
        });
    }
    
    /**
     * Creates the actual note from template.
     */
    private void createNote(String title, Template template) {
        try {
            // Process template content
            String content = processTemplateContent(template.getContent(), title);
            
            // Create the note
            Note note = context.getNoteService().createNote(title, content);
            
            // Open it in the editor
            context.requestOpenNote(note);
            context.requestRefreshNotes();
            
            context.log("Created note from template '" + template.getName() + "': " + title);
        } catch (Exception e) {
            context.logError("Failed to create note from template", e);
            context.showError("Template Error", "Failed to create note: " + e.getMessage());
        }
    }
    
    /**
     * Processes template content, replacing placeholders.
     */
    private String processTemplateContent(String content, String title) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        return content
            .replace("{{title}}", title)
            .replace("{{date}}", today.format(dateFormatter))
            .replace("{{week}}", String.valueOf(today.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())));
    }
}
