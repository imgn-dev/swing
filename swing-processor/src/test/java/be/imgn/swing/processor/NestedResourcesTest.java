package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.swing.ui.spi.ComponentInjector;
import be.imgn.swing.ui.spi.ComponentResources;
import be.imgn.toml.Toml;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The resources of the component fields of a view's component fields, in nested tables. */
class NestedResourcesTest {

    private static final String BASE_PANEL = """
            package q;

            import be.imgn.swing.ui.Exposed;
            import javax.swing.JLabel;
            import javax.swing.JPanel;

            public class BasePanel extends JPanel {
                private static final long serialVersionUID = 1L;

                @Exposed public final JLabel cityLabel = new JLabel();
            }
            """;

    private static final String ADDRESS_PANEL = """
            package q;

            import be.imgn.swing.Observable;
            import be.imgn.swing.Property;
            import be.imgn.swing.ui.Exposed;
            import javax.swing.JLabel;

            public final class AddressPanel extends BasePanel {
                private static final long serialVersionUID = 1L;

                @Exposed public final JLabel streetLabel = new JLabel();
                @Exposed public final JLabel zipLabel = new JLabel();
                @Exposed public final PhonePanel phone = new PhonePanel();
                @Exposed public JLabel missingLabel;
                @Exposed final JLabel packageLabel = new JLabel();
                public final JLabel plainLabel = new JLabel();
                @Exposed public final transient Observable<Boolean> valid = Property.of(true);
            }
            """;

    private static final String PHONE_PANEL = """
            package q;

            import be.imgn.swing.ui.Exposed;
            import javax.swing.JLabel;
            import javax.swing.JPanel;
            import javax.swing.JTextField;

            public final class PhonePanel extends JPanel {
                private static final long serialVersionUID = 1L;

                @Exposed public final JLabel label = new JLabel();
                @Exposed public final JTextField number = new JTextField();
            }
            """;

    private static final String CONTACT_DIALOG = """
            package p;

            import be.imgn.swing.ui.DialogView;
            import be.imgn.swing.ui.Exposed;
            import be.imgn.swing.ui.Variants;
            import java.util.Optional;
            import javax.swing.JComponent;
            import javax.swing.JPanel;

            public final class ContactDialog extends BaseDialog implements DialogView<Void> {
                @Exposed final q.AddressPanel office = new q.AddressPanel();

                public JComponent content() {
                    return new JPanel();
                }

                public Optional<Void> result() {
                    return Optional.empty();
                }

                @Variants
                public enum Kind { CREATE, EDIT }
            }
            """;

    private static final String BASE_DIALOG = """
            package p;

            import be.imgn.swing.ui.Exposed;

            public abstract class BaseDialog implements be.imgn.swing.ui.View {
                @Exposed final q.AddressPanel home = new q.AddressPanel();
            }
            """;

    private static Sources dialog(String resources) {
        return Sources.create()
                .source("q.BasePanel", BASE_PANEL)
                .source("q.AddressPanel", ADDRESS_PANEL)
                .source("q.PhonePanel", PHONE_PANEL)
                .source("p.BaseDialog", BASE_DIALOG)
                .source("p.ContactDialog", CONTACT_DIALOG)
                .resource("p/ContactDialog.toml", """
                        [variants.create]
                        [variants.edit]

                        """ + resources);
    }

