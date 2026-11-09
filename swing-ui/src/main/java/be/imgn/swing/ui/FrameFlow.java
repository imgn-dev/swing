package be.imgn.swing.ui;

import static java.util.Objects.requireNonNull;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.Scope;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.ExitVetoes;
import be.imgn.swing.ui.internal.ApplicationIcon;
import be.imgn.swing.ui.internal.AwtWindow;
import be.imgn.swing.ui.internal.FrameControl;
import be.imgn.swing.ui.internal.SwingWindows;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowStateSupport;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Opens frame views in frames of their own, closes them, and follows the active frame. Used on the EDT. */
@Singleton
@ExposeAs({Frames.class, FrameFlow.class, FrameControl.class})
final class FrameFlow implements Frames, FrameControl {

    /** How far a frame is placed right of and below the frame of the same class opened last. */
    static final int CASCADE = 24;

    private final ViewBuilder builder;
    private final UiServices services;
    private final ExitVetoes vetoes;
    private final Injector root;
    private final DisposalScope application;
    private final List<WindowHost> closing = new ArrayList<>();

    @Inject
    FrameFlow(ViewBuilder builder, UiServices services, ExitVetoes vetoes, Injector root, Scope scope) {
        this.builder = builder;
        this.services = services;
        this.vetoes = vetoes;
        this.root = root;
        this.application = (DisposalScope) scope;
    }

    @Override
    public <V extends FrameView> V open(Class<V> type) {
        requireNonNull(type, "type");
        return type.cast(open(Optional.of(type), Optional.empty(), injector -> injector.getInstance(type))
                .view()
                .orElseThrow());
    }

    @Override
    public <F, V extends FrameView> V open(Class<F> factory, Function<? super F, ? extends V> create) {
        requireNonNull(factory, "factory");
        requireNonNull(create, "create");
        List<V> created = new ArrayList<>(1);
        open(Optional.empty(), Optional.empty(), injector -> {
            V view = create.apply(injector.getInstance(factory));
            created.add(view);
            return view;
        });
        return created.getFirst();
    }

    @Override
    public <V extends FrameView, E extends Enum<E>> V open(Class<V> type, E variant) {
        requireNonNull(type, "type");
        requireNonNull(variant, "variant");
        if (!type.equals(variant.getDeclaringClass().getEnclosingClass())) {
            throw new IllegalArgumentException(
                    "The variant " + variant + " is not a constant of an enum nested in " + type.getName());
        }
        return type.cast(open(Optional.of(type), Optional.of(variant), injector -> injector.getInstance(type))
                .view()
                .orElseThrow());
    }

