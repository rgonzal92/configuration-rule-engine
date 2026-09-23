package dev.rgonz.cre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Playwright;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Exercises the packaged application with a real database and Chromium browser. */
class PackagedApplicationIT {
  @Test
  void packagedJarMigratesDatabaseAndServesOnlyDeclaredBrowserRoutes() throws Exception {
    try (var database = new PostgreSQLContainer("postgres:18.6")) {
      database.start();

      int port;
      try (var socket = new ServerSocket(0)) {
        port = socket.getLocalPort();
      }

      var jar = Path.of("target/configuration-rule-engine-0.1.0.jar").toAbsolutePath();
      var log = Path.of("target/packaged-application.log").toFile();
      var command =
          new ProcessBuilder(
              Path.of(System.getProperty("java.home"), "bin", "java").toString(),
              "-jar",
              jar.toString(),
              "--server.port=" + port);
      command.environment().put("DATABASE_URL", database.getJdbcUrl());
      command.environment().put("DATABASE_USER", database.getUsername());
      command.environment().put("DATABASE_PASSWORD", database.getPassword());

      var process = command.redirectErrorStream(true).redirectOutput(log).start();
      try {
        var base = "http://127.0.0.1:" + port;
        var client = HttpClient.newHttpClient();
        waitUntilReady(client, base, process);

        try (var connection =
                DriverManager.getConnection(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var statement = connection.createStatement();
            var result =
                statement.executeQuery(
                    "select version from flyway_schema_history where success = true")) {
          assertTrue(result.next());
          assertEquals("1", result.getString(1));
        }

        try (var playwright = Playwright.create();
            var browser = playwright.chromium().launch()) {
          var page = browser.newPage();
          page.navigate(base + "/");
          assertEquals("Configuration Rule Engine", page.locator("h1").textContent());

          page.navigate(base + "/workspace");
          assertTrue(page.locator("body").textContent().contains("Catalog editing"));

          page.navigate(base + "/showcase");
          assertTrue(page.locator("body").textContent().contains("Public catalog examples"));
        }

        var api =
            client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/unknown")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, api.statusCode());
        assertTrue(
            api.headers().firstValue("content-type").orElse("").contains("application/json"));
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
      } finally {
        process.destroy();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor();
        }
      }
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
        "Packaged application did not become ready; see target/packaged-application.log");
  }
}
