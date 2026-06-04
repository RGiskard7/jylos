package com.example.jylos.util;

/**
 * Lightweight fuzzy-matching utilities for the QuickSwitcher and
 * CommandPalette.
 *
 * <p>The implementation uses a simplified <em>subsequence matching</em>
 * algorithm with scoring, similar to how editors like VS Code or Sublime Text
 * rank search results.  It avoids heavyweight NLP dependencies.</p>
 *
 * <h3>Scoring heuristics</h3>
 * <ul>
 *   <li>Consecutive character matches add a bonus.</li>
 *   <li>Matches at the start of the string or after separator characters
 *       (spaces, hyphens, underscores) receive a higher bonus.</li>
 *   <li>Exact prefix matches beat scattered subsequence matches.</li>
 *   <li>A score of zero (or below) means "no match".</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public final class FuzzySearchUtils {

    private FuzzySearchUtils() {
        // utility class
    }

    /**
     * Returns a non-negative score indicating how well {@code query} fuzzy-matches
     * {@code target}. Returns {@code 0} when there is no match.
     *
     * <p>Higher is better.</p>
     *
     * @param query  the user-typed search string (case insensitive)
     * @param target the candidate string to compare against
     * @return match score (0 = no match)
     */
    public static int fuzzyScore(String query, String target) {
        if (query == null || target == null || query.isEmpty()) {
            return 0;
        }

        String q = query.toLowerCase();
        String t = target.toLowerCase();

        // Quick exact-contains shortcut — heavily rewarded.
        int containsIdx = t.indexOf(q);
        if (containsIdx >= 0) {
            // Full substring match — best possible.
            int bonus = 100 + (q.length() * 10);
            // Extra reward if it appears at the very start.
            if (containsIdx == 0) {
                bonus += 50;
            }
            return bonus;
        }

        // Subsequence match with scoring.
        int score = 0;
        int qi = 0; // pointer into query
        int consecutive = 0;
        boolean prevSeparator = true; // treat start-of-string as separator

        for (int ti = 0; ti < t.length() && qi < q.length(); ti++) {
            char tc = t.charAt(ti);
            char qc = q.charAt(qi);

            if (tc == qc) {
                qi++;
                consecutive++;
                // A basic per-character score.
                score += 1;
                // Bonus for consecutive matches.
                score += (consecutive - 1) * 5;
                // Bonus for matching right after a separator.
                if (prevSeparator) {
                    score += 10;
                }
                // Bonus for matching at the very first character.
                if (ti == 0) {
                    score += 15;
                }
            } else {
                consecutive = 0;
            }

            prevSeparator = isSeparator(tc);
        }

        // All query characters must have been consumed.
        if (qi < q.length()) {
            return 0;
        }

        return score;
    }

    /**
     * Convenience method: returns {@code true} when {@code query} fuzzy-matches
     * {@code target} with a score above the given threshold.
     *
     * @param query     user input
     * @param target    candidate
     * @param threshold minimum score to consider a match (e.g. 1)
     */
    public static boolean matches(String query, String target, int threshold) {
        return fuzzyScore(query, target) >= threshold;
    }

    /**
     * Convenience overload with a default threshold of 1.
     */
    public static boolean matches(String query, String target) {
        return matches(query, target, 1);
    }

    // ------------------------------------------------------------------

    private static boolean isSeparator(char c) {
        return c == ' ' || c == '-' || c == '_' || c == '/' || c == '\\' || c == '.';
    }
}
