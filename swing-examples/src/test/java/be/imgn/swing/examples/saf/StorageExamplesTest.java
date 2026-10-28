package be.imgn.swing.examples.saf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.swing.testing.AppTester;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            assertThat(example.message.getText()).isEqualTo("Saved: " + file);

            commands.clearMap().execute();
            assertThat(entries(example)).isEmpty();

            commands.loadMap().execute();

            assertThat(entries(example)).hasSize(3).startsWith("Hello = World", "Bonjour = Monde");
            assertThat(example.message.getText()).isEqualTo("Loaded: " + file);
        }
    }

    private static List<String> entries(LocalStorageExample1 example) throws Exception {
        List<String> entries = new ArrayList<>();
        onEdt(() -> {
            ListModel<String> model = example.mapList.getModel();
            for (int index = 0; index < model.getSize(); index++) {
                entries.add(model.getElementAt(index));
            }
        });
        return entries;
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(action);
    }
}
