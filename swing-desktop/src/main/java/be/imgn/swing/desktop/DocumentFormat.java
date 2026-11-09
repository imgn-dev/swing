package be.imgn.swing.desktop;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.ui.FileType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * How a document's contents are read from and written to a file. {@link #read} and {@link #write} run on a task's
 * thread, and touch no component.
 *
 * @param <T> the contents
 */
public interface DocumentFormat<T> {

    /**
     * Returns the type of the document's files, which the file choosers offer.
     *
     * @return the type
     */
    FileType fileType();

    /**
     * Returns the contents of a new document.
     *
     * @return the contents
     */
    T empty();

    /**
     * Reads the contents of a file, on a task's thread.
     *
     * @param in the file's stream, which the caller closes
     * @return the contents
     * @throws IOException if the file can't be read or doesn't hold valid contents
     */
    T read(InputStream in) throws IOException;

    /**
     * Writes contents to a file, on a task's thread.
     *
     * @param contents the contents
     * @param out the file's stream, which the caller closes
     * @throws IOException if the contents can't be written
     */
    void write(T contents, OutputStream out) throws IOException;

    /**
     * Returns the format of UTF-8 text, whose empty contents are {@code ""}. A read that doesn't decode fails with
     * {@code MalformedInputException}, an {@link IOException}.
     *
     * @param fileType the type of the files
     * @return the format
     * @throws NullPointerException if {@code fileType} is {@code null}
     */
    static DocumentFormat<String> text(FileType fileType) {
        requireNonNull(fileType, "fileType");
        return new DocumentFormat<>() {
            @Override
            public FileType fileType() {
                return fileType;
            }

            @Override
            public String empty() {
                return "";
            }

            @Override
            public String read(InputStream in) throws IOException {
                return StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(in.readAllBytes()))
                        .toString();
            }

            @Override
            public void write(String contents, OutputStream out) throws IOException {
                out.write(contents.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
