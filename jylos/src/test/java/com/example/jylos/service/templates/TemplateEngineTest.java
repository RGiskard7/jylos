package com.example.jylos.service.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * A template is written as Obsidian's Templates writes it: {@code {{title}}} and the other
 * variables in any case, {@code {{date}}} and {@code {{time}}} in the vault's formats or the
 * one after the colon, all at the same instant, and what it does not know left as it is.
 */
class TemplateEngineTest {

    private static final ZonedDateTime NOW = LocalDateTime.parse("2026-11-19T14:05:09").atZone(ZoneId.of("Europe/Madrid"));
    private static final Locale ES = Locale.forLanguageTag("es");

    private static String render(String text, String title) {
        return TemplateEngine.render(text, Map.of("title", title), "YYYY-MM-DD", "HH:mm", NOW, ES);
    }

    @Test
    void theVariablesAreReplacedInAnyCase() {
        assertEquals("# Acta\nActa, Acta", render("# {{title}}\n{{Title}}, {{TITLE}}", "Acta"));
    }

    @Test
    void dateAndTimeUseTheVaultsFormats() {
        assertEquals("2026-11-19 14:05", render("{{date}} {{time}}", "x"));
        assertEquals("19/11/2026 14.05",
                TemplateEngine.render("{{date}} {{time}}", Map.of(), "DD/MM/YYYY", "HH.mm", NOW, ES));
    }

    @Test
    void aFormatAfterTheColonWinsAndAnEmptyOneIsTheVaults() {
        assertEquals("jueves 19 de noviembre, 14:05:09", render("{{date:dddd D [de] MMMM}}, {{time:HH:mm:ss}}", "x"));
        assertEquals("2026 11", render("{{DATE:YYYY}} {{Date:MM}}", "x"));
        assertEquals("2026-11-19 14:05", render("{{date:}} {{time:}}", "x"));
        assertEquals("[2026] (11)", render("[{{date:YYYY}}] ({{time:MM}})", "x"), "each format ends at its own }}");
    }

    @Test
    void whatItDoesNotKnowIsLeftAsItIs() {
        assertEquals("{{foo}} {{ date }} {{datex}} {date}", render("{{foo}} {{ date }} {{datex}} {date}", "x"));
    }

    @Test
    void aDollarInAValueIsReadAsObsidianReadsIt() {
        // JavaScript's replace: $$ is a dollar, $& the text replaced, $` what comes before it.
        assertEquals("# Coste $ y {{title}} y $1 y # ", render("# {{title}}", "Coste $$ y $& y $1 y $`"));
    }

    @Test
    void theVariablesComeFirstAndTheirValuesAreNotReadAgainForDates() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("title", "{{date}}");
        assertEquals("2026-11-19", TemplateEngine.render("{{title}}", variables, "YYYY-MM-DD", "HH:mm", NOW, ES),
                "as in Obsidian, the dates are replaced after the variables, in all the text");
    }

    @Test
    void jylosAlsoWritesDatetime() {
        assertEquals("2026-11-19T14:05:09",
                TemplateEngine.render("{{datetime}}", TemplateEngine.variables("x", NOW), "YYYY-MM-DD", "HH:mm", NOW, ES));
    }
}
