package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class ApplicationStepTest {

    @Test
    void generatesTheDescriptor() {
        var compilation = Sources.create()
                .source("p.CounterApp", Sources.COUNTER)
                .resource("p/CounterApp.toml", "[commands.increment]\ntext = \"&Increment\"\n")
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$CounterApp$$Application")
                .contains("public final class $CounterApp$$Application implements ApplicationDescriptor")
                .contains("return CounterApp.class;")
                .contains("return \"Counter\";")
                .contains("return Optional.of(\"imgn\");")
                .contains("return Optional.of(\"be\");")
                .contains("return Optional.of(\"1.0\");");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/be.imgn.swing.ApplicationDescriptor")
                .contains("p.$CounterApp$$Application");
    }

    @Test
    void checksTheNamesWithGaiasRules() {
        var compilation = Sources.create().source("p.App", """
                        package p;

                        @be.imgn.swing.Application(name = "Coun/ter")
                        public final class App implements be.imgn.swing.ui.SingleFrame {
                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }
                        }
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("The name must not contain a control character or any of");
    }

    @Test
    void rejectsAQualifierWithoutVendor() {
        var compilation = Sources.create().source("p.App", """
                        package p;

                        @be.imgn.swing.Application(name = "Counter", qualifier = "be")
                        public final class App implements be.imgn.swing.ui.SingleFrame {
                            public javax.swing.JComponent content() {
                                return new javax.swing.JPanel();
                            }
                        }
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("A qualifier is only given with a vendor");
    }

    @Test
    void requiresSingleFrame() {
        var compilation = Sources.create().source("p.App", """
                        package p;

                        @be.imgn.swing.Application(name = "Counter")
                        public final class App {}
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("must implement be.imgn.swing.ui.SingleFrame");
    }
}
