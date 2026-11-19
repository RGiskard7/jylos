package com.example.jylos.ui.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.VaultSettingsStore;
import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.service.LinkIndex;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.templates.TemplateEngine;
import com.example.jylos.service.templates.TemplateLibrary;
import com.example.jylos.ui.components.ExplorerOrdering;
import com.example.jylos.util.MomentFormat;

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
        NoteService vault = notes.get();
        return vault != null ? Optional.ofNullable(notesByPath(vault).get(template.path())) : Optional.empty();
    }

    /** The text of {@code template} as it is in its file, properties included; {@code ""} if it cannot be read. */
    String text(TemplateLibrary.Template template) {
        NoteService vault = notes.get();
        Optional<Note> note = note(template);
        if (vault == null || note.isEmpty()) {
            return "";
        }
        Optional<Path> file = vault.getNoteFilePath(note.get().getId());
        if (file.isPresent()) {
            try {
                return Files.readString(file.get(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warning("Could not read template " + template.path() + ": " + e.getMessage());
                return "";
            }
        }
        // With the database there is no file: the note as it would be written.
        return FrontmatterHandler.generate(vault.getNoteById(note.get().getId()).orElse(note.get()));
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
        VaultSettingsStore store = settings.get();
        return (store != null ? store.get() : VaultSettings.defaults()).templates();
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
