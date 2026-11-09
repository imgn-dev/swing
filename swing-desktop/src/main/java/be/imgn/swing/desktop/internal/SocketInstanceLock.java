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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
    private static final int TRY_TIMEOUT_MILLIS = 1000;
    private static final int REQUEST_TIMEOUT_MILLIS = 5000;
    private static final int REQUEST_LIMIT = 64 * 1024;

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
            if (forward(directory, arguments, deadline)) {
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

    // Takes the lock, then starts the server and writes its file; a failure after the lock is taken releases all.
    private Optional<Closeable> tryFirst(Path directory, Consumer<List<String>> receiver) throws IOException {
        FileChannel channel =
                FileChannel.open(directory.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        @Nullable FileLock lock = null;
        @Nullable ServerSocket server = null;
        boolean started = false;
        try {
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException heldInThisJvm) {
                lock = null;
            }
            if (lock == null) {
                return Optional.empty();
            }
            server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            byte[] random = new byte[TOKEN_BYTES];
            new SecureRandom().nextBytes(random);
            String token = Base64.getEncoder().encodeToString(random);
            writeOwnerOnly(
                    directory.resolve(INSTANCE_FILE),
                    Toml.format(Toml.toTable(new InstanceFile(server.getLocalPort(), token))));
            ServerSocket listening = server;
            Thread.ofVirtual().name("instance server").start(() -> serve(listening, token, receiver));
            started = true;
            return Optional.of(closer(server, lock, channel));
        } finally {
            if (!started) {
                closer(server, lock, channel).close();
            }
        }
    }

    private static Closeable closer(@Nullable ServerSocket server, @Nullable FileLock lock, FileChannel channel) {
        return () -> {
            try {
                if (server != null) {
                    server.close();
                }
            } finally {
                try {
                    if (lock != null && lock.isValid()) {
                        lock.release();
                    }
                } finally {
                    channel.close();
                }
            }
        };
    }

    // Writes the file atomically, readable by its owner only from its creation on POSIX file systems: the token is
    // never readable by others, even for a moment.
    private static void writeOwnerOnly(Path file, String content) throws IOException {
        Path directory = Objects.requireNonNull(file.getParent(), "The instance file has no directory");
        boolean posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null;
        Path temporary = posix
                ? Files.createTempFile(
                        directory,
                        "instance",
                        ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(directory, "instance", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    // Each connection is read on a thread of its own, within a time and a size limit, so that a connection that never
    // ends its request blocks no other.
    private static void serve(ServerSocket server, String token, Consumer<List<String>> receiver) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread.ofVirtual().name("instance request").start(() -> handle(socket, token, receiver));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    LOGGER.log(Level.WARNING, "The instance could not accept a request", e);
                }
            }
        }
    }

    private static void handle(Socket connection, String token, Consumer<List<String>> receiver) {
        try (Socket socket = connection) {
            socket.setSoTimeout(REQUEST_TIMEOUT_MILLIS);
            read(socket, token).ifPresent(receiver);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "A request to the instance failed: " + e.getMessage());
        }
    }

    // A request with the right token is answered ok; one with another token, too large, or that doesn't parse, is
    // closed without an answer.
    private static Optional<List<String>> read(Socket socket, String token) throws IOException {
        byte[] bytes = socket.getInputStream().readNBytes(REQUEST_LIMIT + 1);
        if (bytes.length > REQUEST_LIMIT) {
            LOGGER.log(Level.WARNING, "Ignored a request to the instance larger than " + REQUEST_LIMIT + " bytes");
            return Optional.empty();
        }
        InstanceRequest request;
        try {
            request = Toml.bind(
                    Toml.parse(new String(bytes, StandardCharsets.UTF_8)).root(),
                    InstanceRequest.class,
                    UnknownKeys.IGNORE);
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

    // Sends the arguments to the instance holding the lock; false when it can't be reached or doesn't answer within
    // a second, or the time left.
    private boolean forward(Path directory, List<String> arguments, long deadline) {
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
        long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        int wait = (int) Math.max(1, Math.min(TRY_TIMEOUT_MILLIS, left));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), instance.port()), wait);
            socket.setSoTimeout(wait);
            OutputStream out = socket.getOutputStream();
            out.write(Toml.format(Toml.toTable(new InstanceRequest(instance.token(), arguments)))
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput();
            InputStream in = socket.getInputStream();
            return OK.equals(new String(in.readNBytes(OK.length()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }
}
