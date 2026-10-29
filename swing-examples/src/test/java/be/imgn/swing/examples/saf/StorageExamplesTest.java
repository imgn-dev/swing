package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.ListModel;
import org.junit.jupiter.api.Test;

class StorageExamplesTest {

    @Test
    void localStorageExample1SavesAndLoadsItsMapInTheDataDirectory() throws Exception {
        try (AppTester<LocalStorageExample1> app = AppTester.start(LocalStorageExample1.class)) {
            LocalStorageExample1 example = app.application();
            LocalStorageExample1Commands commands = app.commands(LocalStorageExample1Commands.class);
            Path file = app.info().directories().data().resolve("map.toml");
            onEdt(() -> {
                example.put("Hello", "World");
                example.put("Bonjour", "Monde");
            });
            commands.addRandomEntry().execute();

            assertThat(commands.saveMap().execute()).isTrue();

            assertThat(Files.exists(file)).isTrue();
            assertThat(app.component(JTextField.class, "Message").getText()).isEqualTo("Saved: " + file);

            commands.clearMap().execute();
            assertThat(entries(app)).isEmpty();

            commands.loadMap().execute();

            assertThat(entries(app)).hasSize(3).startsWith("Hello = World", "Bonjour = Monde");
            assertThat(app.component(JTextField.class, "Message").getText()).isEqualTo("Loaded: " + file);
        }
    }

    private static List<String> entries(AppTester<LocalStorageExample1> app) throws Exception {
        List<String> entries = new ArrayList<>();
        onEdt(() -> {
            ListModel<?> model = app.component(JList.class).getModel();
            for (int index = 0; index < model.getSize(); index++) {
                entries.add(String.valueOf(model.getElementAt(index)));
            }
        });
        return entries;
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(action);
    }
}
