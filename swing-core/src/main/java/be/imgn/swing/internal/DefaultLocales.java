package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.DisplayLocale;
import be.imgn.swing.Locales;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Resources;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.spi.MessagesDescriptor;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** The display locale, changed on the {@link UiExecutor}, and saved through the {@link LocaleFile}. */
@Singleton
public final class DefaultLocales implements Locales {

    private final Locale launch;
    private final ResourceState state;
    private final Resources resources;
    private final UiExecutor executor;
    private final Refresher refresher;
    private final LocaleFile file;
    private final Property<Locale> current;
    private final Map<ResourceState.Localized, Object> messages = new ConcurrentHashMap<>();
    // Core's executor runs a change on the calling thread: two threads may change the locale at once.
    private final Object changing = new Object();
    private @Nullable Map<Class<?>, MessagesDescriptor> descriptors;

    @Inject
    DefaultLocales(
            DisplayLocale launch,
            ResourceState state,
            Resources resources,
            UiExecutor executor,
            Refresher refresher,
            LocaleFile file) {
        this.launch = launch.locale();
        this.state = state;
        this.resources = resources;
        this.executor = executor;
        this.refresher = refresher;
        this.file = file;
        this.current = Property.of(this.launch);
    }

    @Override
    public Observable<Locale> current() {
        return current;
    }

    @Override
    public void set(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        executor.execute(() -> {
            synchronized (changing) {
                if (change(locale)) {
                    file.save(locale);
                }
            }
        });
    }

    @Override
    public void reset() {
        executor.execute(() -> {
            synchronized (changing) {
                change(launch);
                file.save(null);
            }
        });
    }

    private boolean change(Locale locale) {
        if (locale.equals(current.get())) {
            return false;
        }
        state.locale(locale);
        refresher.refresh();
        current.set(locale);
        return true;
    }

    @Override
    public <M> M messages(Class<M> type, Locale locale) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(locale, "locale");
        MessagesDescriptor descriptor = descriptors().get(type);
        if (descriptor == null) {
            throw new IllegalArgumentException(type.getName()
                    + " is not a @Messages interface compiled by swing's processor; on the module path, register the"
                    + " services with gaia's register-services goal");
        }
        return type.cast(messages.computeIfAbsent(
                new ResourceState.Localized(type, locale), key -> descriptor.create(resources, locale)));
    }

    private synchronized Map<Class<?>, MessagesDescriptor> descriptors() {
        Map<Class<?>, MessagesDescriptor> found = descriptors;
        if (found == null) {
            found = new ConcurrentHashMap<>();
            for (MessagesDescriptor descriptor : ServiceLoader.load(MessagesDescriptor.class)) {
                found.putIfAbsent(descriptor.type(), descriptor);
            }
            descriptors = found;
        }
        return found;
    }

    /** Reads the saved choice, which becomes the starting locale, before anything reads resources. */
    void load() {
        file.load().ifPresent(locale -> {
            state.locale(locale);
            current.set(locale);
        });
    }
}
