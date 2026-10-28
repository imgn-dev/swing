package be.imgn.swing.ui.internal;

import java.awt.event.KeyEvent;

/**
 * A text with its mnemonic: {@code &} marks the mnemonic, {@code &&} is a literal ampersand.
 *
 * @param text the text without its markers
 * @param index the index of the mnemonic in the text, or -1 for none
 */
public record Mnemonics(String text, int index) {

    /**
     * Parses a text.
     *
     * @param marked the text with its markers
     * @return the parsed text
     */
    public static Mnemonics parse(String marked) {
        StringBuilder text = new StringBuilder(marked.length());
        int index = -1;
        int at = 0;
        while (at < marked.length()) {
            char c = marked.charAt(at);
            if (c == '&' && at + 1 < marked.length()) {
                if (marked.charAt(at + 1) == '&') {
                    text.append('&');
                    at += 2;
                    continue;
                }
                if (index < 0) {
                    index = text.length();
                }
            } else {
                text.append(c);
            }
            at++;
        }
        return new Mnemonics(text.toString(), index);
    }

    /**
     * Returns whether the text has a mnemonic.
     *
     * @return {@code true} if it has one
     */
    public boolean hasMnemonic() {
        return index >= 0 && index < text.length();
    }

    /**
     * Returns the mnemonic character, in upper case.
     *
     * @return the character
     * @throws IllegalStateException if the text has no mnemonic
     */
    public char character() {
        if (!hasMnemonic()) {
            throw new IllegalStateException("No mnemonic in " + text);
        }
        return Character.toUpperCase(text.charAt(index));
    }

    /**
     * Returns the key code of the mnemonic.
     *
     * @return the key code, such as {@link KeyEvent#VK_S}
     * @throws IllegalStateException if the text has no mnemonic
     */
    public int keyCode() {
        return KeyEvent.getExtendedKeyCodeForChar(character());
    }
}
