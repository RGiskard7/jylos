package com.example.jylos.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal line-based diff (LCS) used by the note-history viewer.
 *
 * <p>Produces an ordered list of {@link Line}s typed {@code SAME}, {@code REMOVED}
 * (present in the old text only) or {@code ADDED} (present in the new text only).
 * For pathological inputs (more than {@link #MAX_LINES} lines on either side) it
 * degrades gracefully to a common-prefix/suffix diff instead of the quadratic LCS,
 * so the UI never freezes on huge notes.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class LineDiff {

    /** Above this many lines per side, fall back to prefix/suffix diffing. */
    static final int MAX_LINES = 3000;

    public enum Type { SAME, ADDED, REMOVED }

    /** One diff output line. */
    public record Line(Type type, String text) {
    }

    private LineDiff() {
        // utility class
    }

    /** Diffs {@code oldText} against {@code newText}, line by line. Null-safe. */
    public static List<Line> diff(String oldText, String newText) {
        String[] a = (oldText != null ? oldText : "").split("\n", -1);
        String[] b = (newText != null ? newText : "").split("\n", -1);
        if (a.length > MAX_LINES || b.length > MAX_LINES) {
            return prefixSuffixDiff(a, b);
        }
        return lcsDiff(a, b);
    }

    private static List<Line> lcsDiff(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        // lcs[i][j] = LCS length of a[i..] and b[j..]
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Line> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                out.add(new Line(Type.SAME, a[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new Line(Type.REMOVED, a[i]));
                i++;
            } else {
                out.add(new Line(Type.ADDED, b[j]));
                j++;
            }
        }
        while (i < n) {
            out.add(new Line(Type.REMOVED, a[i++]));
        }
        while (j < m) {
            out.add(new Line(Type.ADDED, b[j++]));
        }
        return out;
    }

    /** Cheap fallback: common prefix + common suffix, middle as removed-then-added. */
    private static List<Line> prefixSuffixDiff(String[] a, String[] b) {
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.length - prefix && suffix < b.length - prefix
                && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) {
            suffix++;
        }
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < prefix; i++) {
            out.add(new Line(Type.SAME, a[i]));
        }
        for (int i = prefix; i < a.length - suffix; i++) {
            out.add(new Line(Type.REMOVED, a[i]));
        }
        for (int i = prefix; i < b.length - suffix; i++) {
            out.add(new Line(Type.ADDED, b[i]));
        }
        for (int i = a.length - suffix; i < a.length; i++) {
            out.add(new Line(Type.SAME, a[i]));
        }
        return out;
    }
}
