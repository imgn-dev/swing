package be.imgn.swing.desktop.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.ui.internal.TesterExtension;
import java.util.Optional;

/**
 * The desktop's replacements in the test harness: a single instance that never opens a socket or a lock, and always
 * finds no other instance; the harness stands for the forwarding with {@code launchAgain}.
 */
public final class DesktopTesterExtension implements TesterExtension {

    /** Creates the extension; the service loader does. */
    public DesktopTesterExtension() {}

    @Override
    public void bind(Injector.Builder builder) {
        builder.bind(InstanceLock.class, (directory, arguments, receiver) -> Optional.of(() -> {}));
    }
}
