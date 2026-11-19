package com.example.jylos.service.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Templater's syntax, as Templater writes it: {@code <% %>} and its whitespace control, what
 * {@code tp.date}, {@code tp.file}, {@code tp.frontmatter} and {@code tp.system} write, and how
 * a value is written (as JavaScript writes it). The expected texts are Templater's own, its
 * parser run on these templates with the same note and answers; a template Templater aborts
 * is refused here too. What runs code is left as it is.
 */
class TemplaterDialectTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final ZonedDateTime NOW = OffsetDateTime.parse("2026-11-19T14:05:09+01:00").atZoneSameInstant(MADRID);
    private static final Locale ES = Locale.forLanguageTag("es");

    /** The note of the expected table. */
    static final TemplaterDialect.Target NOTE = new TemplaterDialect.Target() {
        @Override public String title() { return "Nota"; }
        @Override public List<String> tags() { return List.of("#a", "#b"); }
        @Override public String content() { return "Texto\n"; }
        @Override public String folderName() { return "Diario"; }
        @Override public String folderPath() { return "Proyectos/Diario"; }
        @Override public String path() { return "Proyectos/Diario/Nota.md"; }
        @Override public String absolutePath() { return "/boveda/Proyectos/Diario/Nota.md"; }
        @Override public ZonedDateTime created() { return OffsetDateTime.parse("2026-11-01T08:00:00+01:00").atZoneSameInstant(MADRID); }
        @Override public ZonedDateTime modified() { return OffsetDateTime.parse("2026-11-18T20:30:00+01:00").atZoneSameInstant(MADRID); }
        @Override public String selection() { return "sel"; }
        @Override public Map<String, Object> frontmatter() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("estado", "hecho");
            values.put("vacía", null);
            values.put("lista", List.of("x", "y"));
            values.put("con espacio", 1);
            return values;
        }
    };

    /** The answers of the expected table. */
    static final TemplaterDialect.Host HOST = new TemplaterDialect.Host() {
        @Override public Optional<String> clipboard() { return Optional.of("pegado"); }
        @Override public Optional<String> prompt(String question, String defaultValue, boolean multiline) {
            return Optional.of(defaultValue != null ? defaultValue : "respuesta");
        }
        @Override public Optional<Object> suggest(List<String> texts, List<Object> items, String placeholder) {
            return items.isEmpty() ? Optional.empty() : Optional.ofNullable(items.getFirst());
        }
        @Override public boolean exists(String path) { return "Nota.md".equals(path); }
        @Override public Optional<String> include(String link) { return Optional.empty(); }
    };

    private static String render(String text) {
        return TemplaterDialect.render(text, NOTE, HOST, NOW, ES).text();
    }

    @Test
    void everyTemplateIsWrittenAsTemplaterWritesIt() throws Exception {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        try (InputStream in = TemplaterDialectTest.class.getResourceAsStream("templater-expected.tsv");
                BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] cols = line.split("\t");
                String template = JsonParser.parseString(cols[0]).getAsString();
                String expected = JsonParser.parseString(cols[1]).getAsString();
                String actual;
                try {
                    actual = render(template);
                } catch (TemplaterDialect.Failure e) {
                    actual = "ERROR";
                }
                checked++;
                if (!expected.equals(actual)) {
                    wrong.add(template + "\n    expected [" + expected + "]\n    but was  [" + actual + "]");
                }
            }
        }
        assertTrue(checked > 90, "the whole table was read: " + checked);
        assertEquals(List.of(), wrong, wrong.size() + " of " + checked + " differ");
    }

    @Test
    void whatRunsCodeIsLeftAsItIsAndSaid() {
        String template = "a <%* tR += 'x' %> b <% tp.user.mio() %> c <% tp.web.daily_quote() %> d <% 1 + 2 %> e"
                + " <%+ tp.file.title %> f <% tp.file.title %>";

        TemplaterDialect.Written written = TemplaterDialect.render(template, NOTE, HOST, NOW, ES);

        assertEquals("a <%* tR += 'x' %> b <% tp.user.mio() %> c <% tp.web.daily_quote() %> d <% 1 + 2 %> e"
                + " <%+ tp.file.title %> f Nota", written.text());
        assertEquals(List.of("<%*", "tp.user.mio", "tp.web.daily_quote", "1 + 2", "<%+"), written.unsupported());
    }

    @Test
    void aTagLeftAsItIsKeepsTheWhitespaceAroundIt() {
        assertEquals("x\n<%- tp.app.vault -%>\ny", render("x\n<%- tp.app.vault -%>\ny"));
    }

    @Test
    void anIncludedNoteIsWrittenTooUpToTenDeep() {
        TemplaterDialect.Host including = new TemplaterDialect.Host() {
            @Override public Optional<String> clipboard() { return Optional.empty(); }
            @Override public Optional<String> prompt(String q, String d, boolean m) { return Optional.empty(); }
            @Override public Optional<Object> suggest(List<String> t, List<Object> i, String p) { return Optional.empty(); }
            @Override public boolean exists(String path) { return false; }
            @Override public Optional<String> include(String link) {
                return switch (link) {
                    case "Cabecera" -> Optional.of("# <% tp.file.title %>");
                    case "Bucle" -> Optional.of("<% tp.file.include(\"[[Bucle]]\") %>");
                    case "n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8", "n9", "n10" -> Optional.of(
                            "<% tp.file.include(\"[[n" + (Integer.parseInt(link.substring(1)) + 1) + "]]\") %>");
                    case "n11" -> Optional.of("fondo");
                    default -> Optional.empty();
                };
            }
        };

        assertEquals("# Nota\nfin", TemplaterDialect.render("<% tp.file.include(\"[[Cabecera]]\") %>\nfin", NOTE,
                including, NOW, ES).text());
        assertThrows(TemplaterDialect.Failure.class, () -> TemplaterDialect.render(
                "<% tp.file.include(\"[[Bucle]]\") %>", NOTE, including, NOW, ES), "more than ten deep");
        assertEquals("fondo", TemplaterDialect.render("<% tp.file.include(\"[[n2]]\") %>", NOTE, including, NOW, ES)
                .text(), "ten deep is written");
        assertThrows(TemplaterDialect.Failure.class, () -> TemplaterDialect.render(
                "<% tp.file.include(\"[[n1]]\") %>", NOTE, including, NOW, ES), "eleven deep stops");
        assertThrows(TemplaterDialect.Failure.class, () -> TemplaterDialect.render(
                "<% tp.file.include(\"Cabecera\") %>", NOTE, including, NOW, ES), "not a link");
    }

    @Test
    void aCancelledPromptWritesNullOrStopsTheTemplate() {
        TemplaterDialect.Host cancelling = new TemplaterDialect.Host() {
            @Override public Optional<String> clipboard() { return Optional.empty(); }
            @Override public Optional<String> prompt(String q, String d, boolean m) { return Optional.empty(); }
            @Override public Optional<Object> suggest(List<String> t, List<Object> i, String p) { return Optional.empty(); }
            @Override public boolean exists(String path) { return false; }
            @Override public Optional<String> include(String link) { return Optional.empty(); }
        };

        assertEquals("null", TemplaterDialect.render("<% tp.system.prompt(\"¿Qué?\") %>", NOTE, cancelling, NOW, ES).text());
        assertThrows(TemplaterDialect.Failure.class, () -> TemplaterDialect.render(
                "<% tp.system.prompt(\"¿Qué?\", \"\", true) %>", NOTE, cancelling, NOW, ES));
    }
}
