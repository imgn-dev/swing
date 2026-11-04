package be.imgn.swing.processor;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import be.imgn.gaia.processing.testing.Compilation;
import org.junit.jupiter.api.Test;

class SubscribersStepTest {

    @Test
    void acceptsTheSubscribersOfAWindowOrViewScope() {
        var compilation =
                Sources.create().source("p.Detail", """
                        package p;

                        @be.imgn.swing.ui.ViewScoped
                        public final class Detail {
                            @be.imgn.gaia.core.event.Subscribe
                            void onSelected(String event) {}
                        }
                        """).source("p.Status", """
                        package p;

                        @be.imgn.swing.ui.WindowScoped
                        public final class Status {
                            @be.imgn.swing.ui.OnEdt
                            void onProgress(Integer event) {}
                        }
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void acceptsAnEagerSingleton() {
        var compilation = subscriber("""
                @be.imgn.inject.Singleton
                @be.imgn.inject.Eager
                public final class Listener {
                    @be.imgn.gaia.core.event.OnVirtualThread
                    void onChanged(String event) {}
                }
                """);

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void acceptsAnInheritedScope() {
        var compilation =
                Sources.create().source("p.Base", """
                        package p;

                        @be.imgn.swing.ui.WindowScoped
                        public abstract class Base {}
                        """).source("p.Listener", """
                        package p;

                        public final class Listener extends Base {
                            @be.imgn.gaia.core.event.Subscribe
                            void onSelected(String event) {}
                        }
                        """).compile();

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    @Test
    void rejectsAClassWithoutScope() {
        var compilation = subscriber("""
                public final class Listener {
                    @be.imgn.gaia.core.event.Subscribe
                    void onSelected(String event) {}
                }
                """);

        assertThat(compilation)
                .failed()
                .hadErrorContaining("p.Listener declares subscribers but has no scope")
                .inFile("Listener.java")
                .onLine(3);
    }

    @Test
    void rejectsAClassWithoutScopeWhoseSubscribersUseASubscribingAnnotation() {
        var compilation = subscriber("""
                public final class Listener {
                    @be.imgn.swing.ui.OnEdt
                    void onProgress(Integer event) {}
                }
                """);

        assertThat(compilation).failed().hadErrorContaining("p.Listener declares subscribers but has no scope");
    }

    @Test
    void rejectsAClassWithoutScopeWhoseSubscribersUseASubscribingAnnotationOfTheSameCompilation() {
        var compilation = Sources.create()
                .source("p.Queued", """
                        package p;

                        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                        @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
                        @be.imgn.gaia.core.event.SubscribingAnnotation(executor = QueuedExecutor.class)
                        public @interface Queued {}
                        """)
                .source("p.QueuedExecutor", """
                        package p;

                        public final class QueuedExecutor implements java.util.concurrent.Executor {
                            @Override
                            public void execute(Runnable task) {
                                task.run();
                            }
                        }
                        """)
                .source("p.Listener", """
                        package p;

                        public final class Listener {
                            @Queued
                            void onQueued(String event) {}
                        }
                        """)
                .compile();

        assertThat(compilation)
                .failed()
                .hadErrorContaining("p.Listener declares subscribers but has no scope")
                .inFile("Listener.java")
                .onLine(3);
    }

    @Test
    void rejectsASingletonThatIsNotEager() {
        var compilation = subscriber("""
                @be.imgn.inject.Singleton
                public final class Listener {
                    @be.imgn.gaia.core.event.Subscribe
                    void onSelected(String event) {}
                }
                """);

        assertThat(compilation)
                .failed()
                .hadErrorContaining("p.Listener is a @Singleton that declares subscribers but is not @Eager")
                .inFile("Listener.java")
                .onLine(4);
    }

    @Test
    void ignoresAClassWithoutSubscribers() {
        var compilation = subscriber("""
                public final class Listener {
                    void onSelected(String event) {}
                }
                """);

        assertThat(compilation).succeeded().hadNoWarnings();
    }

    private static Compilation subscriber(String body) {
        return Sources.create().source("p.Listener", "package p;\n\n" + body).compile();
    }
}
