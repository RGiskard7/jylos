package com.example.jylos.util;

/**
 * Compares version numbers such as {@code 2.6.0} or {@code v2.5.8}: numeric parts compared in
 * order, a missing part counting as 0, and a suffix after {@code -} ignored.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class Versions {

    private Versions() {
    }

    /** Whether {@code candidate} is a later version than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /** Negative, zero or positive as {@code a} is earlier than, the same as, or later than {@code b}. */
    public static int compare(String a, String b) {
        int[] aParts = parse(a);
        int[] bParts = parse(b);
        int max = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < max; i++) {
            int aPart = i < aParts.length ? aParts[i] : 0;
            int bPart = i < bParts.length ? bParts[i] : 0;
            if (aPart != bPart) {
                return Integer.compare(aPart, bPart);
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[] {0, 0, 0};
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int suffixIndex = normalized.indexOf('-');
        if (suffixIndex >= 0) {
            normalized = normalized.substring(0, suffixIndex);
        }
        String[] rawParts = normalized.split("\\.");
        int[] parts = new int[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            parts[i] = parsePart(rawParts[i]);
        }
        return parts;
    }

    private static int parsePart(String part) {
        String digits = part == null ? "" : part.replaceAll("[^0-9].*$", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }
}
