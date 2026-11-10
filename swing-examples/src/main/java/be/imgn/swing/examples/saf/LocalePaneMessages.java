package be.imgn.swing.examples.saf;

import be.imgn.swing.Messages;

/** The texts of a {@link LocalePane}, in English, French and Spanish, and the exit veto's question. */
@Messages
public interface LocalePaneMessages {

    /**
     * Returns the label's text.
     *
     * @return the text
     */
    String text();

    /**
     * Returns the button's text.
     *
     * @return the text
     */
    String what();

    /**
     * Returns the question asked before exiting.
     *
     * @return the question
     */
    String stop();
}
