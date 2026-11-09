package be.imgn.swing.desktop;

import be.imgn.inject.ConstructionListener;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Starts the document of each document view, once the view's build ends. */
@Singleton
@IntoSet(ConstructionListener.class)
final class DocumentStarter implements ConstructionListener {

    private final WindowRegistry registry;
    private final Documents documents;

    @Inject
    DocumentStarter(WindowRegistry registry, Documents documents) {
        this.registry = registry;
        this.documents = documents;
    }

    @Override
    public void constructed(Object instance, Injector injector) {
        if (!(instance instanceof DocumentView<?> view)) {
            return;
        }
        registry.viewOf(injector).ifPresent(host -> {
            Document document = injector.getInstance(Document.class);
            @Nullable Path opening = documents.opening();
            host.attach(() -> document.start(view, host, opening));
        });
    }
}
