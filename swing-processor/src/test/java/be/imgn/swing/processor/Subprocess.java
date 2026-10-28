package be.imgn.swing.processor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a JDK tool in another process, so that it sees only what it is given, not the tests' own module. */
final class Subprocess {

    private Subprocess() {}

    /**
     * Runs a command and waits up to a minute for it to finish.
     *
     * @param command the command and its arguments
     * @return its exit code, its standard output and its standard error
     * @throws IOException if the command can't be started
     * @throws InterruptedException if the wait is interrupted
     */
    static Result run(List<String> command) throws IOException, InterruptedException {
        var error = Files.createTempFile("subprocess", ".err");
        try {
            var process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.to(error.toFile()))
                    .start();
            // JDK tools write in the platform's native encoding, which may differ from the JVM's default charset.
            var charset = Charset.forName(System.getProperty("native.encoding"));
            var output = new String(process.getInputStream().readAllBytes(), charset);
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException(command.getFirst() + " did not finish within a minute");
            }
            return new Result(process.exitValue(), output, Files.readString(error, charset));
        } finally {
            Files.delete(error);
        }
    }

    /**
     * The outcome of a command.
     *
     * @param exitCode its exit code
     * @param output its standard output
     * @param error its standard error
     */
    record Result(int exitCode, String output, String error) {

        /**
         * Returns both outputs, to describe a failure.
         *
         * @return the standard output, then the standard error
         */
        String description() {
            return output + error;
        }
    }
}
