package be.imgn.swing.testing;

import be.imgn.swing.ui.FileType;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A file chooser the application showed, as {@link FakeDialogs#fileChoices()} records it.
 *
 * @param kind whether the chooser opens or saves a file
 * @param types the types of files it offered
 * @param suggestedName the name a save chooser started with; {@code null} for an open chooser
 */
public record FileChoice(
        Kind kind, List<FileType> types, @Nullable String suggestedName) {

    /**
     * Records a chooser.
     *
     * @param kind whether the chooser opens or saves a file
     * @param types the types of files, copied
     * @param suggestedName the name a save chooser started with; {@code null} for an open chooser
     * @throws NullPointerException if {@code kind}, {@code types} or a type is {@code null}
     */
    public FileChoice {
        Objects.requireNonNull(kind, "kind");
        types = List.copyOf(types);
    }

    /** Whether a chooser opens or saves a file. */
    public enum Kind {
        /** A chooser of a file to open. */
        OPEN,
        /** A chooser of a file to save to. */
        SAVE
    }
}
