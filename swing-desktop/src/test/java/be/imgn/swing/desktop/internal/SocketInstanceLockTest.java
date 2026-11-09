package be.imgn.swing.desktop.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.toml.Toml;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The single instance's transport, with two lock holders in one JVM, where the second sees the lock held. */
class SocketInstanceLockTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final int TOKEN_BYTES = 32;

    @TempDir
    Path directory;

    private final SocketInstanceLock lock = new SocketInstanceLock(Duration.ofMillis(300), Duration.ofMillis(20));
    private final BlockingQueue<List<String>> received = new LinkedBlockingQueue<>();
    private @Nullable Closeable first;

    @AfterEach
    void releaseTheFirstInstance() throws IOException {
        if (first != null) {
            first.close();
        }
    }

    private void startFirst() throws IOException {
        first = lock.acquire(directory, List.of(), received::add).orElseThrow();
    }

    @Test
    void forwardsTheArgumentsOfASecondLaunchToTheFirstInstance() throws IOException, InterruptedException {
        startFirst();

        Optional<Closeable> second = lock.acquire(directory, List.of("notes.txt", "todo.txt"), launch -> {});

        assertThat(second).isEmpty();
        assertThat(received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).containsExactly("notes.txt", "todo.txt");
    }

    @Test
    void writesThePortAndTokenReadableByTheOwnerOnly() throws IOException {
        startFirst();
        Path file = directory.resolve(SocketInstanceLock.INSTANCE_FILE);

        InstanceFile instance = Toml.parse(file, InstanceFile.class);

        assertThat(instance.port()).isPositive();
        assertThat(Base64.getDecoder().decode(instance.token())).hasSize(TOKEN_BYTES);
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(file))
                    .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    void ignoresARequestWithAnotherTokenOrThatDoesntParse() throws IOException, InterruptedException {
        startFirst();
        InstanceFile instance = Toml.parse(directory.resolve(SocketInstanceLock.INSTANCE_FILE), InstanceFile.class);

        assertThat(send(instance.port(), "token = \"wrong\"\narguments = [\"x\"]\n"))
                .isEmpty();
        assertThat(send(instance.port(), "not toml")).isEmpty();
        assertThat(received.poll(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void failsWhenTheInstanceHoldingTheLockDoesntAnswer() throws IOException {
        startFirst();
        Files.delete(directory.resolve(SocketInstanceLock.INSTANCE_FILE));

        assertThatThrownBy(() -> lock.acquire(directory, List.of("x"), launch -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holds the lock")
                .hasMessageContaining("doesn't answer");
    }

    @Test
    void becomesTheFirstInstanceOnceTheOtherOneReleasesTheLock() throws IOException {
        lock.acquire(directory, List.of(), received::add).orElseThrow().close();

        startFirst();

        assertThat(first).isNotNull();
    }

    @Test
    void servesASecondLaunchWhileAnotherConnectionNeverEndsItsRequest() throws IOException, InterruptedException {
        startFirst();
        InstanceFile instance = Toml.parse(directory.resolve(SocketInstanceLock.INSTANCE_FILE), InstanceFile.class);
        try (Socket stuck = new Socket(InetAddress.getLoopbackAddress(), instance.port())) {
            stuck.getOutputStream().write('t');

            Optional<Closeable> second = lock.acquire(directory, List.of("todo.txt"), launch -> {});

            assertThat(second).isEmpty();
            assertThat(received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).containsExactly("todo.txt");
        }
    }

    @Test
    void becomesTheFirstInstanceWhenTheLockIsReleasedWhileItRetries() throws Exception {
        SocketInstanceLock patient = new SocketInstanceLock(Duration.ofSeconds(TIMEOUT_SECONDS), Duration.ofMillis(20));
        startFirst();
        Files.delete(directory.resolve(SocketInstanceLock.INSTANCE_FILE));
        Thread release = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(200);
                Objects.requireNonNull(first).close();
                first = null;
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });

        Optional<Closeable> second = patient.acquire(directory, List.of(), launch -> {});
        release.join();

        assertThat(second).isPresent();
        second.orElseThrow().close();
    }

    // A stale instance.toml names a port that accepts but never answers: each try gives up after a second, so a later
    // try reaches the instance once its file is back.
    @Test
    void retriesWithinItsPatienceWhenAPortNeverAnswers() throws Exception {
        SocketInstanceLock patient = new SocketInstanceLock(Duration.ofSeconds(3), Duration.ofMillis(20));
        startFirst();
        Path file = directory.resolve(SocketInstanceLock.INSTANCE_FILE);
        String real = Files.readString(file);
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Files.writeString(file, "port = " + silent.getLocalPort() + "\ntoken = \"stale\"\n");
            Thread restore = Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(300);
                    Files.writeString(file, real);
                } catch (IOException | InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            });

            Optional<Closeable> second = patient.acquire(directory, List.of("x"), launch -> {});
            restore.join();

            assertThat(second).isEmpty();
            assertThat(received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).containsExactly("x");
        }
    }

    private static String send(int port, String request) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
