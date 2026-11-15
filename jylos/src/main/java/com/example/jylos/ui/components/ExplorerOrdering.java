package com.example.jylos.ui.components;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.NoteService.SortOption;

/**
 * Order of the children of one folder in the Explorer tree.
 *
 * <p>Folders always come first, by name from A to Z. Notes follow, in the order the user
 * picked. Names compare the way people read them: runs of digits compare by value ("Nota 2"
 * before "Nota 10"), and case and accents do not change the order of letters ("Árbol"
 * between "apple" and "banana", not after every unaccented name).</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ExplorerOrdering {

    private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);

    static {
        COLLATOR.setStrength(Collator.SECONDARY);
    }

    /** Natural, case-insensitive name order; a missing name sorts as an empty one. */
    public static final Comparator<String> NAME_ORDER = ExplorerOrdering::compareNames;

    private ExplorerOrdering() {
    }

    /**
     * Folders (by name) followed by notes: pinned ones first, as in the notes list, each group
     * by {@code noteSort}. Notes with an equal sort key fall back to name order, so the result
     * does not depend on the order the storage returned them in.
     */
    public static List<Component> order(List<Folder> folders, List<Note> notes, SortOption noteSort) {
        List<Folder> sortedFolders = new ArrayList<>(folders.stream().filter(Objects::nonNull).toList());
        sortedFolders.sort(Comparator.comparing(Folder::getTitle, NAME_ORDER));
        List<Note> sortedNotes = new ArrayList<>(notes.stream().filter(Objects::nonNull).toList());
        sortedNotes.sort(Comparator.comparing((Note note) -> !note.isPinned()).thenComparing(noteComparator(noteSort)));

        List<Component> out = new ArrayList<>(sortedFolders.size() + sortedNotes.size());
        out.addAll(sortedFolders);
        out.addAll(sortedNotes);
        return out;
    }

    /** The order of notes alone, with name order as the tie-breaker. */
    public static Comparator<Note> noteComparator(SortOption noteSort) {
        Comparator<Note> byName = Comparator.comparing(Note::getTitle, NAME_ORDER);
        SortOption option = noteSort != null ? noteSort : SortOption.TITLE_ASC;
        return switch (option) {
            case TITLE_ASC -> byName;
            case TITLE_DESC -> byName.reversed();
            case CREATED_NEWEST -> Comparator.comparing(ExplorerOrdering::created).reversed().thenComparing(byName);
            case CREATED_OLDEST -> Comparator.comparing(ExplorerOrdering::created).thenComparing(byName);
            case MODIFIED_NEWEST -> Comparator.comparing(ExplorerOrdering::modified).reversed().thenComparing(byName);
            case MODIFIED_OLDEST -> Comparator.comparing(ExplorerOrdering::modified).thenComparing(byName);
        };
    }

    /**
     * Compares two names chunk by chunk: digit runs by numeric value, everything else with a
     * collator that ignores case. Names that are equal under those rules fall back to plain
     * string order, so the comparison is still total.
     */
    public static int compareNames(String a, String b) {
        String left = a != null ? a : "";
        String right = b != null ? b : "";
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            boolean leftDigit = Character.isDigit(left.charAt(i));
            boolean rightDigit = Character.isDigit(right.charAt(j));
            int iEnd = chunkEnd(left, i, leftDigit);
            int jEnd = chunkEnd(right, j, rightDigit);
            String leftChunk = left.substring(i, iEnd);
            String rightChunk = right.substring(j, jEnd);
            int cmp = leftDigit && rightDigit
                    ? compareNumbers(leftChunk, rightChunk)
                    : COLLATOR.compare(leftChunk, rightChunk);
            if (cmp != 0) {
                return cmp;
            }
            i = iEnd;
            j = jEnd;
        }
        int byLength = Integer.compare(left.length() - i, right.length() - j);
        return byLength != 0 ? byLength : left.compareTo(right);
    }

    private static int chunkEnd(String s, int start, boolean digits) {
        int end = start;
        while (end < s.length() && Character.isDigit(s.charAt(end)) == digits) {
            end++;
        }
        return end;
    }

    private static int compareNumbers(String a, String b) {
        String x = stripLeadingZeros(a);
        String y = stripLeadingZeros(b);
        if (x.length() != y.length()) {
            return Integer.compare(x.length(), y.length());
        }
        return x.compareTo(y);
    }

    private static String stripLeadingZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') {
            k++;
        }
        return digits.substring(k);
    }

    private static String created(Note note) {
        return note.getCreatedDate() != null ? note.getCreatedDate() : "";
    }

    private static String modified(Note note) {
        return note.getModifiedDate() != null ? note.getModifiedDate() : created(note);
    }
}
