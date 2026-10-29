package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import be.imgn.gaia.processing.testing.TestCompiler;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class SwingProcessorTest {

    // The test setup that milestone 1's steps build on: TestCompiler, the processor, and a resource on the class
    // output. With no step at work, it can only check that the setup compiles cleanly; the resource is named after no
    // class, since a file named after a class the processor generates nothing for is a warning.
    @Test
    void compilesWithAResourceOnTheClassOutput() {
        var compilation = TestCompiler.javac()
                .withProcessors(new SwingProcessor())
                .withOptions("-Werror")
                .withResource(StandardLocation.CLASS_OUTPUT, "p/notes.toml", "title = \"Notes\"\n")
                .compile(TestCompiler.source("p.NotesView", "package p; public class NotesView {}"));

        assertThat(compilation).succeeded().hadNoWarnings().generatedNoSourceFiles();
    }
}
