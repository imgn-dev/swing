package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.inject.Injector;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.UiExecutor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    private static final UiExecutor NOW = new UiExecutor() {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isUiThread() {
            return true;
        }
    };

    private final List<Integer> exits = new ArrayList<>();
    private final ExitVetoes vetoes = new ExitVetoes();
    private final DisposalScope application = DisposalScope.root("application");
    private final Injector injector = TestInjectors.root(application);
    private final DefaultLifecycle lifecycle =
            new DefaultLifecycle(NOW, vetoes, new TaskRunner(NOW, failure -> {}), injector, application, exits::add);

    @Test
    void asksTheVetoesFromTheInnermostScope() {
        DisposalScope window = application.child("window");
        DisposalScope view = window.child("view");
        List<String> asked = new ArrayList<>();
        vetoes.register(window, veto(asked, "window", true));
        vetoes.register(view, veto(asked, "view 1", true));
        vetoes.register(view, veto(asked, "view 2", true));

        lifecycle.requestExit(3);

        assertThat(asked).containsExactly("view 1", "view 2", "window");
        assertThat(exits).containsExactly(3);
        assertThat(application.isClosed()).isTrue();
    }

    @Test
    void stopsAtTheFirstVetoThatRefuses() {
        DisposalScope view = application.child("view");
        List<String> asked = new ArrayList<>();
        vetoes.register(view, veto(asked, "first", false));
        vetoes.register(view, veto(asked, "second", true));
        List<String> cancelled = new ArrayList<>();
        lifecycle.onCancel(() -> cancelled.add("cancelled"));

        lifecycle.requestExit();

        assertThat(asked).containsExactly("first");
        assertThat(cancelled).containsExactly("cancelled");
        assertThat(exits).isEmpty();
        assertThat(lifecycle.isShuttingDown()).isFalse();
    }

    @Test
    void forgetsTheVetoesOfAClosedScope() {
        DisposalScope view = application.child("view");
        List<String> asked = new ArrayList<>();
        vetoes.register(view, veto(asked, "view", false));

        view.close();
        lifecycle.requestExit();

        assertThat(asked).isEmpty();
        assertThat(exits).containsExactly(0);
    }

    @Test
    void ignoresARequestAfterTheShutdown() {
        lifecycle.requestExit();
        lifecycle.requestExit();

        assertThat(exits).containsExactly(0);
    }

    private static ExitVeto veto(List<String> asked, String name, boolean answer) {
        return () -> {
            asked.add(name);
            return answer;
        };
    }
}
