package dev.rgonz.cre;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Exercises the packaged application with a real database and Chromium browser. */
class PackagedApplicationIT {
  @Test
  void packagedJarServesRoutesAndKeepsGuestsAcrossRestarts() throws Exception {
    try (var database = new PostgreSQLContainer("postgres:18.6")) {
      database.start();

      int port;
      try (var socket = new ServerSocket(0)) {
        port = socket.getLocalPort();
      }

      var base = "http://127.0.0.1:" + port;
      var client = HttpClient.newHttpClient();

      var process = start(database, port, "first");
      try {
        waitUntilReady(client, base, process);

        assertEquals("2", query(database, "select max(version::int) from flyway_schema_history"));
        checkApiAndAssetErrors(client, base);

        var guest = new GuestClient(base);
        var workspaceId = guest.start().text("workspaceId");

        try (var playwright = Playwright.create();
            var browser = playwright.chromium().launch();
            var context = browser.newContext()) {
          var page = context.newPage();
          var liveMessage = page.getByText("Your private workspace is available until");

          page.navigate(base + "/");
          assertThat(page.locator("h1")).hasText("Configuration Rule Engine");

          page.navigate(base + "/showcase");
          assertThat(page.getByText("These examples are read-only.")).isVisible();

          page.navigate(base + "/workspace");
          var startButton =
              page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start guest workspace"));
          startButton.click();
          assertThat(liveMessage).isVisible();

          var browserWorkspace = browserWorkspace(page);

          page.reload();
          assertThat(liveMessage).isVisible();
          assertEquals(browserWorkspace, browserWorkspace(page));

          stop(process);
          process = start(database, port, "restarted");
          waitUntilReady(client, base, process);

          assertEquals(workspaceId, guest.get("/api/session").text("workspaceId"));

          page.reload();
          assertThat(liveMessage).isVisible();
          assertEquals(browserWorkspace, browserWorkspace(page));

          update(
              database,
              "update workspace set expires_at = now() - interval '1 second' where kind = 'GUEST'");

          page.reload();
          assertThat(page.getByText("Your guest workspace expired")).isVisible();
          assertEquals("EXPIRED", guest.get("/api/session").text("status"));
        }
      } finally {
        stop(process);
      }
    }
  }

  /** Reads the workspace ID with Chromium's own cookie store, which holds the Secure cookie. */
  private static String browserWorkspace(Page page) {
    var workspaceId =
        (String)
            page.evaluate(
                "fetch('/api/session').then(r => r.json()).then(body => body.workspaceId ?? '')");
    assertFalse(workspaceId.isEmpty());

    return workspaceId;
  }

  private static void checkApiAndAssetErrors(HttpClient client, String base) throws Exception {
    var api =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/unknown")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(404, api.statusCode());
    assertTrue(api.headers().firstValue("content-type").orElse("").contains("application/json"));
    assertTrue(api.body().contains("\"code\":\"NOT_FOUND\""));

    var browserStyleApi =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/unknown"))
                .header("Accept", "text/html")
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(404, browserStyleApi.statusCode());
    assertTrue(
        browserStyleApi
            .headers()
            .firstValue("content-type")
            .orElse("")
            .contains("application/json"));

    var asset =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/missing.js")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(404, asset.statusCode());
    assertFalse(asset.body().contains("Create, check, and apply feature relationships."));
  }

  private static Process start(PostgreSQLContainer database, int port, String logName)
      throws Exception {
    var jar = Path.of("target/configuration-rule-engine-0.1.0.jar").toAbsolutePath();
    var log = Path.of("target/packaged-application-" + logName + ".log").toFile();
    var command =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar",
            jar.toString(),
            "--server.port=" + port);

    command.environment().put("DATABASE_URL", database.getJdbcUrl());
    command.environment().put("DATABASE_USER", database.getUsername());
    command.environment().put("DATABASE_PASSWORD", database.getPassword());

    return command.redirectErrorStream(true).redirectOutput(log).start();
  }

  private static void stop(Process process) throws InterruptedException {
    process.destroy();

    if (!process.waitFor(5, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      process.waitFor();
    }
  }

  private static String query(PostgreSQLContainer database, String sql) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
        var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      assertTrue(result.next());

      return result.getString(1);
    }
  }

  private static void update(PostgreSQLContainer database, String sql) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  /** Bounds each health request so a stalled child process cannot hang verification. */
  private static void waitUntilReady(HttpClient client, String base, Process process)
      throws Exception {
    var deadline = java.time.Instant.now().plus(Duration.ofSeconds(45));

    while (java.time.Instant.now().isBefore(deadline) && process.isAlive()) {
      try {
        var response =
            client.send(
                HttpRequest.newBuilder(URI.create(base + "/actuator/health/readiness"))
                    .timeout(Duration.ofSeconds(2))
                    .build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (java.io.IOException ignored) {
        // The server socket is unavailable while the application starts.
      }
      Thread.sleep(250);
    }

    throw new AssertionError(
        "Packaged application did not become ready; see target/packaged-application-*.log");
  }
}
