package be.imgn.swing.ui.spi;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.toml.Toml;
import org.junit.jupiter.api.Test;

/** The lookups of the resources by field path. */
class ComponentResourcesTest {

    private final ComponentResources resources = new ComponentResources(
            Toml.parse("""
                            [home]
                            title = "Home Address"

                            [home.streetLabel]
                            text = "S&treet"

                            [home.phone.label]
                            text = "Phone"
                            enabled = false
                            """).root(),
            path -> {
                throw new AssertionError(path);
            },
            true);

    @Test
    void readsAFieldOfTheView() {
        assertThat(resources.string("home", "title")).hasValue("Home Address");
    }

    @Test
    void readsTheNestedTableAFieldPathReaches() {
        assertThat(resources.string("home.streetLabel", "text")).hasValue("S&treet");
        assertThat(resources.string("home.phone.label", "text")).hasValue("Phone");
        assertThat(resources.bool("home.phone.label", "enabled")).hasValue(false);
    }

    @Test
    void givesNothingForAPathWithoutTable() {
        assertThat(resources.string("home.zipLabel", "text")).isEmpty();
        assertThat(resources.string("office.streetLabel", "text")).isEmpty();
        assertThat(resources.string("home.title", "text")).isEmpty();
        assertThat(resources.string("home.streetLabel", "font")).isEmpty();
    }
}
