package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

/**
 * A single unescaped {@code '} in a {@link MessageFormat} pattern starts a quoted literal
 * region — anything inside it, including a {@code {0}} placeholder, is printed as-is
 * instead of substituted, and the quote characters themselves vanish from the output. A
 * dialog confirming "Remove tag '{0}' from this note?" rendered as "Remove tag {0} from
 * this note?" this way. Guards every message key actually run through
 * {@code MessageFormat.format} in the app against that mistake.
 */
class I18nMessageFormatQuoteGuardTest {

    /** Keys formatted with a single {@code {0}} argument via {@code MessageFormat.format}. */
    private static final List<String> KEYS_WITH_ONE_ARGUMENT = List.of(
            "dialog.delete_tag.content",
            "dialog.remove_tag.header",
            "dialog.tag_already_assigned.header",
            "status.filtered_tag",
            "status.tag_added",
            "status.tag_removed");

    @Test
    void placeholderSurvivesFormattingInEveryLocale() {
        for (Locale locale : List.of(Locale.ROOT, Locale.ENGLISH, Locale.of("es"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", locale);
            for (String key : KEYS_WITH_ONE_ARGUMENT) {
                if (!bundle.containsKey(key)) {
                    continue;
                }
                String formatted = MessageFormat.format(bundle.getString(key), "MARKER");
                assertEquals(true, formatted.contains("MARKER"),
                        "[" + locale + "] '" + key + "' swallowed its {0} argument — pattern: "
                                + bundle.getString(key));
            }
        }
    }
}
