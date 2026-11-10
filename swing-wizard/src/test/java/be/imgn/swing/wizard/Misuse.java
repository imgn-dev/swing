package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;

/** How the next {@link MisusedDialog} gives its steps. */
@Singleton
final class Misuse {

    private Kind kind = Kind.NONE;

    @Inject
    Misuse() {}

    Kind kind() {
        return kind;
    }

    void kind(Kind value) {
        this.kind = value;
    }

    /** A way to give a wizard's steps wrongly. */
    enum Kind {
        NONE,
        TWICE,
        DUPLICATE,
        UNEXPOSED
    }
}
