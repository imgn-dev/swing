package be.imgn.swing.flatlaf;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Injector;
import be.imgn.swing.Appearance;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.internal.DefaultApplicationInfo;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Launcher;
import be.imgn.swing.internal.ResourceState;
import be.imgn.swing.spi.ApplicationDescriptor;
import be.imgn.swing.ui.internal.Edt;
import com.formdev.flatlaf.FlatLaf;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// Run on the class path, where the service loader finds the opener of the tests' resources in META-INF/services.
class FlatLafTokensTest {

    private final DisposalScope application = DisposalScope.root("application");
    private final Injector root = root();

    private Injector root() {
        ApplicationDescriptor descriptor = new ApplicationDescriptor() {
            @Override
            public Class<?> type() {
                return FlatLafTokensTest.class;
            }

            @Override
            public String name() {
                return "Tokens";
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
        return Launcher.build(Launcher.rootBuilder(info, List.of(), Locale.ROOT, application), info);
    }

    @AfterEach
    void close() {
        root.close();
        application.close();
    }

    @Test
    void givesFlatLafTheTokensOfEachAppearance() {
        FlatLafInstaller installer = new FlatLafInstaller(
                root.getInstance(ApplicationInfo.class),
                Platform.current(),
                root.getInstance(Resources.class),
                new DarkMode(command -> Optional.empty()),
                property -> null);

        Edt.invokeAndWait(() -> installer.install(Appearance.LIGHT));
        assertThat(UIManager.getInt("Component.arc")).isEqualTo(8);
        assertThat(FlatLaf.isLafDark()).isFalse();

        root.getInstance(ResourceState.class).appearance(Appearance.DARK);
        Edt.invokeAndWait(() -> installer.install(Appearance.DARK));

        assertThat(UIManager.getInt("Component.arc")).isEqualTo(20);
        assertThat(FlatLaf.isLafDark()).isTrue();
    }
}
