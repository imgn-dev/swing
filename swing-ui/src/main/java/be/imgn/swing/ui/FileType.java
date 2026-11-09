package be.imgn.swing.ui;

import static java.util.Objects.requireNonNull;

import java.util.List;

/**
 * A type of file a chooser offers as a filter, such as text files.
 *
 * @param description the description the chooser shows, as it is: it comes from typed messages, so it is translated
 * @param extensions the extensions, without the dot, matched without regard to case
 */
public record FileType(String description, List<String> extensions) {

    /**
     * Creates a type of file.
     *
     * @param description the description
     * @param extensions the extensions, without the dot, copied
     * @throws NullPointerException if {@code description}, {@code extensions} or an extension is {@code null}
     * @throws IllegalArgumentException if there is no extension, or one is blank or holds a {@code .}
     */
    public FileType {
        requireNonNull(description, "description");
        extensions = List.copyOf(requireNonNull(extensions, "extensions"));
        if (extensions.isEmpty()) {
            throw new IllegalArgumentException("A file type has at least one extension: " + description);
        }
        for (String extension : extensions) {
            if (extension.isBlank() || extension.contains(".")) {
                throw new IllegalArgumentException(
                        "An extension is given without the dot, and is not blank: \"" + extension + "\"");
            }
        }
    }
}
