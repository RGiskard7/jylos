package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class I18nBundleFallbackGuardTest {

    private static final String[] BUNDLES = {
        "/com/example/jylos/i18n/messages.properties",
        "/com/example/jylos/i18n/messages_en.properties",
        "/com/example/jylos/i18n/messages_es.properties"
    };

    @Test
    void baseBundleShouldResolveCriticalKeysForUnsupportedLocale() {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "com.example.jylos.i18n.messages",
                Locale.forLanguageTag("fr-FR"));

        assertNotNull(bundle);
        assertTrue(bundle.containsKey("app.all_notes"));
        assertTrue(bundle.containsKey("app.my_notes"));
        assertNotEquals("app.all_notes", bundle.getString("app.all_notes"));
    }

    /** Every language bundle must define exactly the same set of keys (ready for translation). */
    @Test
    void allBundlesHaveIdenticalKeySets() throws Exception {
        Set<String> reference = keysOf(BUNDLES[0]);
        for (String path : BUNDLES) {
            Set<String> keys = keysOf(path);
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(reference);
            assertTrue(missing.isEmpty() && extra.isEmpty(),
                    path + " key set diverges. Missing: " + missing + " Extra: " + extra);
        }
    }

    /** No duplicate keys in any bundle (a Properties load would silently drop them). */
    @Test
    void bundlesHaveNoDuplicateKeys() throws Exception {
        for (String path : BUNDLES) {
            int unique;
            try (InputStream in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, "missing bundle: " + path);
                Properties props = new Properties();
                props.load(in);
                unique = props.size();
            }
            long rawKeyLines;
            try (InputStream in = getClass().getResourceAsStream(path)) {
                rawKeyLines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .lines()
                        .filter(l -> l.matches("^[A-Za-z][A-Za-z0-9._]*=.*"))
                        .count();
            }
            assertTrue(rawKeyLines == unique,
                    path + " has duplicate keys (" + rawKeyLines + " key lines vs " + unique + " unique).");
        }
    }

    private Set<String> keysOf(String resourcePath) throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing bundle: " + resourcePath);
            props.load(in);
        }
        return new TreeSet<>(props.stringPropertyNames());
    }
}
