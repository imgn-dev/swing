package be.imgn.swing.testing;

import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.swing.Arguments;
import be.imgn.swing.desktop.Notifications;
import be.imgn.swing.desktop.OpenFilesHandler;
import be.imgn.swing.desktop.SecondInstanceHandler;
import java.nio.file.Path;
import java.util.List;

/**
 * The tester's part that touches the types of swing-desktop, which it creates only when the service loader finds the
 * desktop's extension: without swing-desktop, this class is never loaded.
 */
final class DesktopFakes {

    private final FakeNotifications notifications = new FakeNotifications();

    void bind(Injector.Builder builder) {
        builder.bind(Notifications.class, notifications);
    }

    FakeNotifications notifications() {
        return notifications;
    }

    // Called on the EDT.
    static void launchAgain(Injector root, List<String> arguments) {
        if (root.getBeanDefinition(Key.of(SecondInstanceHandler.class)).isEmpty()) {
            throw new IllegalStateException("The application has no SecondInstanceHandler: it is not single-instance");
        }
        root.getInstance(SecondInstanceHandler.class).launched(new Arguments(arguments));
    }

    // Called on the EDT.
    static void openFiles(Injector root, List<Path> files) {
        if (root.getBeanDefinition(Key.of(OpenFilesHandler.class)).isEmpty()) {
            throw new IllegalStateException("The application has no OpenFilesHandler");
        }
        root.getInstance(OpenFilesHandler.class).open(files);
    }
}
