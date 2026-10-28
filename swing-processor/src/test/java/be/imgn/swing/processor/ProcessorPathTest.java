package be.imgn.swing.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Looks the processor up on the processor path the way javac does: with the service loader, over a class path of the
 * main classes and the modules they require, where only {@code META-INF/services} makes it found.
 */
class ProcessorPathTest {

    // The lookup runs in another process: in this one, the class loaders hand the processor's package to this test
    // module, whose providers a class path lookup skips.
    private static final String LOOKUP = """
            import java.util.ServiceLoader;
            import javax.annotation.processing.Processor;

            class Lookup {
                public static void main(String[] args) {
                    ServiceLoader.load(Processor.class).stream()
                            .map(provider -> provider.type().getName())
                            .forEach(System.out::println);
                }
            }
            """;

    @TempDir
    Path directory;

    @Test
    void isFoundOnTheProcessorPath() throws IOException, InterruptedException {
        var classPath = new ArrayList<Path>();
        classPath.add(ProcessorRuntime.mainClasses());
        classPath.addAll(ProcessorRuntime.requiredModules());

        var result = lookup(classPath);

        assertThat(result.exitCode()).as(result.description()).isZero();
        assertThat(result.output().lines()).containsExactly(SwingProcessor.class.getName());
    }

    @Test
    void isNotFoundWithoutTheMainClasses() throws IOException, InterruptedException {
        var result = lookup(ProcessorRuntime.requiredModules());

        assertThat(result.exitCode()).as(result.description()).isZero();
        assertThat(result.output().lines()).isEmpty();
    }

    private Subprocess.Result lookup(List<Path> classPath) throws IOException, InterruptedException {
        var lookup = Files.writeString(directory.resolve("Lookup.java"), LOOKUP);
        return Subprocess.run(List.of(
                ProcessorRuntime.jdkTool("java"),
                "--class-path",
                classPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
                lookup.toString()));
    }
}
