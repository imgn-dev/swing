package be.imgn.swing.internal;

/**
 * A settings record of the store's tests, bound and written by {@link SampleSettingsBinder}.
 *
 * @param size a size
 */
record SampleSettings(int size) {

    /** The size the binder refuses to write, so that a test makes a save fail. */
    static final int UNWRITABLE = 30;
}
