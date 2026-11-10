import be.imgn.swing.ResourceOpener;
import be.imgn.swing.wizard.internal.WizardInternalResources;
import be.imgn.swing.wizard.internal.WizardResources;

/**
 * Wizards: dialog views whose content is a sequence of steps, with Previous, Next and Finish, each step validated
 * before the next.
 */
module be.imgn.swing.wizard {
    requires transitive be.imgn.swing.ui;
    requires static org.jspecify;

    exports be.imgn.swing.wizard;

    provides ResourceOpener with
            WizardInternalResources,
            WizardResources;
}
