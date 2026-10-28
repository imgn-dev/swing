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
 * Runs javac with the processor on the processor module path, where only the main descriptor's {@code provides} makes
 * it found.
 */
class ProcessorModuleTest {

    @TempDir
    Path directory;

    @Test
    void runsFromProcessorModulePath() throws IOException, InterruptedException {
        var modules = new ArrayList<Path>();
        modules.add(ProcessorRuntime.mainClasses());
        modules.addAll(ProcessorRuntime.requiredModules());

        var result = javac(modules);

        assertThat(result.exitCode()).as(result.description()).isZero();
    }

    @Test
    void failsWithoutTheProcessorModule() throws IOException, InterruptedException {
        var result = javac(ProcessorRuntime.requiredModules());

        assertThat(result.exitCode()).as(result.description()).isNotZero();
        assertThat(result.error()).contains(SwingProcessor.class.getName());
    }

    // -processor names the processor, so javac fails when the processor module path doesn't provide it; without it, a
    // processor that supports no annotation yet would never be run.
    private Subprocess.Result javac(List<Path> processorModulePath) throws IOException, InterruptedException {
        var sources = directory.resolve("sources");
        var moduleInfo = write(sources.resolve("module-info.java"), "module app {}\n");
        var source = write(sources.resolve("app/Main.java"), "package app; public class Main {}\n");
        return Subprocess.run(List.of(
                ProcessorRuntime.jdkTool("javac"),
                "-d",
                directory.resolve("classes").toString(),
                "--processor-module-path",
                processorModulePath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
                "-processor",
                SwingProcessor.class.getName(),
                moduleInfo.toString(),
                source.toString()));
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }
}