    private ViewHost open(
            Optional<? extends Class<?>> type, Optional<? extends Enum<?>> variant, Function<Injector, ?> resolve) {
        Window window = services.windows().createFrame();
        // A frame never belongs to the view that opened it: it outlives it.
        WindowHost host = builder.openWindow(window, root, application, null, WindowHost.Kind.FRAME, "", "frame");
        if (window instanceof SwingWindows.FrameWindow real) {
            real.frame().setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            real.frame().addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    close(host);
                }
            });
        }
        type.ifPresent(known -> title(window, builder.table(known, variant)));
        icon(window);
        ViewHost view = build(host, type, variant, resolve, built -> {
            if (type.isEmpty()) {
                title(window, builder.table(built.type(), variant));
            }
        });
        host.stateKey(stateKey(view.type(), variant));
        place(host, (FrameView) view.view().orElseThrow());
        services.windows().show(window);
        track(host);
        return view;
    }

    /** Builds a frame view in its window: places its content, menu bar and toolbar in the window's root pane. */
    ViewHost build(
            WindowHost host,
            Optional<? extends Class<?>> type,
            Optional<? extends Enum<?>> variant,
            Function<Injector, ?> resolve,
            Consumer<ViewHost> resolved) {
        return builder.buildView(host, type, variant, resolve, null, built -> {
            resolved.accept(built);
            FrameView frame = (FrameView) built.view().orElseThrow();
            MainWindowFlow.place(host.window().rootPane(), frame.content(), frame.menuBar(), frame.toolBar());
        });
    }

    // Each variant of a frame view keeps its own window state, as a dialog's variant does.
    private static String stateKey(Class<?> type, Optional<? extends Enum<?>> variant) {
        return type.getName()
                + variant.map(constant ->
                                "." + constant.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                        .orElse("");
    }

    /** Sets a frame's title from its view's {@code title} resource, else the displayed name. */
    void title(Window window, TomlTable resources) {
        window.setTitle(
                resources.entries().get("title") instanceof TomlString title
                        ? title.value()
                        : services.application().displayedName());
    }

    /** Sets the application's icon on a window, if it has one. */
    void icon(Window window) {
        root.getInstance(ApplicationIcon.class).image().ifPresent(window::setIconImage);
    }

    /**
     * Places a frame: configures it, packs it, restores its saved state, then centers it on the active frame, else on
     * the screen, or cascades it from the frame of the same class opened last. A headless window has no bounds, but its
     * components' state is restored all the same.
     */
    void place(WindowHost host, FrameView view) {
        WindowStateSupport state = host.injector().getInstance(WindowStateSupport.class);
        if (!(host.window() instanceof SwingWindows.FrameWindow real)) {
            state.restore(view.restoreBounds());
            return;
        }
        JFrame frame = real.frame();
        // A fresh frame sits at (0, 0), so a sentinel location tells a configureFrame that moves it there apart from
        // one
        // that leaves it alone; one that asks the platform for the location has moved it as well.
        Point before = new Point(-1, -1);
        frame.setLocation(before);
        view.configureFrame(frame);
        boolean moved = frame.isLocationByPlatform() || !before.equals(frame.getLocation());
        frame.pack();
        boolean restored = state.restore(view.restoreBounds());
        if (moved) {
            return;
        }
        Optional<JFrame> sibling = sibling(host);
        if (sibling.isPresent()) {
            frame.setLocation(cascade(sibling.get(), frame));
        } else if (!restored) {
            frame.setLocationRelativeTo(activeFrame(host).orElse(null));
        }
    }

    // The frame of the same view class opened last, other than this one.
    private Optional<JFrame> sibling(WindowHost host) {
        if (host.isMain()) {
            return Optional.empty();
        }
        Optional<JFrame> sibling = Optional.empty();
        for (WindowHost other : services.registry().windows()) {
            if (other != host
                    && other.isFrame()
                    && other.stateKey().equals(host.stateKey())
                    && other.window() instanceof SwingWindows.FrameWindow real) {
                sibling = Optional.of(real.frame());
            }
        }
        return sibling;
    }

    private static Point cascade(JFrame sibling, JFrame frame) {
        Point location = new Point(sibling.getX() + CASCADE, sibling.getY() + CASCADE);
        GraphicsConfiguration configuration = sibling.getGraphicsConfiguration();
        if (configuration == null) {
            return location;
        }
        Rectangle screen = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        Rectangle usable = new Rectangle(
                screen.x + insets.left,
                screen.y + insets.top,
                screen.width - insets.left - insets.right,
                screen.height - insets.top - insets.bottom);
        if (!usable.contains(new Rectangle(location, frame.getSize()))) {
            return new Point(usable.x, usable.y);
        }
        return location;
    }

    private Optional<java.awt.Window> activeFrame(WindowHost except) {
        return activeHost()
                .filter(active -> active != except)
                .map(WindowHost::window)
                .filter(AwtWindow.class::isInstance)
                .map(window -> ((AwtWindow) window).awt());
    }

    /**
     * Follows the focus of a frame that was just shown, which the platform focuses: it becomes the active frame, and
     * whenever it gains the focus again.
     */
    void track(WindowHost host) {
        services.registry().focused(host);
        if (host.window() instanceof SwingWindows.FrameWindow real) {
            real.frame().addWindowFocusListener(new WindowAdapter() {
                @Override
                public void windowGainedFocus(WindowEvent event) {
                    services.registry().focused(host);
                }
            });
        }
    }

    /**
     * Returns the active frame: the frame that has the focus, else the frame that had it last, else the frame opened
     * last.
     *
     * @return the frame's host, empty when no frame is open
     */
    Optional<WindowHost> activeHost() {
        return services.registry().activeFrame();
    }

    private List<WindowHost> frames() {
        return services.registry().frames();
    }

    @Override
    public List<FrameView> views() {
        return frames().stream()
                .map(host -> host.views().getFirst().view())
                .flatMap(Optional::stream)
                .filter(FrameView.class::isInstance)
                .map(FrameView.class::cast)
                .toList();
    }

    @Override
    public Optional<FrameView> active() {
        return activeHost()
                .flatMap(host -> host.views().getFirst().view())
                .filter(FrameView.class::isInstance)
                .map(FrameView.class::cast);
    }

    @Override
    public boolean close(FrameView view) {
        return close(hostOf(view));
    }

    /**
     * Closes a frame: the main frame of a single-frame application, or the only frame of a multi-frame one, requests
     * exit; another asks the vetoes of its view scope, then of its window scope, then saves its state, disposes of it
     * and closes its scopes.
     *
     * @param host the frame
     * @return whether the frame closed
     */
    boolean close(WindowHost host) {
        if (closing.contains(host) || services.lifecycle().isShuttingDown() || !frames().contains(host)) {
            return false;
        }
        if (host.isMain() || (frames().size() == 1 && services.registry().main().isEmpty())) {
            services.lifecycle().requestExit();
            return false;
        }
        closing.add(host);
        try {
            for (ExitVeto veto : vetoesOf(host)) {
                if (!veto.canExit()) {
                    return false;
                }
            }
            discard(host);
            return true;
        } finally {
            closing.remove(host);
        }
    }

    @Override
    public void discard(WindowHost frame) {
        frame.injector().getInstance(WindowStateSupport.class).save();
        services.windows().dispose(frame.window());
        builder.close(frame);
    }

    @Override
    public boolean isMultiFrame() {
        return services.registry().main().isEmpty();
    }

    private List<ExitVeto> vetoesOf(WindowHost host) {
        List<ExitVeto> asked = new ArrayList<>();
        for (ViewHost view : host.views()) {
            asked.addAll(vetoes.of(view.scope()));
        }
        asked.addAll(vetoes.of(host.scope()));
        return asked;
    }

    @Override
    public void toFront(FrameView view) {
        WindowHost host = hostOf(view);
        host.window().toFront();
        services.registry().focused(host);
    }

    private WindowHost hostOf(FrameView view) {
        requireNonNull(view, "view");
        for (WindowHost host : frames()) {
            if (view.equals(host.views().getFirst().view().orElse(null))) {
                return host;
            }
        }
        throw new IllegalArgumentException(view + " is not in an open frame");
    }
}
