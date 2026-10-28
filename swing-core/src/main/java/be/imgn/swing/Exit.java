package be.imgn.swing;

/** Ends the JVM at the end of the shutdown. Core's default calls {@link System#exit(int)}. */
public interface Exit {

    /**
     * Ends the application.
     *
     * @param status the exit status
     */
    void exit(int status);
}
