package be.imgn.swing.examples.addressbook;

import be.imgn.swing.docking.Dock;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.testing.AppTester;
import javax.swing.JTable;

/** Reaches the views of the address book's dock, and their components, in tests. */
final class AddressBookViews {

    private AddressBookViews() {}

    static <V extends DockView> V view(AppTester<AddressBookApp> app, Class<V> type) {
        Dock dock = app.bean(Dock.class);
        return app.call(() -> dock.views().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in " + dock.views())));
    }

    static JTable table(AppTester<AddressBookApp> app) {
        return view(app, ContactListView.class).table;
    }
}
