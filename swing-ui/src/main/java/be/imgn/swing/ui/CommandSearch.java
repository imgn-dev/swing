package be.imgn.swing.ui;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Matches a query against the text of a command, as the palette and the keymap dialog filter: both are decomposed,
 * stripped of combining marks, lower-cased and stripped, and the query matches when its characters appear in the text
 * in order. A match has a rank: the text starts with the query, then a word of it does, then the text contains it, then
 * its characters match only in order.
 */
final class CommandSearch {

    /** The rank of a text that the query doesn't match. */
    static final int NO_MATCH = -1;

    // The ranks of a match, the best first.
    private static final int PREFIX = 0;
    private static final int WORD = 1;
    private static final int SUBSTRING = 2;
    private static final int SUBSEQUENCE = 3;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private CommandSearch() {}

    static String normalize(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    /**
     * Ranks a text against a query, both normalized.
     *
     * @param text the normalized text
     * @param query the normalized query, not empty
     * @return 0 for a prefix, 1 for a word's prefix, 2 for a substring, 3 for characters in order, or {@link #NO_MATCH}
     */
    static int rank(String text, String query) {
        if (text.startsWith(query)) {
            return PREFIX;
        }
        for (int index = 1; index < text.length(); index++) {
            if (isWordStart(text, index) && text.startsWith(query, index)) {
                return WORD;
            }
        }
        if (text.contains(query)) {
            return SUBSTRING;
        }
        int at = 0;
        for (int index = 0; index < text.length() && at < query.length(); index++) {
            if (text.charAt(index) == query.charAt(at)) {
                at++;
            }
        }
        return at == query.length() ? SUBSEQUENCE : NO_MATCH;
    }

    // A word is a run of letters and digits.
    private static boolean isWordStart(String text, int index) {
        return Character.isLetterOrDigit(text.charAt(index)) && !Character.isLetterOrDigit(text.charAt(index - 1));
    }
}
