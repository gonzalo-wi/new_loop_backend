package com.loop.new_loop_api.integrations.aguas.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.loop.new_loop_api.integrations.aguas.dto.ReceiptImage;
import com.loop.new_loop_api.integrations.aguas.exception.AguasReceiptUploadException;
import com.loop.new_loop_api.settlements.domain.ReceiptImageFormat;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Real JDK HttpClient against a real local server: timeouts, redirects, retries and logs (spec 009 FR-16..18). */
class AguasReceiptClientTransportTest {

    private static final String MARKER = "IMAGE-BYTES-MARKER-9f3c1a";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);
    private static final Duration SHORT = Duration.ofMillis(400);

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger redirectTargetHits = new AtomicInteger();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private ListAppender<ILoggingEvent> logs;
    private Logger root;
    private Level previousLevel;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        previousLevel = root.getLevel();
        root.setLevel(Level.TRACE);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        root.detachAppender(logs);
        root.setLevel(previousLevel);
    }

    private AguasReceiptClient clientFor(String baseUrl) {
        return new AguasReceiptClient(AguasReceiptClientConfig.restClient(baseUrl, SHORT, SHORT));
    }

    private String start() {
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static ReceiptImage image() {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        byte[] tail = MARKER.getBytes(StandardCharsets.US_ASCII);
        byte[] all = new byte[head.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return new ReceiptImage(ReceiptImageFormat.JPEG, all);
    }

    private void respond(String path, int status) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    @Test
    void shouldDeliverTheRealRequestOnceOnSuccess() {
        respond(AguasReceiptClient.PATH, 204);

        clientFor(start()).upload(12L, DAY, image());

        assertThat(hits).hasValue(1);
        assertThat(receivedBody.get()).contains("comprobante-12-2026-11-21.jpg").contains(MARKER);
    }

    @Test
    void shouldFailWithinTheBoundWhenAguasStalls() {
        server.createContext(AguasReceiptClient.PATH, exchange -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        AguasReceiptClient client = clientFor(start());

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.upload(1L, DAY, image())).isInstanceOf(AguasReceiptUploadException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(Duration.ofMillis(2500));
        assertThat(hits).hasValue(1);
    }

    @Test
    void shouldFailWhenTheConnectionIsRefused() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        AguasReceiptClient client = clientFor("http://127.0.0.1:" + freePort);

        assertThatThrownBy(() -> client.upload(1L, DAY, image()))
                .isInstanceOf(AguasReceiptUploadException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldNotFollowARedirectAndShouldHitAguasOnce() {
        server.createContext(AguasReceiptClient.PATH, exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/elsewhere", exchange -> {
            redirectTargetHits.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        AguasReceiptClient client = clientFor(start());

        assertThatThrownBy(() -> client.upload(1L, DAY, image())).isInstanceOf(AguasReceiptUploadException.class);

        assertThat(hits).hasValue(1);
        assertThat(redirectTargetHits).hasValue(0);
    }

    @Test
    void shouldNotRetryAfterAServerError() {
        respond(AguasReceiptClient.PATH, 500);
        AguasReceiptClient client = clientFor(start());

        assertThatThrownBy(() -> client.upload(1L, DAY, image())).isInstanceOf(AguasReceiptUploadException.class);

        assertThat(hits).hasValue(1);
    }

    @Test
    void shouldNeverLogTheImageBytes() {
        respond(AguasReceiptClient.PATH, 204);
        AguasReceiptClient okClient = clientFor(start());
        okClient.upload(1L, DAY, image());
        server.removeContext(AguasReceiptClient.PATH);
        respond(AguasReceiptClient.PATH, 500);
        assertThatThrownBy(() -> okClient.upload(1L, DAY, image())).isInstanceOf(AguasReceiptUploadException.class);

        assertThat(receivedBody.get()).contains(MARKER);
        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(MARKER);
            assertThat(event.getMessage()).doesNotContain(MARKER);
            for (Object arg : event.getArgumentArray() == null ? new Object[0] : event.getArgumentArray()) {
                assertThat(String.valueOf(arg)).doesNotContain(MARKER);
            }
            for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain(MARKER);
            }
        }
    }
}
