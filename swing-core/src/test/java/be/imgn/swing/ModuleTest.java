package be.imgn.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.module.ResolvedModule;
import org.junit.jupiter.api.Test;

class ModuleTest {

    // What the module reads, as resolved: its own requirements, and those they pass on with requires transitive.
    @Test
    void doesNotReadJavaDesktop() {
        var module = Scope.class.getModule();
        var resolved =
                module.getLayer().configuration().findModule(module.getName()).orElseThrow();

        assertThat(module.getName()).isEqualTo("be.imgn.swing");
        assertThat(resolved.reads()).extracting(ResolvedModule::name).doesNotContain("java.desktop");
    }
}
