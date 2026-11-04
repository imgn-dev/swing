package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import java.util.List;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class SettingsStepTest {

    @Test
    void generatesTheDescriptor() {
        var compilation = Sources.create().source("p.EditorSettings", """
                        package p;

                        import be.imgn.swing.Settings;
                        import be.imgn.toml.TomlConfig;
                        import be.imgn.toml.TomlKey;
                        import org.jspecify.annotations.Nullable;

                        @Settings("editor")
                        @TomlConfig
                        public record EditorSettings(
                                int fontSize,
                                boolean wordWrap,
                                @Nullable String fontName,
                                @TomlKey("theme") @Nullable String colorTheme) {

                            public EditorSettings() {
                                this(14, true, null, null);
                            }
                        }
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$EditorSettings$$Settings")
                .contains("public final class $EditorSettings$$Settings implements SettingsDescriptor<EditorSettings>")
                .contains("public Class<EditorSettings> type()")
                .contains("return EditorSettings.class;")
                .contains("return \"editor\";")
                .contains("return new EditorSettings();")
                .contains("return Set.of(\"font-name\", \"theme\");");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/be.imgn.swing.spi.SettingsDescriptor")
                .contains("p.$EditorSettings$$Settings");
    }

    @Test
    void namesTheDescriptorOfANestedRecordAfterItsEnclosingTypes() {
        var compilation = Sources.create().source("p.Outer", """
                        package p;

                        public final class Outer {
                            @be.imgn.swing.Settings("nested")
                            @be.imgn.toml.TomlConfig
                            public record Nested(int size) {
                                public Nested() {
                                    this(1);
                                }
                            }
                        }
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$Outer$Nested$$Settings")
                .contains("implements SettingsDescriptor<Outer.Nested>")
                .contains("return new Outer.Nested();")
                .contains("return Set.of();");
    }

    @Test
    void rejectsATypeThatIsNotARecord() {
        var compilation = Sources.create().source("p.EditorSettings", """
                        package p;

                        @be.imgn.swing.Settings("editor")
                        @be.imgn.toml.TomlConfig
                        public final class EditorSettings {}
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("@Settings must annotate a record");
    }

    @Test
    void requiresTomlConfig() {
        var compilation = Sources.create().source("p.EditorSettings", """
                        package p;

                        @be.imgn.swing.Settings("editor")
                        public record EditorSettings(int size) {
                            public EditorSettings() {
                                this(1);
                            }
                        }
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("must also be annotated @be.imgn.toml.TomlConfig");
    }

    @Test
    void requiresAConstructorWithoutParameter() {
        var compilation = Sources.create().source("p.EditorSettings", """
                        package p;

                        @be.imgn.swing.Settings("editor")
                        @be.imgn.toml.TomlConfig
                        public record EditorSettings(int size) {}
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("must have a public constructor with no parameter");
    }

    @Test
    void requiresThatConstructorToBePublic() {
        var compilation = Sources.create().source("p.EditorSettings", """
                        package p;

                        @be.imgn.swing.Settings("editor")
                        @be.imgn.toml.TomlConfig
                        public record EditorSettings(int size) {
                            EditorSettings() {
                                this(1);
                            }
                        }
                        """).compile();

        assertThat(compilation).failed().hadErrorContaining("must have a public constructor with no parameter");
    }

    @Test
    void rejectsAnIdThatIsNotLowerCaseKebabCase() {
        for (String id : List.of("", "Editor", "editor_settings", "-editor", "editor-", "editor--settings", "é")) {
            var compilation = Sources.create()
                    .source("p.EditorSettings", """
                            package p;

                            @be.imgn.swing.Settings("%s")
                            @be.imgn.toml.TomlConfig
                            public record EditorSettings(int size) {
                                public EditorSettings() {
                                    this(1);
                                }
                            }
                            """.formatted(id))
                    .compile();

            assertThat(compilation).as(id).failed().hadErrorContaining("is not lower-case kebab case");
        }
    }

    @Test
    void acceptsGroupsOfLettersAndDigits() {
        var compilation = Sources.create().source("p.EditorSettings", """
                        package p;

                        @be.imgn.swing.Settings("editor-2-x9")
                        @be.imgn.toml.TomlConfig
                        public record EditorSettings(int size) {
                            public EditorSettings() {
                                this(1);
                            }
                        }
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsTwoRecordsWithTheSameId() {
        String record = """
                package p;

                @be.imgn.swing.Settings("editor")
                @be.imgn.toml.TomlConfig
                public record %1$s(int size) {
                    public %1$s() {
                        this(1);
                    }
                }
                """;
        var compilation = Sources.create()
                .source("p.First", record.formatted("First"))
                .source("p.Second", record.formatted("Second"))
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("The settings records p.First and p.Second have the same id editor");
    }
}
