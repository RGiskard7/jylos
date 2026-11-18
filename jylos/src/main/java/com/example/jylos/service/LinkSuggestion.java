package com.example.jylos.service;

import java.util.List;

/**
 * A note as {@code [[} suggests it while a link is typed: its name, the folder it is in ("" at
 * the root), the shortest link that names it unambiguously (its name, or its path when another
 * note has that name) and its aliases.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public record LinkSuggestion(String name, String folder, String link, List<String> aliases) {

    public LinkSuggestion {
        aliases = aliases != null ? List.copyOf(aliases) : List.of();
    }
}
