package dev.rgonz.cre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Runs the public Caddy configuration in front of a stub app and checks what reaches the app: no
 * request body over 64 KiB and no forwarded headers.
 */
class ProxyIT {
  private static final int LIMIT = 64 * 1024;
  private static final List<String> FORWARDED_HEADERS =
      List.of("Forwarded", "X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host");

  private static StubApp app;
  private static GenericContainer<?> caddy;
  private static URI draft;

  private final HttpClient client =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  @BeforeAll
  static void startProxy() throws IOException {
    app = new StubApp();
    Testcontainers.exposeHostPorts(app.port());

    caddy =
        new GenericContainer<>("caddy:2.11.4-alpine")
            .withCopyFileToContainer(
                MountableFile.forHostPath("deploy/Caddyfile"), "/etc/caddy/Caddyfile")
            .withEnv("CRE_UPSTREAM", "host.testcontainers.internal:" + app.port())
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort());
    caddy.start();

    draft =
        URI.create(
            "http://%s:%d/api/catalogs/c/draft"
                .formatted(caddy.getHost(), caddy.getMappedPort(8080)));
  }

  @AfterAll
  static void stopProxy() {
    caddy.stop();
    app.close();
  }

  @BeforeEach
  void forgetRequests() {
    app.reset();
  }

  @Test
  void refusesADeclaredLengthBodyOver64KiB() throws Exception {
    var status = put(BodyPublishers.ofByteArray(body(LIMIT + 1)));

    assertEquals(413, status);
    assertTrue(app.received().isEmpty(), "no complete body reached the app");
  }

  @Test
  void refusesAChunkedBodyOver64KiB() throws Exception {
    var bytes = body(LIMIT + 1);

    // A stream of unknown length is sent with chunked transfer encoding.
    var status = put(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)));

    assertEquals(413, status);
    assertTrue(app.received().isEmpty(), "no complete body reached the app");
  }

  @Test
  void passesABodyOfExactly64KiB() throws Exception {
    var status = put(BodyPublishers.ofByteArray(body(LIMIT)));

    assertEquals(200, status);
    assertEquals(LIMIT, app.received().getFirst().bodyLength());
  }

  @Test
  void dropsForwardedHeadersBeforeTheApp() throws Exception {
    var request =
        HttpRequest.newBuilder(draft)
            .header("Forwarded", "for=203.0.113.9;proto=https")
            .header("X-Forwarded-For", "203.0.113.9")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "example.com")
            .header("CF-Connecting-IP", "203.0.113.9")
            .PUT(BodyPublishers.ofString("{}"))
            .build();

    var status = client.send(request, BodyHandlers.discarding()).statusCode();

    assertEquals(200, status);
    var headers = app.received().getFirst().headers();
    for (var name : FORWARDED_HEADERS) {
      assertFalse(headers.containsKey(name), name + " reached the app");
    }
    assertFalse(headers.containsKey("CF-Connecting-IP"), "CF-Connecting-IP reached the app");
  }

  private int put(BodyPublisher body) throws Exception {
    var request =
        HttpRequest.newBuilder(draft).header("Content-Type", "application/json").PUT(body).build();

    return client.send(request, BodyHandlers.discarding()).statusCode();
  }

  private static byte[] body(int length) {
    var bytes = new byte[length];
    Arrays.fill(bytes, (byte) 'a');

    return bytes;
  }

  /** One request the stub read completely: its headers and body size. */
  record Received(Headers headers, int bodyLength) {}

  /**
   * Stands in for the app behind the proxy. It records only requests whose whole body arrived, so a
   * body cut off by the proxy never counts as received.
   */
  static final class StubApp implements AutoCloseable {
    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    StubApp() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::handle);
      server.start();
    }

    int port() {
      return server.getAddress().getPort();
    }

    List<Received> received() {
      return List.copyOf(received);
    }

    void reset() {
      received.clear();
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private void handle(HttpExchange exchange) {
      try (exchange) {
        var body = exchange.getRequestBody().readAllBytes();
        received.add(new Received(new Headers(exchange.getRequestHeaders()), body.length));

        exchange.sendResponseHeaders(200, -1);
      } catch (IOException ignored) {
        // The proxy cut the body off, so the request never arrived whole.
      }
    }
  }
}
