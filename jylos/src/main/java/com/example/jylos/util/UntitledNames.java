package com.example.jylos.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The name a new note or folder gets, as in Obsidian: the base name, or the first free "base N".
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class UntitledNames {

    private UntitledNames() {
    }

    /**
     * {@code base} if no name in {@code taken} is the same (ignoring case, as on most disks),
     * otherwise {@code "base 1"}, {@code "base 2"}, ... the first one free.
     */
    public static String next(String base, Collection<String> taken) {
        Set<String> used = taken.stream().filter(java.util.Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!used.contains(base.toLowerCase(Locale.ROOT))) {
            return base;
        }
        int number = 1;
        while (used.contains((base + " " + number).toLowerCase(Locale.ROOT))) {
            number++;
        }
        return base + " " + number;
    }
}
