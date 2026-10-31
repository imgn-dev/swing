package be.imgn.swing.processor;

import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/** Compiles sources and resources with swing's processor. */
final class Sources {

    static final String COUNTER = """
            package p;

            import be.imgn.swing.Application;
            import be.imgn.swing.Property;
            import be.imgn.swing.ui.Command;
            import be.imgn.swing.ui.Exposed;
            import be.imgn.swing.ui.SingleFrame;
            import javax.swing.JComponent;
            import javax.swing.JLabel;

            @Application(name = "Counter", vendor = "imgn", qualifier = "be", version = "1.0")
            public final class CounterApp implements SingleFrame {
                final Property<Integer> count = Property.of(0);
                @Exposed JLabel label = new JLabel();

                @Override
                public JComponent content() {
                    return label;
                }

                @Command
                void increment() {
                    count.set(count.get() + 1);
                }
            }
            """;

    private final Map<String, String> sources = new LinkedHashMap<>();
    private final Map<String, Object> resources = new LinkedHashMap<>();

    static Sources create() {
        return new Sources();
    }

    Sources source(String qualifiedName, String text) {
        sources.put(qualifiedName, text);
        return this;
    }

    Sources resource(String path, String text) {
        resources.put(path, text);
        return this;
    }

    Sources resource(String path, byte[] bytes) {
        resources.put(path, bytes);
        return this;
    }

    Compilation compile() {
        TestCompiler compiler =
                TestCompiler.javac().withProcessors(new SwingProcessor()).withOptions("-Xlint:all,-processing");
        for (Map.Entry<String, Object> resource : resources.entrySet()) {
            compiler = resource.getValue() instanceof String text
                    ? compiler.withResource(StandardLocation.CLASS_OUTPUT, resource.getKey(), text)
                    : compiler.withResource(
                            StandardLocation.CLASS_OUTPUT, resource.getKey(), (byte[]) resource.getValue());
        }
        List<JavaFileObject> files = new ArrayList<>();
        sources.forEach((name, text) -> files.add(TestCompiler.source(name, text)));
        return compiler.compile(files);
    }
}
