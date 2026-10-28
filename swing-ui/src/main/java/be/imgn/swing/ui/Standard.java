package be.imgn.swing.ui;

import java.util.Arrays;
import java.util.Optional;

/** The standard commands, by id. */
enum Standard {
    EXIT("exit"),
    ABOUT("about"),
    PREFERENCES("preferences"),
    CUT("cut"),
    COPY("copy"),
    PASTE("paste"),
    SELECT_ALL("selectAll"),
    CLOSE_WINDOW("closeWindow"),
    OK("ok"),
    CANCEL("cancel");

    private final String id;

    Standard(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    boolean isSystemAction() {
        return this == EXIT || this == ABOUT || this == PREFERENCES;
    }

    static Optional<Standard> of(String id) {
        return Arrays.stream(values())
                .filter(standard -> standard.id.equals(id))
                .findFirst();
    }
}
