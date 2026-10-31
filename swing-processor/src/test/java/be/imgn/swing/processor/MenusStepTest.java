package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import org.junit.jupiter.api.Test;

class MenusStepTest {

    @Test
    void generatesTheMenusClass() {
        var compilation = Sources.create()
                .source("p.CounterApp", Sources.COUNTER)
                .resource("p/CounterApp.toml", """
                        [commands.increment]
                        text = "&Increment"

                        [menus.file]
                        text = "&Fichier"

                        [menus.recent-files]
                        text = "&Recent"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.CounterAppMenus")
                .contains("@ViewScoped")
                .contains("public final class CounterAppMenus extends StandardMenus")
                .contains("CounterAppMenus(Ui ui)")
                .contains("this.recentFiles = menu(CounterApp.class, \"recent-files\");")
                .contains("public BoundMenu recentFiles()")
                .doesNotContain("file()");
    }

    @Test
    void rejectsAGeneratedNameThatAlreadyExists() {
        var compilation = Sources.create()
                .source("p.CounterApp", Sources.COUNTER)
                .source("p.CounterAppMenus", "package p;\n\npublic class CounterAppMenus {}\n")
                .resource("p/CounterApp.toml", """
                        [commands.increment]
                        text = "&Increment"

                        [menus.recent-files]
                        text = "&Recent"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("The class p.CounterAppMenus that p.CounterApp generates already exists");
    }

    @Test
    void rejectsAnIdThatDoesNotConvert() {
        var compilation = Sources.create()
                .source("p.CounterApp", Sources.COUNTER)
                .resource("p/CounterApp.toml", """
                        [commands.increment]
                        text = "&Increment"

                        [menus."2-recent"]
                        text = "&Recent"
                        """)
                .compile();

        assertThat(compilation).failed().hadErrorContaining("The menu id 2-recent does not convert");
    }
}