    @Test
    void acceptsTablesOfTheComponentFieldsOfAComponentFieldToAnyDepth() {
        var compilation = dialog("""
                        [home]
                        title = "Home Address"

                        [home.streetLabel]
                        text = "S&treet"

                        [home.cityLabel]
                        text = "City"

                        [home.phone]
                        tool-tip-text = "Phone"

                        [home.phone.number]
                        columns = 12

                        [variants.edit.office.phone.label]
                        text = "Office phone"
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsANestedTableThatNamesNoExposedComponentField() {
        var compilation = dialog("""
                        [home.missing]
                        text = "Nope"

                        [home.plainLabel]
                        text = "Nope"

                        [home.valid]
                        text = "Nope"

                        [home.phone.nothing]
                        text = "Nope"

                        [variants.create.office.unknown]
                        text = "Nope"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining(
                        "ContactDialog.toml:4:7: [home.missing] names no @Exposed component field of q.AddressPanel");
        assertThat(compilation)
                .hadErrorContaining("ContactDialog.toml:7:7: [home.plainLabel] names no @Exposed component field of"
                        + " q.AddressPanel");
        assertThat(compilation)
                .hadErrorContaining(
                        "ContactDialog.toml:10:7: [home.valid] names no @Exposed component field of q.AddressPanel");
        assertThat(compilation)
                .hadErrorContaining("ContactDialog.toml:13:13: [home.phone.nothing] names no @Exposed component field"
                        + " of q.PhonePanel");
        assertThat(compilation)
                .hadErrorContaining("ContactDialog.toml:16:25: [office.unknown] names no @Exposed component field"
                        + " of q.AddressPanel");
    }

    @Test
    void rejectsANestedFieldTheViewsPackageCannotReach() {
        var compilation = dialog("""
                        [home.packageLabel]
                        text = "Nope"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("ContactDialog.toml:4:7: [home.packageLabel] names the field packageLabel of"
                        + " q.AddressPanel, which the generated code of p.ContactDialog can't reach");
    }

    @Test
    void rejectsANestedFieldOfASuperclassesResourcesTheViewsPackageCannotReach() {
        var compilation = Sources.create()
                .source("p.LocalPanel", """
                        package p;

                        public class LocalPanel extends javax.swing.JPanel {
                            @be.imgn.swing.ui.Exposed final javax.swing.JLabel streetLabel = new javax.swing.JLabel();
                        }
                        """)
                .source("p.SharedDialog", """
                        package p;

                        public abstract class SharedDialog implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed public final LocalPanel home = new LocalPanel();
                        }
                        """)
                .source("r.ContactDialog", """
                        package r;

                        public final class ContactDialog extends p.SharedDialog {
                            public javax.swing.JComponent content() {
                                return home;
                            }
                        }
                        """)
                .resource("p/SharedDialog.toml", """
                        [home.streetLabel]
                        text = "Street"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("SharedDialog.toml:1:7: [home.streetLabel] names the field streetLabel of"
                        + " p.LocalPanel, which the generated code of r.ContactDialog can't reach");
    }

    @Test
    void rejectsANestedFieldThatIsNotInheritedAcrossPackages() {
        var compilation = Sources.create()
                .source("p.PackagePanel", """
                        package p;

                        public class PackagePanel extends javax.swing.JPanel {
                            @be.imgn.swing.ui.Exposed final javax.swing.JLabel cityLabel = new javax.swing.JLabel();
                        }
                        """)
                .source("q.CityPanel", """
                        package q;

                        public final class CityPanel extends p.PackagePanel {}
                        """)
                .source("p.CityDialog", """
                        package p;

                        public final class CityDialog implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed final q.CityPanel home = new q.CityPanel();

                            public javax.swing.JComponent content() {
                                return home;
                            }
                        }
                        """)
                .resource("p/CityDialog.toml", """
                        [home.cityLabel]
                        text = "City"
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining(
                        "CityDialog.toml:1:7: [home.cityLabel] names no @Exposed component field of q.CityPanel");
    }

    @Test
    void reachesANestedFieldOfItsOwnPackageThatIsNotPublic() {
        var compilation = Sources.create()
                .source("q.BasePanel", BASE_PANEL)
                .source("q.AddressPanel", ADDRESS_PANEL)
                .source("q.PhonePanel", PHONE_PANEL)
                .source("q.Dialog", """
                        package q;

                        public final class Dialog implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed final AddressPanel home = new AddressPanel();

                            public javax.swing.JComponent content() {
                                return home;
                            }
                        }
                        """)
                .resource("q/Dialog.toml", """
                        [home.packageLabel]
                        text = "Package"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("q.$Dialog$$Injector")
                .contains("nested(nested(view.home, \"home\").packageLabel, \"home.packageLabel\")");
    }

    @Test
    void attachesTheDiagnosticsOfANestedTableToTheViewsOwnField() {
        var compilation = dialog("""
                        [office.streetLabel]
                        columns = 3

                        [office.phone.number]
                        columns = "wide"

                        [office.unknownLabel]
                        text = "Unknown"
                        """).compile();

        assertThat(compilation).failed().hadErrorCount(3);
        assertThat(compilation)
                .hadErrorContaining("columns is not a supported property of javax.swing.JLabel")
                .inFile("p/ContactDialog.java")
                .onLine(11);
        assertThat(compilation)
                .hadErrorContaining("columns must be a integer")
                .inFile("p/ContactDialog.java")
                .onLine(11);
        assertThat(compilation)
                .hadErrorContaining("[office.unknownLabel] names no @Exposed component field of q.AddressPanel")
                .inFile("p/ContactDialog.java")
                .onLine(11);
    }

    @Test
    void checksTheKeysOfANestedTableAsProperties() {
        var compilation = dialog("""
                        [home.streetLabel]
                        columns = 3
                        text = "&Two &Mnemonics"

                        [home.phone.number]
                        columns = "wide"

                        [variants.edit.home.zipLabel]
                        foreground = "red"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining(
                        "ContactDialog.toml:5:1: columns is not a supported property of javax.swing.JLabel");
        assertThat(compilation)
                .hadErrorContaining(
                        "ContactDialog.toml:6:8: The text \"&Two &Mnemonics\" has more than one & mnemonic");
        assertThat(compilation).hadErrorContaining("ContactDialog.toml:9:11: columns must be a integer");
        assertThat(compilation).hadErrorContaining("ContactDialog.toml:12:14: The color \"red\" is not");
    }

    @Test
    void checksTheTranslationsOfANestedTable() {
        var compilation = dialog("""
                        [home.streetLabel]
                        text = "Street"
                        """).resource("p/ContactDialog_fr.toml", """
                        [variants.create]
                        [variants.edit]

                        [home.zipLabel]
                        text = "Code postal"
                        """).compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("ContactDialog_fr.toml:5:1: The key home.zipLabel.text is not in the base file");
        assertThat(compilation).hadErrorContaining("ContactDialog_fr.toml lacks the key home.streetLabel.text");
    }

    @Test
    void generatesTheNestedPathsTheResourcesOfTheViewAndItsSuperclassesName() {
        var compilation = dialog("""
                        [home.streetLabel]
                        text = "Street"

                        [variants.edit.office.phone.label]
                        text = "Office phone"
                        """)
                .resource("p/ContactDialog.macos.toml", """
                        [home.phone.number]
                        columns = 10
                        """)
                .resource("p/BaseDialog.toml", """
                        [home.cityLabel]
                        text = "City"
                        """)
                .compile();

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("p.$ContactDialog$$Injector")
                .contains("resources.string(\"home.streetLabel\", \"text\").ifPresent(value -> resources.setText("
                        + "nested(nested(view.home, \"home\").streetLabel, \"home.streetLabel\"), value));")
                .contains("resources.string(\"office.phone.label\", \"text\").ifPresent(value -> resources.setText("
                        + "nested(nested(nested(view.office, \"office\").phone, \"office.phone\").label,"
                        + " \"office.phone.label\"), value));")
                .contains("resources.integer(\"home.phone.number\", \"columns\").ifPresent(value -> "
                        + "nested(nested(nested(view.home, \"home\").phone, \"home.phone\").number,"
                        + " \"home.phone.number\").setColumns(value));")
                .contains("resources.string(\"home.cityLabel\", \"text\")")
                .contains("ComponentResources.setTabTitle(nested(nested(view.home, \"home\").streetLabel,"
                        + " \"home.streetLabel\"), value)")
                .contains("private static <C> C nested(C component, String path)")
                .doesNotContain("\"home.zipLabel\"")
                .doesNotContain("\"office.streetLabel\"")
                .doesNotContain("\"home.phone.label\"");
        assertThat(compilation).generatedSourceFile("p.$ContactDialog$$State").doesNotContain("streetLabel");
    }

    @Test
    void keepsTheWindowStateOfTheViewsOwnFields() {
        var compilation = Sources.create()
                .source("q.StatePanel", """
                        package q;

                        import be.imgn.swing.ui.Exposed;
                        import javax.swing.JSplitPane;
                        import javax.swing.JTabbedPane;
                        import javax.swing.JTable;

                        public final class StatePanel extends javax.swing.JPanel {
                            private static final long serialVersionUID = 1L;

                            @Exposed public final JSplitPane nestedSplit = new JSplitPane();
                            @Exposed public final JTabbedPane nestedTabs = new JTabbedPane();
                            @Exposed public final JTable nestedTable = new JTable();
                        }
                        """)
                .source("p.StateView", """
                        package p;

                        public final class StateView implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed final javax.swing.JSplitPane main = new javax.swing.JSplitPane();
                            @be.imgn.swing.ui.Exposed final q.StatePanel panel = new q.StatePanel();

                            public javax.swing.JComponent content() {
                                return panel;
                            }
                        }
                        """)
                .resource("p/StateView.toml", """
                        [panel.nestedSplit]
                        tool-tip-text = "Split"

                        [panel.nestedTabs]
                        tool-tip-text = "Tabs"

                        [panel.nestedTable]
                        tool-tip-text = "Table"
                        """)
                .compile();

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("p.$StateView$$Injector")
                .contains("\"panel.nestedSplit\"")
                .contains("\"panel.nestedTabs\"")
                .contains("\"panel.nestedTable\"");
        assertThat(compilation)
                .generatedSourceFile("p.$StateView$$State")
                .contains("view.main")
                .doesNotContain("nestedSplit")
                .doesNotContain("nestedTabs")
                .doesNotContain("nestedTable");
    }

    @Test
    void followsOnlyTheNamedPathsOfAComponentThatHoldsItself() {
        var compilation = Sources.create()
                .source("p.Node", """
                        package p;

                        public class Node extends javax.swing.JPanel {
                            @be.imgn.swing.ui.Exposed Node child;
                            @be.imgn.swing.ui.Exposed final javax.swing.JLabel label = new javax.swing.JLabel();
                        }
                        """)
                .source("p.TreeView", """
                        package p;

                        public final class TreeView implements be.imgn.swing.ui.View {
                            @be.imgn.swing.ui.Exposed final Node root = new Node();

                            public javax.swing.JComponent content() {
                                return root;
                            }
                        }
                        """)
                .resource("p/TreeView.toml", """
                        [root.child.child.label]
                        text = "Deep"
                        """)
                .compile();

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("p.$TreeView$$Injector")
                .contains("\"root.child.child.label\"")
                .doesNotContain("\"root.child.child.child\"")
                .doesNotContain("\"root.label\"");
    }

    @Test
    void readsNoFileOfTheNestedFieldsClass() {
        var compilation = dialog("""
                        [home]
                        title = "Home"
                        """).resource("q/AddressPanel.toml", """
                        [streetLabel]
                        text = "Own"
                        unknown = 1
                        """).compile();

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("p.$ContactDialog$$Injector")
                .doesNotContain("\"home.streetLabel\"");
    }

    @Test
    void generatesNoHelperWithoutNestedTables() {
        var compilation = dialog("""
                        [home]
                        title = "Home"
                        """).compile();

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("p.$ContactDialog$$Injector")
                .doesNotContain("nested(");
    }

    @Test
    void theGeneratedInjectorSetsTheNestedPropertiesThroughTheEnclosingFields() throws Exception {
        var compilation = dialog("""
                        [home.streetLabel]
                        text = "S&treet"

                        [home.phone.number]
                        columns = 12

                        [variants.edit.office.phone.label]
                        text = "Office phone"
                        """).compile();
        assertThat(compilation).succeeded();
        var loader = new CompiledClasses(compilation);
        Object view = loader.loadClass("p.ContactDialog").getConstructor().newInstance();
        // The merged table of the edit variant, as the view builder gives it to the injector.
        var resources = new ComponentResources(
                Toml.parse("""
                                [home.streetLabel]
                                text = "S&treet"

                                [home.phone.number]
                                columns = 12

                                [office.phone.label]
                                text = "Office phone"
                                """).root(),
                path -> {
                    throw new AssertionError(path);
                },
                true);

        inject(injector(loader, "p.$ContactDialog$$Injector"), view, resources);

        Object home = field(view, "home");
        Assertions.assertThat(((JLabel) field(home, "streetLabel")).getText()).isEqualTo("Street");
        Assertions.assertThat(((JLabel) field(home, "streetLabel")).getDisplayedMnemonicIndex())
                .isEqualTo(1);
        Assertions.assertThat(((JTextField) field(field(home, "phone"), "number")).getColumns())
                .isEqualTo(12);
        Object office = field(view, "office");
        Assertions.assertThat(((JLabel) field(field(office, "phone"), "label")).getText())
                .isEqualTo("Office phone");
        Assertions.assertThat(((JLabel) field(office, "streetLabel")).getText()).isEmpty();
    }

    @Test
    void theGeneratedInjectorThrowsForANullNestedFieldTheResourcesName() throws Exception {
        var compilation = dialog("""
                        [home.missingLabel]
                        text = "Missing"
                        """).compile();
        assertThat(compilation).succeeded();
        var loader = new CompiledClasses(compilation);
        Object view = loader.loadClass("p.ContactDialog").getConstructor().newInstance();
        var injector = injector(loader, "p.$ContactDialog$$Injector");
        var unnamed = new ComponentResources(Toml.parse("[home]\n").root(), path -> null, true);
        var named = new ComponentResources(
                Toml.parse("[home.missingLabel]\ntext = \"Missing\"\n").root(), path -> null, true);

        // The resources that don't name the field at runtime don't read it.
        inject(injector, view, unnamed);
        assertThatThrownBy(() -> inject(injector, view, named))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("home.missingLabel")
                .hasMessageContaining("p.ContactDialog");
    }

    private static ComponentInjector<?> injector(ClassLoader loader, String name) throws Exception {
        return (ComponentInjector<?>) loader.loadClass(name).getConstructor().newInstance();
    }

    private static <V> void inject(ComponentInjector<V> injector, Object view, ComponentResources resources) {
        injector.inject(injector.type().cast(view), resources);
    }

    // The fields are public, or in the package of the classes the loader defines, which the test reaches by reflection.
    private static Object field(Object instance, String name) throws Exception {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    return field.get(instance);
                }
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Loads the classes of a compilation, from its class output. */
    private static final class CompiledClasses extends ClassLoader {

        private final Compilation compilation;

        CompiledClasses(Compilation compilation) {
            super(NestedResourcesTest.class.getClassLoader());
            this.compilation = compilation;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            JavaFileObject file = compilation
                    .generatedFile(StandardLocation.CLASS_OUTPUT, name.replace('.', '/') + ".class")
                    .orElseThrow(() -> new ClassNotFoundException(name));
            try (InputStream in = file.openInputStream()) {
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
