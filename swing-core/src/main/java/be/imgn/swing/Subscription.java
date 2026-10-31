package be.imgn.swing;

/** A listener's subscription to an {@link Observable}, released when its scope closes or when it is closed. */
public interface Subscription extends AutoCloseable {

    /** Unsubscribes the listener. Idempotent. */
    @Override
    void close();
}
