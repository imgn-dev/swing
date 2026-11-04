package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SubscriberRegistrarTest {

    private final DisposalScope application = DisposalScope.root("application");
    private final Injector root = TestInjectors.root(application);
    private final Hosts hosts = new Hosts();
    private final SubscriberRegistrar registrar = new SubscriberRegistrar(root, application, hosts);
    private final EventBus bus = EventBus.create();
    private final List<String> received = new ArrayList<>();

    @AfterEach
    void close() {
        root.getInstance(DefaultLifecycle.class).close();
    }

    @Test
    void registersTheInstancesReportedBeforeTheBusInTheOrderTheyWereReported() {
        registrar.constructed(new Recorder("first", received), root);
        registrar.constructed(new Recorder("second", received), root);
        bus.publish("before");

        registrar.attach(bus);
        registrar.constructed(new Recorder("third", received), root);
        bus.publish("after");

        assertThat(received).containsExactly("first after", "second after", "third after");
    }

    @Test
    void ignoresAnInstanceWithoutSubscribers() {
        registrar.attach(bus);

        assertThatCode(() -> registrar.constructed(new Object(), root)).doesNotThrowAnyException();
    }

    @Test
    void unsubscribesWhenTheApplicationScopeCloses() {
        registrar.attach(bus);
        registrar.constructed(new Recorder("singleton", received), root);

        application.close();
        bus.publish("closed");

        assertThat(received).isEmpty();
    }

    @Test
    void unsubscribesWhenTheScopeOfItsHostCloses() {
        Injector host = root.childBuilder().build();
        DisposalScope view = application.child("view");
        hosts.register(host, view);
        registrar.attach(bus);
        registrar.constructed(new Recorder("view", received), host);
        bus.publish("open");

        view.close();
        bus.publish("closed");

        assertThat(received).containsExactly("view open");
    }

    @Test
    void rejectsASubscriberOfAnInjectorThatHostsNoScope() {
        Injector child = root.childBuilder().build();
        registrar.attach(bus);

        assertThatThrownBy(() -> registrar.constructed(new Recorder("lost", received), child))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hosts no window or view");
        bus.publish("event");
        assertThat(received).isEmpty();
    }

    @Test
    void rejectsASubscriberOfAnInjectorThatHostsNoScopeWhenItIsReportedDuringAHold() {
        Injector child = root.childBuilder().build();
        registrar.attach(bus);
        Runnable release = registrar.hold();

        assertThatThrownBy(() -> registrar.constructed(new Recorder("lost", received), child))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Recorder.class.getName())
                .hasMessageContaining("hosts no window or view");
        assertThatCode(release::run).doesNotThrowAnyException();
        bus.publish("event");
        assertThat(received).isEmpty();
    }

    @Test
    void holdsTheInstancesReportedDuringAHoldUntilItsRelease() {
        registrar.attach(bus);
        Runnable release = registrar.hold();
        registrar.constructed(new Recorder("first", received), root);
        registrar.constructed(new Recorder("second", received), root);
        bus.publish("held");

        release.run();
        registrar.constructed(new Recorder("third", received), root);
        bus.publish("released");

        assertThat(received).containsExactly("first released", "second released", "third released");
    }

    @Test
    void registersAtOnceAnInstanceReportedOnAnotherThreadDuringAHold() throws InterruptedException {
        registrar.attach(bus);
        Runnable release = registrar.hold();
        registrar.constructed(new Recorder("held", received), root);
        Thread other =
                Thread.ofPlatform().start(() -> registrar.constructed(new Recorder("elsewhere", received), root));
        other.join();
        bus.publish("held");

        release.run();
        bus.publish("released");

        assertThat(received).containsExactly("elsewhere held", "elsewhere released", "held released");
    }

    @Test
    void releasesAnInnerHoldOnItsOwn() {
        registrar.attach(bus);
        Runnable outer = registrar.hold();
        registrar.constructed(new Recorder("outer", received), root);
        Runnable inner = registrar.hold();
        registrar.constructed(new Recorder("inner", received), root);

        inner.run();
        bus.publish("inner released");
        outer.run();
        bus.publish("outer released");

        assertThat(received).containsExactly("inner inner released", "inner outer released", "outer outer released");
    }

    @Test
    void queuesTheInstancesOfAHoldReleasedBeforeTheBus() {
        Runnable release = registrar.hold();
        registrar.constructed(new Recorder("held", received), root);
        release.run();
        registrar.constructed(new Recorder("queued", received), root);

        registrar.attach(bus);
        bus.publish("attached");

        assertThat(received).containsExactly("held attached", "queued attached");
    }

    static final class Recorder {
        private final String name;
        private final List<String> received;

        Recorder(String name, List<String> received) {
            this.name = name;
            this.received = received;
        }

        @Subscribe
        void onEvent(String event) {
            received.add(name + " " + event);
        }
    }
}
