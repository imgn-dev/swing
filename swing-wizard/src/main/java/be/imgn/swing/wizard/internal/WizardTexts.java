package be.imgn.swing.wizard.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Resources;
import be.imgn.toml.TomlString;
import java.text.MessageFormat;

/** The wizard's own texts, from {@code WizardTexts.toml} and its locale variants. */
@Singleton
public final class WizardTexts {

    // Read at each call, so that the texts follow the display locale.
    private final Resources resources;

    @Inject
    WizardTexts(Resources resources) {
        this.resources = resources;
    }

    /**
     * Returns the heading of a wizard's shown step.
     *
     * @param step the step's number, from 1
     * @param steps the number of steps
     * @param title the step's title
     * @return the heading, such as {@code Step 1 of 3: Contact Information}
     */
    public String heading(int step, int steps, String title) {
        String pattern = resources.table(WizardTexts.class).entries().get("heading") instanceof TomlString text
                ? text.value()
                : "heading";
        return new MessageFormat(pattern, resources.locale()).format(new Object[] {step, steps, title});
    }
}
