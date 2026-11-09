package be.imgn.swing.desktop;

import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.internal.WindowRegistry;

/** Starts the document of each document view, once the view's build ends. */
@Singleton
@IntoSet(ConstructionListener.class)
final class DocumentStarter implements ConstructionListener {

    private final WindowRegistry registry;

    @Inject
    DocumentStarter(WindowRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void constructed(Object instance, Injector injector) {
        if (!(instance instanceof DocumentView<?> view)) {
            return;
        }
        registry.viewOf(injector).ifPresent(host -> {
            Document document = injector.getInstance(Document.class);
            host.attach(() -> document.start(view, host));
        });
    }
}
