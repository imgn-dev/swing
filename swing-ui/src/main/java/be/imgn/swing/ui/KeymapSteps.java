package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.StartupStep;
import be.imgn.swing.ui.internal.KeymapFile;

/** Reads the keymap before the first window, and writes it last, since the shutdown cancels the tasks still running. */
final class KeymapSteps {

    private KeymapSteps() {}

    /** Reads the saved keymap, after the window state. */
    @Singleton
    @IntoSet(StartupStep.class)
    static final class Load implements StartupStep {

        private static final int ORDER = 260;

        private final Keymap keymap;

        @Inject
        Load(Keymap keymap) {
            this.keymap = keymap;
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public void run() {
            keymap.load();
        }
    }

    /** Writes the last keymap, after the recent files and the locale. */
    @Singleton
    @IntoSet(ShutdownStep.class)
    static final class Save implements ShutdownStep {

        private static final int ORDER = 340;

        private final KeymapFile file;

        @Inject
        Save(KeymapFile file) {
            this.file = file;
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public void run() {
            file.flush();
        }
    }
}
