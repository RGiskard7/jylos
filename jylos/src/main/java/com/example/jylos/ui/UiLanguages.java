package com.example.jylos.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The languages of the interface: those the app carries a {@code messages_<code>.properties}
 * file for, looked up by the class loader for every ISO 639 language, so it works the same from
 * folders (developing), a JAR or an installed image. Adding a language is adding its file; the
 * View menu lists it. Each one is named in its own language ({@link #nameOf}).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class UiLanguages {

    private static final String BUNDLES = "com/example/jylos/i18n/messages_";

    private UiLanguages() {
    }

    /** The codes of the interface's languages ({@code en}, {@code es}…), by code. */
    public static List<String> available() {
        return available(UiLanguages.class.getClassLoader());
    }

    static List<String> available(ClassLoader loader) {
        List<String> codes = new ArrayList<>();
        for (String code : Locale.getISOLanguages()) {
            if (loader != null && loader.getResource(BUNDLES + code + ".properties") != null) {
                codes.add(code);
            }
        }
        return codes.isEmpty() ? List.of("en") : List.copyOf(codes);
    }

    /** The name of the language {@code code} in that language, capitalised: "English", "Русский". */
    public static String nameOf(String code) {
        Locale language = Locale.forLanguageTag(code);
        String name = language.getDisplayLanguage(language);
        if (name.isEmpty()) {
            return code;
        }
        return name.substring(0, 1).toUpperCase(language) + name.substring(1);
    }
}
