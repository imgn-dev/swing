package be.imgn.swing.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.spi.ApplicationDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** Root injectors for tests, with the bindings the launch gives. */
final class TestInjectors {

    private TestInjectors() {}

    static Injector root(DisposalScope scope) {
        return root(scope, builder -> {});
    }

    static Injector root(DisposalScope scope, Consumer<Injector.Builder> bindings) {
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
        Injector.Builder builder = Launcher.rootBuilder(info, List.of(), Locale.ROOT, scope);
        bindings.accept(builder);
        return Launcher.build(builder, info);
    }
}
