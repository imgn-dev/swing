package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.inject.Provider;
import be.imgn.inject.TypeLiteral;
import be.imgn.inject.context.BeanDefinition;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.ui.Window;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Image;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The attachments made during a view's build: held for its end on the thread that builds it, run at once elsewhere. */
class ViewHostTest {

    private final DisposalScope scope = DisposalScope.root("window");
    private final List<String> attached = new CopyOnWriteArrayList<>();

    @AfterEach
    void close() {
        scope.close();
    }

    @Test
    void holdsTheAttachmentsMadeOnTheThreadThatBuildsTheViewUntilItsBuildEnds() throws Exception {
        EventQueue.invokeAndWait(() -> {
            ViewHost view = newView();

            view.attach(() -> attached.add("during the build"));
            assertThat(attached).isEmpty();

            view.built();
            view.attach(() -> attached.add("after the build"));
        });

        assertThat(attached).containsExactly("during the build", "after the build");
    }

    @Test
    void runsAtOnceAnAttachmentMadeOnAnotherThreadDuringTheBuild() throws Exception {
        AtomicReference<ViewHost> building = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> building.set(newView()));
        ViewHost view = Objects.requireNonNull(building.get());

        Thread other = Thread.ofPlatform().start(() -> view.attach(() -> attached.add("elsewhere")));
        other.join();
        assertThat(attached).containsExactly("elsewhere");

        EventQueue.invokeAndWait(view::built);
        assertThat(attached).containsExactly("elsewhere");
    }

    // Called on the EDT, which then builds the view.
    private ViewHost newView() {
        Injector injector = new UnusedInjector();
        WindowHost window =
                new WindowHost(new FakeWindow(), injector, scope, null, WindowHost.Kind.MAIN, "test", new UiMode(true));
        return new ViewHost(Object.class, injector, scope.child("view"), window, null);
    }

    // The hosts only keep their injector.
    private static final class UnusedInjector implements Injector {
        @Override
        public <T> T getInstance(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getInstance(Key<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getInstance(TypeLiteral<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Provider<T> getProvider(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Provider<T> getProvider(TypeLiteral<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Provider<T> getProvider(Key<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BeanDefinition> getBeanDefinition(Key<?> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BeanDefinition> getBeanDefinitionsByStereotype(Class<? extends Annotation> stereotype) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChildBuilder childBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChildBuilder scopedChildBuilder(Class<? extends Annotation> scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }

    private static final class FakeWindow implements Window {
        private final JRootPane rootPane = new JRootPane();

        @Override
        public JRootPane rootPane() {
            return rootPane;
        }

        @Override
        public void setTitle(String title) {}

        @Override
        public String title() {
            return "";
        }

        @Override
        public void setIconImage(Image image) {}

        @Override
        public void focus(Component component) {}

        @Override
        public void toFront() {}
    }
}
