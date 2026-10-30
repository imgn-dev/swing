package be.imgn.swing.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.ApplicationDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Root injectors for tests, with the bindings the launch gives. */
final class TestInjectors {

    private TestInjectors() {}

    static Injector root(DisposalScope scope) {
        ApplicationDescriptor descriptor = new ApplicationDescriptor() {
            @Override
            public Class<?> type() {
                return TestInjectors.class;
            }

            @Override
            public String name() {
                return "Test";
            }

            @Override
            public Optional<String> vendor() {
                return Optional.empty();
            }

            @Override
            public Optional<String> qualifier() {
                return Optional.empty();
            }

            @Override
            public Optional<String> version() {
                return Optional.empty();
            }
        };
        DefaultApplicationInfo info = new DefaultApplicationInfo(descriptor);
        return Launcher.build(Launcher.rootBuilder(info, List.of(), Locale.ROOT, scope), info);
    }
}
