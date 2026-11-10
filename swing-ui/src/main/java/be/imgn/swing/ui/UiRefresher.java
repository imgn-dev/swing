package be.imgn.swing.ui;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.Refresher;
import be.imgn.swing.ui.internal.AppliedLocale;
import be.imgn.swing.ui.internal.RefreshPhase;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import java.awt.ComponentOrientation;
import java.util.List;
import java.util.Locale;
import javax.swing.JRootPane;

/**
 * Refreshes the open windows once what resources give has changed, on the EDT: for each window, in the order they
 * opened, and each of its views, the component resources, the title, the commands, the menus and the code's renders;
 * after a change of the locale, Swing's default locale and the windows' component orientation too.
 */
@Singleton
@ExposeAs(Refresher.class)
final class UiRefresher implements Refresher {

    private final ViewBuilder builder;
    private final UiServices services;
    private final AppliedLocale applied;

    @Inject
    UiRefresher(ViewBuilder builder, UiServices services, AppliedLocale applied) {
        this.builder = builder;
        this.services = services;
        this.applied = applied;
    }

    @Override
    public void refresh() {
        Locale locale = services.resources().locale();
        boolean localeChanged = applied.apply(locale);
        for (WindowHost window : services.registry().windows()) {
            List<ViewHost> views = window.views();
            for (ViewHost view : views) {
                builder.reinject(view);
                title(window, view);
            }
            // Each phase for every view of the window before the next: the shortcuts are the window's, so that all of
            // them leave before any registers again, and commands that swap shortcuts never collide on the way.
            for (RefreshPhase phase : RefreshPhase.values()) {
                views.forEach(view -> view.refresh(phase));
            }
            JRootPane rootPane = window.window().rootPane();
            if (localeChanged) {
                rootPane.applyComponentOrientation(ComponentOrientation.getOrientation(locale));
            }
            rootPane.revalidate();
            rootPane.repaint();
        }
    }

    // A title the view set itself stays; only the one the resources gave follows them.
    private void title(WindowHost window, ViewHost view) {
        if (window.views().getFirst() != view) {
            return;
        }
        window.resourceTitle()
                .filter(title -> title.equals(window.window().title()))
                .ifPresent(ignored ->
                        window.resourceTitle(FrameFlow.titleOf(builder.table(view.type(), view.variant()), services)));
    }
}
