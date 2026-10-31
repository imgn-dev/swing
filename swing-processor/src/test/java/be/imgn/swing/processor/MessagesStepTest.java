package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

class MessagesStepTest {

    private static final String MESSAGES = """
            package p;

            @be.imgn.swing.Messages
            public interface CounterMessages {
                String count(int count);

                String confirmExit();
            }
            """;

    @Test
    void generatesTheImplementation() {
        var compilation = Sources.create()
                .source("p.CounterMessages", MESSAGES)
                .resource("p/CounterMessages.toml", """
                        count = "The count is: {count}."
                        confirmExit = "Do you really want to exit?"
                        """)
                .resource("p/CounterMessages_fr.toml", """
                        count = "Le compte est : {count,number,integer}."
                        confirmExit = "Voulez-vous vraiment quitter ?"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$CounterMessages$$Messages")
                .contains("@Singleton")
                .contains("@ExposeAs(CounterMessages.class)")
                .contains("public final class $CounterMessages$$Messages implements CounterMessages")
                .contains("$CounterMessages$$Messages(Resources resources, DisplayLocale locale)")
                .contains("this.count = MessagePattern.of(messages, \"count\", List.of(\"count\"), locale.locale());")
                .contains("public String count(int count) {\n        return this.count.format(count);");
        assertThat(compilation).generatedSourceFile("p.$$Resources").contains("return \"p\";");
    }

    @Test
    void namesANestedInterfaceAfterItsBinaryName() {
        var compilation = Sources.create()
                .source("p.CounterApp", """
                        package p;

                        public final class CounterApp {
                            @be.imgn.swing.Messages
                            interface CounterMessages {
                                String confirmExit();
                            }
                        }
                        """)
                .resource("p/CounterMessages.toml", "confirmExit = \"Exit?\"\n")
                .compile();

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("p.$CounterApp$CounterMessages$$Messages")
                .contains("implements CounterApp.CounterMessages");
    }

    @Test
    void rejectsAPlaceholderThatNamesNoParameter() {
        var compilation = Sources.create()
                .source("p.CounterMessages", MESSAGES)
                .resource("p/CounterMessages.toml", """
                        count = "The count is: {count}."
                        confirmExit = "Exit {file}?"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterMessages.toml:2:15: The placeholder {file} names no parameter");
    }

    @Test
    void rejectsAPatternThatDoesNotParse() {
        var compilation = Sources.create()
                .source("p.CounterMessages", MESSAGES)
                .resource("p/CounterMessages.toml", """
                        count = "The count is: {count,nonsense}."
                        confirmExit = "Exit?"
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("does not parse as a MessageFormat");
    }

    @Test
    void reportsAMissingTranslationAtTheBaseFilesKey() {
        var compilation = Sources.create()
                .source("p.CounterMessages", MESSAGES)
                .resource("p/CounterMessages.toml", """
                        count = "The count is: {count}."
                        confirmExit = "Exit?"
                        """)
                .resource("p/CounterMessages_fr.toml", "count = \"Le compte est : {count}.\"\n")
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("CounterMessages.toml:2:1: CounterMessages_fr.toml lacks the key confirmExit");
    }

    @Test
    void rejectsAMissingPatternAndAnUnknownKey() {
        var compilation = Sources.create()
                .source("p.CounterMessages", MESSAGES)
                .resource("p/CounterMessages.toml", """
                        count = "{count}"
                        unknown = "?"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("The message confirmExit has no pattern in CounterMessages.toml");
        assertThat(compilation).hadErrorContaining("CounterMessages.toml:2:1: The key unknown names no method");
    }
}
