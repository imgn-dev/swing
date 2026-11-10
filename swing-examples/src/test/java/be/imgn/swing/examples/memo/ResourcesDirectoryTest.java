package be.imgn.swing.examples.memo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The generated opener of a package loaded from a directory names that directory, which hot reload watches. */
class ResourcesDirectoryTest {

    @Test
    void namesThePackagesDirectoryInTheClassOutput() {
        assertThat(new $$Resources().directory())
                .hasValueSatisfying(directory -> assertThat(directory.endsWith(Path.of("be/imgn/swing/examples/memo")))
                        .isTrue());
    }
}
