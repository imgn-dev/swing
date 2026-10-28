package be.imgn.swing;

/**
 * Asked before the application exits, on the {@link UiExecutor}'s thread.
 *
 * <p>A veto is any {@code @IntoSet(ExitVeto.class)} singleton, or any bean of a window or view scope that implements
 * this interface, the application class and views included. Vetoes are asked from the innermost scope to the outermost;
 * within a scope, in the order their instances were constructed. The {@code @IntoSet} singletons, in the application
 * scope, are asked last, sorted by class name. An instance found both ways is asked once.
 */
public interface ExitVeto {

    /**
     * Returns whether the application may exit.
     *
     * @return {@code false} to cancel the shutdown
     */
    boolean canExit();
}
