package be.imgn.swing.desktop.internal;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlMappingException;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.UnknownKeys;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The real {@link InstanceLock}: an exclusive lock on {@code instance.lock}, and a loopback server whose port and token
 * are in {@code instance.toml}, readable by its owner only. A second instance sends its arguments as a TOML request,
 * and waits for {@code ok}.
 */
@Singleton
@ExposeAs(InstanceLock.class)
public final class SocketInstanceLock implements InstanceLock {

    /** The name of the lock file, in the state directory. */
    public static final String LOCK_FILE = "instance.lock";

    /** The name of the file that gives the port and the token, in the state directory. */
    public static final String INSTANCE_FILE = "instance.toml";

    private static final System.Logger LOGGER = System.getLogger(SocketInstanceLock.class.getName());
    private static final int TOKEN_BYTES = 32;
    private static final String OK = "ok";
    private static final Duration PATIENCE = Duration.ofSeconds(5);
    private static final Duration INTERVAL = Duration.ofMillis(100);

    private final Duration patience;
    private final Duration interval;

    @Inject
    SocketInstanceLock() {
        this(PATIENCE, INTERVAL);
    }

    /**
     * Creates a lock that retries a held lock that doesn't answer.
     *
     * @param patience how long to retry
     * @param interval how long to wait between the tries
     */
    public SocketInstanceLock(Duration patience, Duration interval) {
        this.patience = patience;
        this.interval = interval;
    }

    @Override
    public Optional<Closeable> acquire(Path directory, List<String> arguments, Consumer<List<String>> receiver)
            throws IOException {
        Files.createDirectories(directory);
        long deadline = System.nanoTime() + patience.toNanos();
        while (true) {
            Optional<Closeable> first = tryFirst(directory, receiver);
            if (first.isPresent()) {
                return first;
            }
            // A held lock whose file is missing, whose port refuses or which doesn't answer is that of an instance
            // starting or exiting: try the lock and the request again, until the patience runs out.
            if (forward(directory, arguments)) {
                return Optional.empty();
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException(
                        "Another instance holds the lock " + directory.resolve(LOCK_FILE) + " and doesn't answer");
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the instance that holds the lock", e);
            }
        }
    }

    private Optional<Closeable> tryFirst(Path directory, Consumer<List<String>> receiver) throws IOException {
        FileChannel channel =
                FileChannel.open(directory.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        @Nullable FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException heldInThisJvm) {
            lock = null;
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        byte[] random = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(random);
        String token = Base64.getEncoder().encodeToString(random);
        Path file = directory.resolve(INSTANCE_FILE);
        Toml.writeAtomically(Toml.toTable(new InstanceFile(server.getLocalPort(), token)), file);
        ownerOnly(file);
        Thread.ofVirtual().name("instance server").start(() -> serve(server, token, receiver));
        FileLock held = lock;
        return Optional.of(() -> {
            try {
                server.close();
            } finally {
                try {
                    held.release();
                } finally {
                    channel.close();
                }
            }
        });
    }

    private static void ownerOnly(Path file) throws IOException {
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static void serve(ServerSocket server, String token, Consumer<List<String>> receiver) {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                handle(socket, token).ifPresent(receiver);
            } catch (IOException e) {
                if (!server.isClosed()) {
                    LOGGER.log(Level.WARNING, "A request to the instance failed", e);
                }
            }
        }
    }

    // A request with the right token is answered ok; one with another token, or that doesn't parse, is closed without
    // an answer.
    private static Optional<List<String>> handle(Socket socket, String token) throws IOException {
        String text = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        InstanceRequest request;
        try {
            request = Toml.bind(Toml.parse(text).root(), InstanceRequest.class, UnknownKeys.IGNORE);
        } catch (TomlParseException | TomlMappingException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Ignored a request to the instance that doesn't parse: " + e.getMessage());
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(
                request.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            LOGGER.log(Level.WARNING, "Ignored a request to the instance with another token");
            return Optional.empty();
        }
        OutputStream out = socket.getOutputStream();
        out.write(OK.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return Optional.of(request.arguments());
    }

    // Sends the arguments to the instance holding the lock; false when it can't be reached or doesn't answer.
    private boolean forward(Path directory, List<String> arguments) {
        Path file = directory.resolve(INSTANCE_FILE);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        InstanceFile instance;
        try {
            instance = Toml.bind(Toml.parse(file).root(), InstanceFile.class, UnknownKeys.IGNORE);
        } catch (IOException | TomlParseException | TomlMappingException | IllegalArgumentException e) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), instance.port()), (int)
                    interval.toMillis());
            socket.setSoTimeout((int) patience.toMillis());
            OutputStream out = socket.getOutputStream();
            out.write(Toml.format(Toml.toTable(new InstanceRequest(instance.token(), arguments)))
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput();
            InputStream in = socket.getInputStream();
            return OK.equals(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }
}
