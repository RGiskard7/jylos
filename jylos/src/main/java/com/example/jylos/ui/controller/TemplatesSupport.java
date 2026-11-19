package com.example.jylos.ui.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.service.LinkIndex;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.templates.TemplateEngine;
import com.example.jylos.service.templates.TemplateLibrary;
import com.example.jylos.service.templates.TemplaterDialect;
import com.example.jylos.ui.components.ExplorerOrdering;
import com.example.jylos.util.MomentFormat;

import javafx.scene.input.Clipboard;

/**
 * The open vault's templates, as its settings say ({@link VaultSettings.Templates}): which
 * notes they are ({@link TemplateLibrary}), in the explorer's order; a template's text as it
 * is in its file; and that text written ({@link TemplateEngine}) with the vault's formats.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class TemplatesSupport {

    private static final Logger logger = LoggerConfig.getLogger(TemplatesSupport.class);

    private final Supplier<NoteService> notes;
    private final Supplier<VaultSettingsStore> settings;
    private final Supplier<Locale> language;
    private final Supplier<ZonedDateTime> now;

    /**
     * @param notes    the vault's notes
     * @param settings the vault's settings ({@code null} for the defaults)
     * @param language the language of the names of months and days
     * @param now      the instant dates and times are written at
     */
    TemplatesSupport(Supplier<NoteService> notes, Supplier<VaultSettingsStore> settings, Supplier<Locale> language,
            Supplier<ZonedDateTime> now) {
        this.notes = notes;
        this.settings = settings;
        this.language = language;
        this.now = now;
    }

    /** The vault's templates, by their name in the explorer's order, or why there are none. */
    TemplateLibrary.Listing listing() {
        NoteService vault = notes.get();
        if (vault == null) {
            return new TemplateLibrary.Listing("", List.of(), TemplateLibrary.Problem.NO_FOLDER);
        }
        TemplateLibrary.Listing listing = TemplateLibrary.list(templateSettings().folder(), vault.folderPaths(),
                notesByPath(vault).keySet());
        List<TemplateLibrary.Template> sorted = new ArrayList<>(listing.templates());
        sorted.sort(Comparator.comparing(TemplateLibrary.Template::name, ExplorerOrdering.NAME_ORDER));
        return new TemplateLibrary.Listing(listing.folder(), List.copyOf(sorted), listing.problem());
    }

    /** What to tell when {@code listing} has no templates to offer, or {@code null} when it has. */
    String problem(TemplateLibrary.Listing listing, Function<String, String> i18n) {
        if (listing.problem() != null) {
            return switch (listing.problem()) {
                case NO_FOLDER -> i18n.apply("templates.status.no_folder");
                case ROOT_FOLDER -> i18n.apply("templates.status.root_folder");
                case FOLDER_NOT_FOUND -> MessageFormat.format(i18n.apply("templates.status.folder_not_found"),
                        listing.folder());
            };
        }
        return listing.templates().isEmpty()
                ? MessageFormat.format(i18n.apply("templates.status.none"), listing.folder()) : null;
    }

    /** The note of {@code template}. */
    Optional<Note> note(TemplateLibrary.Template template) {
        return noteAt(template.path());
    }

    /** The note at {@code path} in the vault. */
    Optional<Note> noteAt(String path) {
        NoteService vault = notes.get();
        return vault != null && path != null ? Optional.ofNullable(notesByPath(vault).get(path)) : Optional.empty();
    }

    /** The paths of the vault's notes. */
    List<String> notePaths() {
        NoteService vault = notes.get();
        return vault != null ? List.copyOf(notesByPath(vault).keySet()) : List.of();
    }

    /** The text of {@code template} as it is in its file, properties included; {@code ""} if it cannot be read. */
    String text(TemplateLibrary.Template template) {
        return textAt(template.path());
    }

    /** The text of the note at {@code path} as it is in its file, properties included; {@code ""} if it cannot be read. */
    String textAt(String path) {
        NoteService vault = notes.get();
        Optional<Note> note = noteAt(path);
        if (vault == null || note.isEmpty()) {
            return "";
        }
        Optional<Path> file = vault.getNoteFilePath(note.get().getId());
        if (file.isPresent()) {
            try {
                return Files.readString(file.get(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warning("Could not read " + path + ": " + e.getMessage());
                return "";
            }
        }
        // With the database there is no file: the note as it would be written.
        return FrontmatterHandler.generate(vault.getNoteById(note.get().getId()).orElse(note.get()));
    }

    /**
     * A template written for a note: its text, where the cursor goes in it ({@code -1}: after
     * it) and the Templater tags left as they are because they run code.
     */
    record Rendered(String text, int cursor, List<String> unsupported) {
    }

    private static final Pattern CURSOR = Pattern.compile("<%\\s*tp\\.file\\.cursor\\((\\d*)\\)\\s*%>");

    /**
     * {@code text} written for {@code target}: Obsidian's variables in the vault's formats, then,
     * with {@code templater}, Templater's tags, whose {@code tp.file.cursor()} marks where the
     * cursor goes (the first in order; the marks are taken out).
     *
     * @throws TemplaterDialect.Failure when Templater would stop the template
     */
    Rendered write(String text, TemplaterDialect.Target target, boolean templater) {
        String core = render(text, target.title());
        return templater ? templater(core, target) : new Rendered(core, -1, List.of());
    }

    /**
     * {@code text}'s Templater tags written for {@code target}, and nothing else: what Templater
     * does to a note being made ({@link #write}).
     *
     * @throws TemplaterDialect.Failure when Templater would stop the template
     */
    Rendered templater(String text, TemplaterDialect.Target target) {
        TemplaterDialect.Written written = TemplaterDialect.render(text, target, host(target), now(), language());
        Matcher marks = CURSOR.matcher(written.text());
        StringBuilder out = new StringBuilder();
        int cursor = -1;
        int best = Integer.MAX_VALUE;
        while (marks.find()) {
            marks.appendReplacement(out, "");
            int order = marks.group(1).isEmpty() ? 0 : Integer.parseInt(marks.group(1));
            if (order < best) {
                best = order;
                cursor = out.length();
            }
        }
        marks.appendTail(out);
        return new Rendered(out.toString(), cursor, written.unsupported());
    }

    /** Whether the vault's templates understand Templater's tags. */
    boolean templater() {
        return settings().templater().enabled();
    }

    /** Whether Templater writes the notes made in the vault (folder templates and their own tags). */
    boolean templaterOnCreate() {
        VaultSettings.Templater templater = settings().templater();
        return templater.enabled() && templater.triggerOnCreate();
    }

    /**
     * The template of a note made in {@code folderPath}, as Templater finds it: that of the
     * folder, else of the nearest folder above it with one, the vault's root last; none for a
     * note in the templates folder.
     */
    Optional<String> folderTemplate(String folderPath) {
        String templates = settings().templates().folder().strip();
        String folder = folderPath != null ? folderPath : "";
        if (!templates.isEmpty() && (folder.equals(templates) || folder.startsWith(templates + "/"))) {
            return Optional.empty();
        }
        List<VaultSettings.FolderTemplate> byFolder = settings().templater().folderTemplates();
        while (true) {
            String wanted = folder;
            Optional<String> found = byFolder.stream()
                    .filter(t -> wanted.equals(t.folder().replaceAll("^/+|/+$", "")) && !t.template().isBlank())
                    .map(VaultSettings.FolderTemplate::template).findFirst();
            if (found.isPresent() || folder.isEmpty()) {
                return found.map(path -> path.toLowerCase(Locale.ROOT).endsWith(".md") ? path : path + ".md");
            }
            int slash = folder.lastIndexOf('/');
            folder = slash < 0 ? "" : folder.substring(0, slash);
        }
    }

    /** The open {@code note} as a template sees it, {@code selection} selected in its editor. */
    TemplaterDialect.Target target(Note note, String selection) {
        NoteService vault = notes.get();
        String path = vault != null ? Optional.ofNullable(vault.linkIndex().pathOf(note.getId())).orElse(note.getId()) : note.getId();
        Map<String, Object> properties = FrontmatterHandler.propertiesOf(note);
        List<String> tags = new ArrayList<>();
        note.getTags().forEach(tag -> tags.add("#" + tag.getTitle()));
        ZonedDateTime[] times = fileTimes(note);
        return target(path, note.getTitle(), note.getContent() != null ? note.getContent() : "", tags, properties,
                selection, times[0], times[1]);
    }

    /** A note about to be made at {@code path}, as a template sees it. */
    TemplaterDialect.Target newNote(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("\\.md$", "");
        return target(path, name, "", List.of(), Map.of(), "", now(), now());
    }

    private TemplaterDialect.Target target(String path, String title, String content, List<String> tags,
            Map<String, Object> frontmatter, String selection, ZonedDateTime created, ZonedDateTime modified) {
        NoteService vault = notes.get();
        String folder = path.lastIndexOf('/') < 0 ? "" : path.substring(0, path.lastIndexOf('/'));
        String root = vault != null ? vault.getStorageRoot().map(Path::toString).orElse("") : "";
        return new TemplaterDialect.Target() {
            @Override public String title() { return title; }
            @Override public List<String> tags() { return tags; }
            @Override public String content() { return content; }
            @Override public String folderName() { return folder.substring(folder.lastIndexOf('/') + 1); }
            @Override public String folderPath() { return folder.isEmpty() ? "/" : folder; }
            @Override public String path() { return path; }
            @Override public String absolutePath() { return root.isEmpty() ? path : root + "/" + path; }
            @Override public ZonedDateTime created() { return created; }
            @Override public ZonedDateTime modified() { return modified; }
            @Override public String selection() { return selection; }
            @Override public Map<String, Object> frontmatter() { return frontmatter; }
        };
    }

    /** When {@code note}'s file was made and last changed, as Templater reads them; its dates without a file. */
    private ZonedDateTime[] fileTimes(Note note) {
        NoteService vault = notes.get();
        Optional<Path> file = vault != null ? vault.getNoteFilePath(note.getId()) : Optional.empty();
        if (file.isPresent()) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(file.get(), BasicFileAttributes.class);
                return new ZonedDateTime[] {attributes.creationTime().toInstant().atZone(now().getZone()),
                        attributes.lastModifiedTime().toInstant().atZone(now().getZone())};
            } catch (IOException e) {
                logger.fine("No file times for " + note.getId() + ": " + e.getMessage());
            }
        }
        ZonedDateTime created = MomentFormat.parse(note.getCreatedDate(), null, language(), now()).orElse(now());
        ZonedDateTime modified = MomentFormat.parse(note.getModifiedDate(), null, language(), now()).orElse(now());
        return new ZonedDateTime[] {created, modified};
    }

    /** What a template asks of the app, answered with Jylos' dialogs. */
    private TemplaterDialect.Host host(TemplaterDialect.Target target) {
        return new TemplaterDialect.Host() {
            @Override
            public Optional<String> clipboard() {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                return Optional.ofNullable(clipboard.hasString() ? clipboard.getString() : "");
            }

            @Override
            public Optional<String> prompt(String question, String defaultValue, boolean multiline) {
                return prompts.prompt(question, defaultValue, multiline);
            }

            @Override
            public Optional<Object> suggest(List<String> texts, List<Object> items, String placeholder) {
                return prompts.suggest(texts, items, placeholder);
            }

            @Override
            public boolean exists(String path) {
                NoteService vault = notes.get();
                return vault != null && (notePaths().contains(path) || vault.folderPaths().contains(path));
            }

            @Override
            public Optional<String> include(String link) {
                NoteService vault = notes.get();
                if (vault == null) {
                    return Optional.empty();
                }
                Optional<Note> note = vault.resolveLink(link, target.path());
                return note.map(found -> textAt(vault.linkIndex().pathOf(found.getId())));
            }
        };
    }

    /** The dialogs a template's questions are asked with. */
    interface Prompts {
        Optional<String> prompt(String question, String defaultValue, boolean multiline);

        Optional<Object> suggest(List<String> texts, List<Object> items, String placeholder);
    }

    private Prompts prompts = new Prompts() {
        @Override
        public Optional<String> prompt(String question, String defaultValue, boolean multiline) {
            return TemplaterPromptsSupport.prompt(question, defaultValue, multiline);
        }

        @Override
        public Optional<Object> suggest(List<String> texts, List<Object> items, String placeholder) {
            return TemplaterPromptsSupport.suggest(texts, items, placeholder);
        }
    };

    /** Asks a template's questions with {@code prompts} (tests). */
    void usePrompts(Prompts prompts) {
        this.prompts = prompts;
    }

    /** The vault's settings (the defaults with none kept). */
    VaultSettings settings() {
        VaultSettingsStore store = settings.get();
        return store != null ? store.get() : VaultSettings.defaults();
    }

    /** The language of the names of months and days. */
    Locale language() {
        return language.get();
    }

    /** The instant dates and times are written at, now. */
    ZonedDateTime now() {
        return now.get();
    }

    /** {@code text} written for a note called {@code title}, with the vault's formats. */
    String render(String text, String title) {
        ZonedDateTime instant = now.get();
        VaultSettings.Templates formats = templateSettings();
        return TemplateEngine.render(text, TemplateEngine.variables(title, instant), formats.dateFormat(),
                formats.timeFormat(), instant, language.get());
    }

    /** Today, in the vault's date format. */
    String date() {
        return MomentFormat.format(now.get(), templateSettings().dateFormat(), language.get());
    }

    /** Now, in the vault's time format. */
    String time() {
        return MomentFormat.format(now.get(), templateSettings().timeFormat(), language.get());
    }

    private VaultSettings.Templates templateSettings() {
        return settings().templates();
    }

    /** The vault's notes by their path in it. */
    private static Map<String, Note> notesByPath(NoteService vault) {
        LinkIndex links = vault.linkIndex();
        Map<String, Note> byPath = new LinkedHashMap<>();
        for (Note note : vault.getAllNotes()) {
            String path = note != null ? links.pathOf(note.getId()) : null;
            if (path != null) {
                byPath.putIfAbsent(path, note);
            }
        }
        return byPath;
    }
}
