package dev.rgonz.cre;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
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
  private static final String GPU_RULE =
      "Choosing Dedicated GPU also requires High-wattage charger.";
  private static final String TOUCHSCREEN_RULE =
      "Choosing Touchscreen also requires Stylus support.";

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

        assertEquals("4", query(database, "select max(version::int) from flyway_schema_history"));
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

          walkThroughTheCatalogWorkflow(page);

          try (var otherGuest = browser.newContext()) {
            var otherPage = otherGuest.newPage();
            otherPage.navigate(base + "/workspace");
            otherPage
                .getByRole(
                    AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start guest workspace"))
                .click();

            var otherRules = otherPage.locator("app-relationship-list");
            assertThat(otherRules).containsText(GPU_RULE);
            assertThat(otherRules).not().containsText(TOUCHSCREEN_RULE);
          }

          try (var newVisitor = browser.newContext()) {
            var visitorPage = newVisitor.newPage();
            visitorPage.navigate(base + "/showcase");

            var laptop =
                visitorPage.getByRole(
                    AriaRole.ARTICLE, new Page.GetByRoleOptions().setName("Laptop catalog"));
            laptop.getByLabel("Dedicated GPU").check();
            laptop
                .getByRole(
                    AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Test configuration"))
                .click();

            assertThat(laptop).containsText("Dedicated GPU requires High-wattage charger");
          }

          page.navigate(base + "/showcase");
          var laptopShowcase =
              page.getByRole(
                  AriaRole.ARTICLE, new Page.GetByRoleOptions().setName("Laptop catalog"));
          assertThat(laptopShowcase).containsText(GPU_RULE);
          assertThat(laptopShowcase).not().containsText(TOUCHSCREEN_RULE);

          update(
              database,
              "update workspace set expires_at = now() - interval '1 second' where kind = 'GUEST'");

          page.navigate(base + "/workspace");
          assertThat(page.getByText("Your guest workspace expired")).isVisible();
          assertEquals("EXPIRED", guest.get("/api/session").text("status"));
        }
      } finally {
        stop(process);
      }
    }
  }

  /**
   * Stages a conflicting rule and sees it blocked, stages a two-target rule, removes one target,
   * applies the result, tests a configuration, and confirms the rule survives a refresh.
   */
  private static void walkThroughTheCatalogWorkflow(Page page) {
    var status = page.locator("p.status");
    var activeRules = page.locator("app-relationship-list");
    var checkResult =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Check result"));

    stage(page, "Dedicated GPU", "can't be chosen with", "High-wattage charger");
    clickButton(page, "Check pending changes");
    assertThat(checkResult).containsText("Dedicated GPU could never be chosen");
    assertThat(button(page, "Apply changes")).isDisabled();

    clickButton(page, "Undo: Add: Dedicated GPU");
    assertThat(page.getByText("No pending changes.")).isVisible();

    stage(page, "Touchscreen", "requires", "Stylus support", "High-resolution display");
    clickButton(page, "Check pending changes");
    assertThat(checkResult).containsText("Valid configurations: 1,536 → 960");

    clickButton(page, "Edit pending: Add: Choosing Touchscreen");
    assertThat(page.locator("form h4")).hasText("Edit relationship");

    var highResolution = targets(page).getByLabel("High-resolution display");
    assertThat(highResolution).isChecked();
    highResolution.uncheck();
    clickButton(page, "Save change");
    assertThat(status).containsText("Pending changes saved.");

    var pending =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending changes"));
    assertThat(pending).containsText("Add: Choosing Touchscreen also requires Stylus support.");

    clickButton(page, "Check pending changes");
    assertThat(checkResult).containsText("Valid configurations: 1,536 → 1,152");

    clickButton(page, "Apply changes");
    assertThat(status).containsText("The catalog is now at revision 2.");
    assertThat(activeRules).containsText(TOUCHSCREEN_RULE);

    var tester =
        page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Features to choose"));
    tester.getByLabel("Touchscreen").check();
    clickButton(page, "Test configuration");
    assertThat(page.getByText("Touchscreen requires Stylus support")).isVisible();

    page.reload();
    assertThat(activeRules).containsText(TOUCHSCREEN_RULE);
    assertThat(activeRules).containsText(GPU_RULE);
  }

  private static void stage(Page page, String source, String kind, String... targetNames) {
    page.getByLabel("Source feature").selectOption(new SelectOption().setLabel(source));
    page.getByLabel("Relationship type").selectOption(new SelectOption().setLabel(kind));

    for (var target : targetNames) {
      targets(page).getByLabel(target).check();
    }

    clickButton(page, "Stage change");
    assertThat(page.locator("p.status")).containsText("Pending changes saved.");
  }

  private static Locator targets(Page page) {
    return page.getByRole(
        AriaRole.GROUP, new Page.GetByRoleOptions().setName("Target features (up to 10)"));
  }

  private static Locator button(Page page, String name) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
  }

  private static void clickButton(Page page, String name) {
    button(page, name).click();
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
