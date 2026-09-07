package com.example.jylos.plugin.builtin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginI18n;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

/**
 * AI Plugin - Integrates AI capabilities into Jylos.
 * 
 * <p>This plugin demonstrates how to integrate AI services (OpenAI, Anthropic, etc.)
 * through plugins. It provides:</p>
 * <ul>
 *   <li>Summarize notes using AI</li>
 *   <li>Translate notes</li>
 *   <li>Improve writing (grammar, style)</li>
 *   <li>Generate content</li>
 *   <li>Answer questions about notes</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This is a demonstration plugin. To use it, you need:</p>
 * <ul>
 *   <li>An API key from an AI service (OpenAI, Anthropic, etc.)</li>
 *   <li>Configure the API endpoint and key</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public class AIPlugin implements Plugin {
    
    private static final String ID = "ai-assistant";
    private static final String NAME = "AI Assistant";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "AI-powered features: summarize, translate, improve writing";
    private static final String AUTHOR = "Jylos Team";
    
    private PluginContext context;
    private HttpClient httpClient;
    private java.util.prefs.Preferences preferences;
    
    // Progress dialog reference (to close it when done)
    private Stage progressStage;
    
    // Configuration (stored in Preferences)
    private String apiKey = "";
    private String apiEndpoint = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-3.5-turbo";
    private String provider = "OpenAI";
    
    // Predefined providers
    private static final ProviderInfo[] PROVIDERS = {
        new ProviderInfo("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-3.5-turbo"),
        new ProviderInfo("OpenAI (GPT-4)", "https://api.openai.com/v1/chat/completions", "gpt-4"),
        new ProviderInfo("Anthropic (Claude)", "https://api.anthropic.com/v1/messages", "claude-3-sonnet-20240229"),
        new ProviderInfo("Local (Ollama)", "http://localhost:11434/api/chat", "llama2"),
        new ProviderInfo("Custom", "", "")
    };
    
    private static class ProviderInfo {
        final String name;
        final String endpoint;
        final String defaultModel;
        
        ProviderInfo(String name, String endpoint, String defaultModel) {
            this.name = name;
            this.endpoint = endpoint;
            this.defaultModel = defaultModel;
        }
    }
    
    // Loaded once, lazily — getDescription() can be called by the Plugin Manager before
    // initialize() ever runs, so this cannot wait for that.
    private static java.util.ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(AIPlugin.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDescription() { return tr("ai.plugin.description", DESCRIPTION); }

    @Override
    public String getAuthor() { return AUTHOR; }

    @Override
    public void initialize(PluginContext context) {
        try {
            this.context = context;

            // Initialize Preferences
            preferences = context.getPluginPreferences().node("ai-plugin-config");

            // Load saved configuration
            loadConfiguration();

            // Initialize HTTP client
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

            // Register commands
        context.registerCommand(
            "AI: Summarize Note",
            tr("ai.command.summarize.description", "Generate an AI summary of the current note"),
            "Ctrl+Shift+S",
            this::summarizeNote
        );

        context.registerCommand(
            "AI: Translate Note",
            tr("ai.command.translate.description", "Translate note content to another language"),
            null,
            this::translateNote
        );

        context.registerCommand(
            "AI: Improve Writing",
            tr("ai.command.improve.description", "Improve grammar and style of note content"),
            null,
            this::improveWriting
        );

        context.registerCommand(
            "AI: Generate Content",
            tr("ai.command.generate.description", "Generate new content based on a prompt"),
            null,
            this::generateContent
        );

        context.registerCommand(
            "AI: Configure API",
            tr("ai.command.configure.description", "Configure AI API key and endpoint"),
            null,
            this::configureAPI
        );

        // Register menu items (dynamic plugin menu)
        context.registerMenuItem(tr("ai.menuCategory", "AI"), tr("ai.menu.configure", "Configure API..."), this::configureAPI);
        context.addMenuSeparator(tr("ai.menuCategory", "AI"));
        context.registerMenuItem(tr("ai.menuCategory", "AI"), tr("ai.menu.summarize", "Summarize Note"), "Ctrl+Shift+S", this::summarizeNote);
        context.registerMenuItem(tr("ai.menuCategory", "AI"), tr("ai.menu.translate", "Translate Note..."), this::translateNote);
        context.registerMenuItem(tr("ai.menuCategory", "AI"), tr("ai.menu.improve", "Improve Writing"), this::improveWriting);
        context.registerMenuItem(tr("ai.menuCategory", "AI"), tr("ai.menu.generateContent", "Generate Content..."), this::generateContent);

        context.log("AI Plugin initialized (API key required for functionality)");
        } catch (Exception e) {
            context.logError("Failed to initialize AI Plugin", e);
            throw e; // Re-throw to let PluginManager handle it
        }
    }
    
    @Override
    public void shutdown() {
        context.unregisterCommand("AI: Summarize Note");
        context.unregisterCommand("AI: Translate Note");
        context.unregisterCommand("AI: Improve Writing");
        context.unregisterCommand("AI: Generate Content");
        context.unregisterCommand("AI: Configure API");
        context.log("AI Plugin shutdown");
    }
    
    /**
     * Summarizes a note using AI.
     */
    private void summarizeNote() {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        if (allNotes.isEmpty()) {
            context.showInfo(tr("ai.title", "AI Assistant"), tr("ai.noNotes.header", "No Notes"),
                    tr("ai.noNotes.summarize.body", "Create a note first to summarize."));
            return;
        }

        if (!checkAPIKey()) {
            return;
        }

        Platform.runLater(() -> {
            Note selectedNote = showNoteSelector(tr("ai.selector.summarize.title", "Summarize Note"),
                    tr("ai.selector.summarize.header", "Select a note to summarize:"));
            if (selectedNote == null) return;

            String content = selectedNote.getContent();
            if (content == null || content.trim().isEmpty()) {
                context.showInfo(tr("ai.title", "AI Assistant"), tr("ai.emptyNote.header", "Empty Note"),
                        tr("ai.emptyNote.body", "The selected note has no content."));
                return;
            }

            // Show progress dialog
            showProgressDialog(tr("ai.progress.summarize.title", "Summarizing..."),
                    tr("ai.progress.summarize.body", "Please wait while AI processes your note."));

            // Call AI API in background
            new Thread(() -> {
                try {
                    String prompt = "Summarize the following text in 2-3 sentences:\n\n" + content;
                    String summary = callAI(prompt);

                    Platform.runLater(() -> {
                        closeProgressDialog();
                        showResultDialog(tr("ai.result.summary.title", "Summary"), selectedNote.getTitle(), summary,
                            () -> createSummaryNote(selectedNote, summary));
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        closeProgressDialog();
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = tr("ai.error.unknown", "Unknown error occurred");
                        }
                        context.showError(tr("ai.error.title", "AI Error"), tr("ai.error.summarizeFailed", "Failed to summarize: %s").formatted(errorMsg));
                        context.logError("AI summarization failed", e);
                    });
                }
            }).start();
        });
    }
    
    /**
     * Translates a note using AI.
     */
    private void translateNote() {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        if (allNotes.isEmpty()) {
            context.showInfo(tr("ai.title", "AI Assistant"), tr("ai.noNotes.header", "No Notes"),
                    tr("ai.noNotes.translate.body", "Create a note first to translate."));
            return;
        }

        if (!checkAPIKey()) {
            return;
        }

        Platform.runLater(() -> {
            Note selectedNote = showNoteSelector(tr("ai.selector.translate.title", "Translate Note"),
                    tr("ai.selector.translate.header", "Select a note to translate:"));
            if (selectedNote == null) return;

            // Language selector — the combo's items stay the canonical English names (used
            // as-is in the AI prompt and in the resulting note's title); only their on-screen
            // rendering is localized, via the converter below.
            Dialog<String> langDialog = new Dialog<>();
            langDialog.setTitle(tr("ai.selector.translate.title", "Translate Note"));
            langDialog.setHeaderText(tr("ai.dialog.selectLanguage.header", "Select target language:"));

            ComboBox<String> langCombo = new ComboBox<>();
            langCombo.getItems().addAll("Spanish", "French", "German", "Italian", "Portuguese",
                                       "Chinese", "Japanese", "Korean", "Russian", "Arabic");
            langCombo.setValue("Spanish");
            langCombo.setConverter(new StringConverter<String>() {
                @Override
                public String toString(String lang) {
                    if (lang == null) return "";
                    return tr("ai.lang." + lang.toLowerCase(java.util.Locale.ROOT), lang);
                }

                @Override
                public String fromString(String string) {
                    return string;
                }
            });

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            grid.add(new Label(tr("ai.field.language.label", "Language:")), 0, 0);
            grid.add(langCombo, 1, 0);
            
            langDialog.getDialogPane().setContent(grid);
            langDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            langDialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    return langCombo.getValue();
                }
                return null;
            });
            
            Optional<String> langResult = context.showThemed(langDialog);
            if (langResult.isEmpty()) return;
            
            String targetLang = langResult.get();
            String content = selectedNote.getContent();
            
            if (content == null || content.trim().isEmpty()) {
                context.showInfo(tr("ai.title", "AI Assistant"), tr("ai.emptyNote.header", "Empty Note"),
                        tr("ai.emptyNote.body", "The selected note has no content."));
                return;
            }

            showProgressDialog(tr("ai.progress.translate.title", "Translating..."),
                    tr("ai.progress.translate.body", "Please wait while AI translates your note."));

            new Thread(() -> {
                try {
                    String prompt = "Translate the following text to " + targetLang + ":\n\n" + content;
                    String translation = callAI(prompt);

                    Platform.runLater(() -> {
                        closeProgressDialog();
                        String newTitle = selectedNote.getTitle() + " (" + targetLang + ")";
                        showResultDialog(tr("ai.result.translation.title", "Translation"), newTitle, translation,
                            () -> createTranslatedNote(newTitle, translation));
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        closeProgressDialog();
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = tr("ai.error.unknown", "Unknown error occurred");
                        }
                        context.showError(tr("ai.error.title", "AI Error"), tr("ai.error.translateFailed", "Failed to translate: %s").formatted(errorMsg));
                        context.logError("AI translation failed", e);
                    });
                }
            }).start();
        });
    }
    
    /**
     * Improves writing using AI.
     */
    private void improveWriting() {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        if (allNotes.isEmpty()) {
            context.showInfo(tr("ai.title", "AI Assistant"), tr("ai.noNotes.header", "No Notes"),
                    tr("ai.noNotes.improve.body", "Create a note first."));
            return;
        }

        if (!checkAPIKey()) {
            return;
        }

        Platform.runLater(() -> {
            Note selectedNote = showNoteSelector(tr("ai.selector.improve.title", "Improve Writing"),
                    tr("ai.selector.improve.header", "Select a note to improve:"));
            if (selectedNote == null) return;

            String content = selectedNote.getContent();
            if (content == null || content.trim().isEmpty()) {
                context.showInfo(tr("ai.title", "AI Assistant"), tr("ai.emptyNote.header", "Empty Note"),
                        tr("ai.emptyNote.body", "The selected note has no content."));
                return;
            }

            showProgressDialog(tr("ai.progress.improve.title", "Improving..."),
                    tr("ai.progress.improve.body", "Please wait while AI improves your writing."));

            new Thread(() -> {
                try {
                    String prompt = "Improve the grammar, style, and clarity of the following text. " +
                                   "Return only the improved version without explanations:\n\n" + content;
                    String improved = callAI(prompt);

                    Platform.runLater(() -> {
                        closeProgressDialog();
                        showResultDialog(tr("ai.result.improve.title", "Improved Writing"), selectedNote.getTitle(), improved,
                            () -> updateNoteContent(selectedNote, improved));
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        closeProgressDialog();
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = tr("ai.error.unknown", "Unknown error occurred");
                        }
                        context.showError(tr("ai.error.title", "AI Error"), tr("ai.error.improveFailed", "Failed to improve writing: %s").formatted(errorMsg));
                        context.logError("AI improvement failed", e);
                    });
                }
            }).start();
        });
    }
    
    /**
     * Generates content using AI.
     */
    private void generateContent() {
        if (!checkAPIKey()) {
            return;
        }
        
        Platform.runLater(() -> {
            Dialog<String> promptDialog = new Dialog<>();
            promptDialog.setTitle(tr("ai.generate.title", "Generate Content"));
            promptDialog.setHeaderText(tr("ai.generate.header", "Enter a prompt for AI to generate content:"));

            TextArea promptField = new TextArea();
            promptField.setPromptText(tr("ai.generate.prompt.placeholder", "e.g., 'Write a blog post about productivity'"));
            promptField.setPrefRowCount(5);
            promptField.setPrefColumnCount(40);
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            content.getChildren().add(promptField);
            
            promptDialog.getDialogPane().setContent(content);
            promptDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            promptDialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    return promptField.getText();
                }
                return null;
            });
            
            Optional<String> promptResult = context.showThemed(promptDialog);
            if (promptResult.isEmpty() || promptResult.get().trim().isEmpty()) return;
            
            String prompt = promptResult.get();
            showProgressDialog(tr("ai.progress.generate.title", "Generating..."),
                    tr("ai.progress.generate.body", "Please wait while AI generates content."));

            new Thread(() -> {
                try {
                    String generated = callAI(prompt);

                    Platform.runLater(() -> {
                        closeProgressDialog();
                        showResultDialog(tr("ai.result.generate.title", "Generated Content"), tr("ai.result.generate.header", "AI Generated"), generated,
                            () -> createGeneratedNote(generated));
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        closeProgressDialog();
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = tr("ai.error.unknown", "Unknown error occurred");
                        }
                        context.showError(tr("ai.error.title", "AI Error"), tr("ai.error.generateFailed", "Failed to generate content: %s").formatted(errorMsg));
                        context.logError("AI generation failed", e);
                    });
                }
            }).start();
        });
    }
    
    /**
     * Loads configuration from Preferences.
     */
    private void loadConfiguration() {
        try {
            apiKey = preferences.get("apiKey", "");
            apiEndpoint = preferences.get("apiEndpoint", "https://api.openai.com/v1/chat/completions");
            model = preferences.get("model", "gpt-3.5-turbo");
            provider = preferences.get("provider", "OpenAI");
        } catch (Exception e) {
            // If preferences fail, use defaults
            apiKey = "";
            apiEndpoint = "https://api.openai.com/v1/chat/completions";
            model = "gpt-3.5-turbo";
            provider = "OpenAI";
            context.logError("Failed to load AI configuration, using defaults", e);
        }
    }
    
    /**
     * Saves configuration to Preferences.
     */
    private void saveConfiguration() {
        preferences.put("apiKey", apiKey);
        preferences.put("apiEndpoint", apiEndpoint);
        preferences.put("model", model);
        preferences.put("provider", provider);
        try {
            preferences.flush();
            context.log("AI configuration saved");
        } catch (java.util.prefs.BackingStoreException e) {
            context.logError("Failed to save AI configuration", e);
        }
    }
    
    /**
     * Configures the AI API key, provider, and endpoint.
     */
    private void configureAPI() {
        Platform.runLater(() -> {
            Dialog<Void> configDialog = new Dialog<>();
            configDialog.setTitle(tr("ai.configure.title", "Configure AI Assistant"));
            configDialog.setHeaderText(tr("ai.configure.header", "Configure your AI provider and API key:"));
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            // Provider selector
            ComboBox<String> providerCombo = new ComboBox<>();
            for (ProviderInfo p : PROVIDERS) {
                providerCombo.getItems().add(p.name);
            }
            providerCombo.setValue(provider);
            providerCombo.setPrefWidth(300);
            
            // API Key field
            TextField keyField = new TextField(apiKey);
            keyField.setPromptText(tr("ai.field.apiKey.prompt", "Enter your API key"));
            keyField.setPrefWidth(300);

            // Endpoint field (updates when provider changes)
            TextField endpointField = new TextField(apiEndpoint);
            endpointField.setPromptText(tr("ai.field.endpoint.prompt", "API Endpoint URL"));
            endpointField.setPrefWidth(300);

            // Model field (updates when provider changes)
            TextField modelField = new TextField(model);
            modelField.setPromptText(tr("ai.field.model.prompt", "Model name"));
            modelField.setPrefWidth(300);
            
            // Update endpoint and model when provider changes
            providerCombo.setOnAction(e -> {
                String selectedProvider = providerCombo.getValue();
                for (ProviderInfo p : PROVIDERS) {
                    if (p.name.equals(selectedProvider)) {
                        if (!p.endpoint.isEmpty()) {
                            endpointField.setText(p.endpoint);
                        }
                        if (!p.defaultModel.isEmpty()) {
                            modelField.setText(p.defaultModel);
                        }
                        break;
                    }
                }
            });
            
            // Labels and layout
            grid.add(new Label(tr("ai.field.provider.label", "Provider:")), 0, 0);
            grid.add(providerCombo, 1, 0);
            grid.add(new Label(tr("ai.field.apiKey.label", "API Key:")), 0, 1);
            grid.add(keyField, 1, 1);
            grid.add(new Label(tr("ai.field.endpoint.label", "Endpoint:")), 0, 2);
            grid.add(endpointField, 1, 2);
            grid.add(new Label(tr("ai.field.model.label", "Model:")), 0, 3);
            grid.add(modelField, 1, 3);

            // Help text
            Label helpLabel = new Label(tr("ai.configure.help", "Tip: Select a provider to auto-fill endpoint and model. "
                                       + "For custom providers, enter your own endpoint."));
            helpLabel.setWrapText(true);
            helpLabel.getStyleClass().add("dialog-hint-label");
            grid.add(helpLabel, 0, 4, 2, 1);
            
            configDialog.getDialogPane().setContent(grid);
            configDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            configDialog.getDialogPane().setPrefSize(500, 300);
            
            configDialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    provider = providerCombo.getValue();
                    apiKey = keyField.getText().trim();
                    apiEndpoint = endpointField.getText().trim();
                    model = modelField.getText().trim();
                    
                    if (apiKey.isEmpty()) {
                        context.showError(tr("ai.configError.title", "Configuration Error"), tr("ai.configError.emptyKey", "API key cannot be empty."));
                        return null;
                    }

                    if (apiEndpoint.isEmpty()) {
                        context.showError(tr("ai.configError.title", "Configuration Error"), tr("ai.configError.emptyEndpoint", "API endpoint cannot be empty."));
                        return null;
                    }

                    saveConfiguration();
                    context.showInfo(tr("ai.configSaved.title", "AI Configuration"), tr("ai.configSaved.header", "Configuration Saved"),
                        tr("ai.configSaved.body", "AI Assistant is now configured and ready to use!"));
                    context.log("AI API configured - Provider: " + provider + 
                               ", Endpoint: " + apiEndpoint + ", Model: " + model);
                    return null;
                }
                return null;
            });
            
            context.showThemed(configDialog);
        });
    }
    
    /**
     * Calls the AI API with a prompt.
     */
    private String callAI(String prompt) throws IOException, InterruptedException {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("API key not configured");
        }
        
        // Build JSON request (OpenAI-compatible format)
        String jsonBody = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":1000}",
            model, escapeJson(prompt)
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(60))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("API request failed: " + response.statusCode() + " - " + response.body());
        }
        
        // Parse response (simplified - real implementation should use JSON parser)
        String body = response.body();
        
        // OpenAI response structure: {"choices":[{"message":{"content":"..."}}]}
        // Try to find content in the nested structure
        String content = extractContentFromJson(body);
        if (content != null && !content.isEmpty()) {
            return content;
        }
        
        throw new IOException("Failed to parse AI response. Response body: " + 
            (body.length() > 200 ? body.substring(0, 200) + "..." : body));
    }
    
    /**
     * Extracts content from JSON response.
     * Handles OpenAI format: {"choices":[{"message":{"content":"..."}}]}
     * Also handles Anthropic and other formats.
     */
    private String extractContentFromJson(String json) {
        try {
            // Try OpenAI format: choices[0].message.content
            int choicesStart = json.indexOf("\"choices\"");
            if (choicesStart >= 0) {
                int messageStart = json.indexOf("\"message\"", choicesStart);
                if (messageStart >= 0) {
                    int contentStart = json.indexOf("\"content\"", messageStart);
                    if (contentStart >= 0) {
                        // Find the content value (can be string or object)
                        int colonIndex = json.indexOf(":", contentStart);
                        if (colonIndex >= 0) {
                            int valueStart = colonIndex + 1;
                            // Skip whitespace
                            while (valueStart < json.length() && 
                                   Character.isWhitespace(json.charAt(valueStart))) {
                                valueStart++;
                            }
                            
                            if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                                // String value
                                valueStart++; // Skip opening quote
                                int valueEnd = valueStart;
                                boolean escaped = false;
                                while (valueEnd < json.length()) {
                                    char c = json.charAt(valueEnd);
                                    if (escaped) {
                                        escaped = false;
                                    } else if (c == '\\') {
                                        escaped = true;
                                    } else if (c == '"') {
                                        break;
                                    }
                                    valueEnd++;
                                }
                                if (valueEnd < json.length()) {
                                    return unescapeJson(json.substring(valueStart, valueEnd));
                                }
                            } else {
                                // Try to find content as object or array
                                // For simplicity, look for text pattern
                                int textStart = json.indexOf("\"", valueStart);
                                if (textStart > valueStart) {
                                    int textEnd = json.indexOf("\"", textStart + 1);
                                    if (textEnd > textStart) {
                                        return unescapeJson(json.substring(textStart + 1, textEnd));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Try Anthropic format: content[0].text
            int contentArrayStart = json.indexOf("\"content\"");
            if (contentArrayStart >= 0) {
                int arrayStart = json.indexOf("[", contentArrayStart);
                if (arrayStart >= 0) {
                    int textStart = json.indexOf("\"text\"", arrayStart);
                    if (textStart >= 0) {
                        int colonIndex = json.indexOf(":", textStart);
                        if (colonIndex >= 0) {
                            int valueStart = colonIndex + 1;
                            while (valueStart < json.length() && 
                                   Character.isWhitespace(json.charAt(valueStart))) {
                                valueStart++;
                            }
                            if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                                valueStart++;
                                int valueEnd = valueStart;
                                boolean escaped = false;
                                while (valueEnd < json.length()) {
                                    char c = json.charAt(valueEnd);
                                    if (escaped) {
                                        escaped = false;
                                    } else if (c == '\\') {
                                        escaped = true;
                                    } else if (c == '"') {
                                        break;
                                    }
                                    valueEnd++;
                                }
                                if (valueEnd < json.length()) {
                                    return unescapeJson(json.substring(valueStart, valueEnd));
                                }
                            }
                        }
                    }
                }
            }
            
            // Fallback: try simple "content":"..." pattern
            int simpleContentStart = json.indexOf("\"content\":\"");
            if (simpleContentStart >= 0) {
                int valueStart = simpleContentStart + 11;
                int valueEnd = valueStart;
                boolean escaped = false;
                while (valueEnd < json.length()) {
                    char c = json.charAt(valueEnd);
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break;
                    }
                    valueEnd++;
                }
                if (valueEnd < json.length()) {
                    return unescapeJson(json.substring(valueStart, valueEnd));
                }
            }
            
            return null;
        } catch (Exception e) {
            context.logError("Error extracting content from JSON", e);
            return null;
        }
    }
    
    /**
     * Escapes JSON string.
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Unescapes JSON string.
     * Handles common JSON escape sequences.
     */
    private String unescapeJson(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // Process escapes in order (must do \\ first to avoid double-processing)
        return str.replace("\\\\", "\u0000")  // Temporary marker for backslash
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\"", "\"")
                  .replace("\\/", "/")
                  .replace("\\b", "\b")
                  .replace("\\f", "\f")
                  .replace("\u0000", "\\");  // Restore backslash
    }
    
    /**
     * Checks if API key is configured.
     */
    private boolean checkAPIKey() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle(tr("ai.apiKeyRequired.title", "API Key Required"));
                alert.setHeaderText(tr("ai.apiKeyRequired.header", "AI API key not configured"));
                alert.setContentText(tr("ai.apiKeyRequired.body", "Please configure your AI API key first using 'AI: Configure API' command."));
                context.showThemed(alert);
            });
            return false;
        }
        return true;
    }
    
    /**
     * Shows a note selector dialog.
     */
    private Note showNoteSelector(String title, String header) {
        List<Note> allNotes = context.getNoteService().getAllNotes();
        
        Dialog<Note> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        
        ComboBox<Note> noteCombo = new ComboBox<>();
        noteCombo.getItems().addAll(allNotes);
        noteCombo.setValue(allNotes.get(0));
        noteCombo.setPrefWidth(350);
        noteCombo.setConverter(createNoteStringConverter());
        noteCombo.setButtonCell(createNoteListCell());
        noteCombo.setCellFactory(lv -> createNoteListCell());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label(tr("ai.field.note.label", "Note:")), 0, 0);
        grid.add(noteCombo, 1, 0);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return noteCombo.getValue();
            }
            return null;
        });
        
        Optional<Note> result = context.showThemed(dialog);
        return result.orElse(null);
    }
    
    /**
     * Creates a StringConverter for Note objects.
     */
    private StringConverter<Note> createNoteStringConverter() {
        return new StringConverter<Note>() {
            @Override
            public String toString(Note note) {
                if (note == null) return "";
                String title = note.getTitle();
                return title != null ? title : tr("ai.note.untitled", "Untitled");
            }
            
            @Override
            public Note fromString(String string) {
                return null;
            }
        };
    }
    
    /**
     * Creates a ListCell for Note objects.
     */
    private javafx.scene.control.ListCell<Note> createNoteListCell() {
        return new javafx.scene.control.ListCell<Note>() {
            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                if (empty || note == null) {
                    setText("");
                } else {
                    String title = note.getTitle();
                    setText(title != null ? title : tr("ai.note.untitled", "Untitled"));
                }
            }
        };
    }
    
    /**
     * Shows a progress dialog (non-blocking).
     */
    private void showProgressDialog(String title, String message) {
        Platform.runLater(() -> {
            // Close any existing progress dialog first
            if (progressStage != null) {
                try {
                    progressStage.close();
                } catch (Exception e) {
                    // Ignore errors closing old dialog
                }
            }
            
            // Create a non-modal stage with progress indicator
            progressStage = new Stage();
            progressStage.setTitle(title);
            progressStage.initStyle(StageStyle.UTILITY);
            progressStage.initModality(Modality.NONE); // Non-blocking
            progressStage.setResizable(false);
            
            VBox content = new VBox(15);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(20));
            content.setPrefWidth(300);
            
            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setProgress(-1); // Indeterminate
            
            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.setAlignment(Pos.CENTER);
            
            content.getChildren().addAll(progressIndicator, messageLabel);
            
            Scene scene = new Scene(content);
            progressStage.setScene(scene);
            context.applyTheme(scene);
            
            // Center on screen
            progressStage.centerOnScreen();
            progressStage.show();
        });
    }
    
    /**
     * Closes progress dialogs.
     */
    private void closeProgressDialog() {
        Platform.runLater(() -> {
            if (progressStage != null) {
                try {
                    progressStage.close();
                    progressStage = null;
                } catch (Exception e) {
                    // Ignore errors
                    context.logError("Error closing progress dialog", e);
                }
            }
        });
    }
    
    /**
     * Shows a result dialog with option to save.
     */
    private void showResultDialog(String title, String header, String content, Runnable onSave) {
        Platform.runLater(() -> {
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(title);
            dialog.setHeaderText(header);
            
            TextArea textArea = new TextArea(content);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefRowCount(15);
            textArea.setPrefColumnCount(60);
            textArea.getStyleClass().add("dialog-monospace-area");
            
            ButtonType saveButton = new ButtonType(tr("ai.button.saveAsNote", "Save as Note"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CLOSE);

            VBox vbox = new VBox(10);
            vbox.setPadding(new Insets(10));
            vbox.getChildren().addAll(new Label(tr("ai.result.label", "AI Result:")), textArea);
            
            dialog.getDialogPane().setContent(vbox);
            dialog.getDialogPane().setPrefSize(600, 500);
            
            dialog.setResultConverter(button -> {
                if (button == saveButton) {
                    onSave.run();
                }
                return null;
            });
            
            context.showThemed(dialog);
        });
    }
    
    /**
     * Creates a summary note.
     */
    private void createSummaryNote(Note originalNote, String summary) {
        String title = "Summary: " + originalNote.getTitle();
        Note summaryNote = context.getNoteService().createNote(title, summary);
        context.requestOpenNote(summaryNote);
        context.requestRefreshNotes();
        context.log("Created summary note: " + title);
    }
    
    /**
     * Creates a translated note.
     */
    private void createTranslatedNote(String title, String translation) {
        Note translatedNote = context.getNoteService().createNote(title, translation);
        context.requestOpenNote(translatedNote);
        context.requestRefreshNotes();
        context.log("Created translated note: " + title);
    }
    
    /**
     * Updates note content.
     */
    private void updateNoteContent(Note note, String newContent) {
        note.setContent(newContent);
        context.getNoteService().updateNote(note);
        context.requestRefreshNotes();
        context.log("Updated note content: " + note.getTitle());
    }
    
    /**
     * Creates a note from generated content.
     */
    private void createGeneratedNote(String content) {
        // Extract title from first line or use default
        String title = "AI Generated Content";
        String[] lines = content.split("\n", 2);
        if (lines.length > 0 && !lines[0].trim().isEmpty()) {
            title = lines[0].trim().replaceAll("^#+\\s*", ""); // Remove markdown headers
            if (title.length() > 50) title = title.substring(0, 50);
        }
        
        Note generatedNote = context.getNoteService().createNote(title, content);
        context.requestOpenNote(generatedNote);
        context.requestRefreshNotes();
        context.log("Created generated note: " + title);
    }
}
