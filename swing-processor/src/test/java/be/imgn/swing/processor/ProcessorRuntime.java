package be.imgn.swing.processor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** What javac needs to run the processor as it ships: its main classes and the modules they require. */
final class ProcessorRuntime {

    private ProcessorRuntime() {}

    /**
     * Returns the main classes of the processor, as Surefire passes them in the {@code main.classes} property.
     *
     * @return the directory of the main classes
     */
    static Path mainClasses() {
        return Path.of(Objects.requireNonNull(
                System.getProperty("main.classes"), "The main.classes property must name the main classes"));
    }

    /**
     * Returns the path of a tool of the JDK the tests run on.
     *
     * @param name the tool's name, such as {@code javac}
     * @return the tool's path
     */
    static String jdkTool(String name) {
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

    /**
     * Returns the locations of the modules the main descriptor requires, directly or not, outside the JDK. Static
     * requirements are left out: they are needed to compile the processor, not to run it.
     *
     * @return the locations, main classes excluded
     * @throws IOException if the main descriptor can't be read
     */
    static List<Path> requiredModules() throws IOException {
        Set<Path> locations = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<ModuleDescriptor.Requires> pending =
                new ArrayDeque<>(mainDescriptor().requires());
        while (!pending.isEmpty()) {
            var requires = pending.pop();
            if (requires.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC)
                    || !seen.add(requires.name())) {
                continue;
            }
            var module = ModuleLayer.boot()
                    .configuration()
                    .findModule(requires.name())
                    .map(ResolvedModule::reference)
                    .orElseThrow(() -> new IllegalStateException("Module " + requires.name() + " is not resolved"));
            var location = module.location().orElseThrow();
            if (!"jrt".equals(location.getScheme())) {
                locations.add(Path.of(location));
                pending.addAll(module.descriptor().requires());
            }
        }
        return List.copyOf(locations);
    }

    private static ModuleDescriptor mainDescriptor() throws IOException {
        try (InputStream in = Files.newInputStream(mainClasses().resolve("module-info.class"))) {
            return ModuleDescriptor.read(in);
        }
    }
}
