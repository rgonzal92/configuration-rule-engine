package dev.rgonz.cre;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ReducedMotion;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Exercises the packaged application with a real database and Chromium browser. */
class PackagedApplicationIT {
  private static final String GPU_RULE =
      "Choosing Dedicated GPU also requires High-wattage charger.";
  private static final String TOUCHSCREEN_RULE =
      "Choosing Touchscreen also requires Stylus support.";

  /** Lists the innermost elements that reach past the right edge of the viewport. */
  private static final String OVERFLOW =
      """
      [...document.querySelectorAll('body *')]
        .filter((e) => e.getBoundingClientRect().right > document.documentElement.clientWidth)
        .filter((e) => ![...e.children].some((c) =>
          c.getBoundingClientRect().right > document.documentElement.clientWidth))
        .map((e) => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + ' "'
          + (e.textContent || '').trim().slice(0, 30) + '" right='
          + Math.round(e.getBoundingClientRect().right))
        .join('; ')
        + ' | scrollWidth=' + document.documentElement.scrollWidth
        + ' clientWidth=' + document.documentElement.clientWidth
        + ' widest=' + [...document.querySelectorAll('body, body *')]
          .map((e) => [e, Math.max(e.getBoundingClientRect().right, e.scrollWidth)])
          .sort((a, b) => b[1] - a[1]).slice(0, 4)
          .map(([e, r]) => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + '.'
            + String(e.className).slice(0, 40) + '=' + Math.round(r)).join(', ')
      """;

  private static final List<String> WCAG_TAGS =
      List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
  private static final String GPU = "00000000-0000-0000-0000-00000000f001";
  private static final String FANLESS = "00000000-0000-0000-0000-00000000f003";

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
            var context = newContext(browser)) {
          var page = context.newPage();
          var liveMessage = page.getByText("Your private workspace is available until");

          page.navigate(base + "/");
          assertThat(page.locator("h1")).hasText("Configuration Rule Engine");
          checkTypography(page);
          checkKeyboardBasics(page);
          assertAccessible(page, "home");

          page.navigate(base + "/showcase");
          assertThat(page.getByText("These examples are read-only.")).isVisible();
          assertThat(page.getByRole(AriaRole.ARTICLE)).hasCount(2);
          assertAccessible(page, "showcase");

          page.navigate(base + "/workspace");
          var startButton =
              page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start guest workspace"));
          assertThat(startButton).isVisible();
          assertAccessible(page, "workspace before starting");
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

          try (var otherGuest = newContext(browser)) {
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

          try (var newVisitor = newContext(browser)) {
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

          try (var openAi = new StubOpenAi()) {
            stop(process);
            process =
                start(
                    database,
                    port,
                    "assistant",
                    "--spring.ai.openai.api-key=sk-test",
                    "--spring.ai.openai.base-url=" + openAi.baseUrl());
            waitUntilReady(client, base, process);

            page.navigate(base + "/workspace");
            useTheLiveAssistant(page, openAi);
          }

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
    assertAccessible(page, "workspace with a blocked check result");

    clickButton(page, "Undo: Add: Dedicated GPU");
    assertThat(page.getByText("No pending changes.")).isVisible();

    stage(page, "Touchscreen", "requires", "Stylus support", "High-resolution display");
    clickButton(page, "Check pending changes");
    assertThat(checkResult).containsText("Valid configurations: 1,536 → 960");

    clickButton(page, "Edit pending: Add: Choosing Touchscreen");
    assertThat(page.locator("form h3")).hasText("Edit relationship");

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

    var assistant = assistant(page);
    assertThat(assistant).containsText("This example parser does not use AI.");
    describe(page, "Dedicated GPU can't be chosen with Fanless chassis");
    assertThat(assistant).containsText("Review it, then stage it.");
    assertAccessible(page, "workspace with an assistant reply");
    stageSuggestion(page, "Add: Dedicated GPU and Fanless chassis can't be chosen together.");

    page.reload();
    assertThat(activeRules).containsText(TOUCHSCREEN_RULE);
    assertThat(activeRules).containsText(GPU_RULE);
  }

  /**
   * With a key, a stubbed model answer reaches the form for review; after a failed request the
   * assistant says it is unavailable and manual staging still works.
   */
  private static void useTheLiveAssistant(Page page, StubOpenAi openAi) {
    var assistant = assistant(page);
    assertThat(assistant).containsText("The AI assistant turns your description");

    openAi.answer(
        Map.of(
            "status",
            "SUGGESTION",
            "sourceFeatureId",
            FANLESS,
            "kind",
            "NOT_ALLOWED_WITH",
            "targetFeatureIds",
            List.of(GPU)));
    describe(page, "fanless laptops shouldn't have a big GPU");
    assertThat(assistant).containsText("Review it, then stage it.");
    stageSuggestion(page, "Add: Fanless chassis and Dedicated GPU can't be chosen together.");

    openAi.fail(500);
    describe(page, "something vague");
    assertThat(assistant).containsText("The assistant is unavailable right now.");

    stage(page, "Backlit keyboard", "requires", "Extra battery");
    clickButton(page, "Undo: Add: Choosing Backlit keyboard");
    assertThat(page.getByText("No pending changes.")).isVisible();

    assertEquals(2, openAi.requests().size());
  }

  private static Locator assistant(Page page) {
    return page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Describe a rule"));
  }

  private static void describe(Page page, String text) {
    var assistant = assistant(page);
    assistant.getByLabel("Rule in plain English").fill(text);
    assistant.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Suggest")).click();
  }

  /** Stages the suggestion now in the form, checks it is pending, then undoes it. */
  private static void stageSuggestion(Page page, String pendingText) {
    assertThat(page.locator("form h3")).hasText("Stage a relationship");
    clickButton(page, "Stage change");
    assertThat(page.locator("p.status")).containsText("Pending changes saved.");

    var pending =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Pending changes"));
    assertThat(pending).containsText(pendingText);

    clickButton(page, "Undo: " + pendingText);
    assertThat(page.getByText("No pending changes.")).isVisible();
  }

  private static void stage(Page page, String source, String kind, String... targetNames) {
    choose(page, "Source feature", source);
    choose(page, "Relationship type", kind);

    for (var target : targetNames) {
      targets(page).getByLabel(target).check();
    }

    clickButton(page, "Stage change");
    assertThat(page.locator("p.status")).containsText("Pending changes saved.");
  }

  /** Browses with reduced motion, so colors settle before the accessibility scans measure them. */
  private static BrowserContext newContext(Browser browser) {
    return browser.newContext(
        new Browser.NewContextOptions().setReducedMotion(ReducedMotion.REDUCE));
  }

  /** Picks an option from a PrimeNG select the way a visitor does: open it, then click. */
  private static void choose(Page page, String select, String option) {
    page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName(select)).click();
    page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(option).setExact(true))
        .click();
  }

  /**
   * Requires zero WCAG 2.2 A and AA violations in the light and the dark theme, and no sideways
   * scrolling at phone and desktop widths. The theme ends where it started.
   */
  private static void assertAccessible(Page page, String where) {
    for (int round = 0; round < 2; round++) {
      var theme = "true".equals(themeToggle(page).getAttribute("aria-pressed")) ? "dark" : "light";
      var results = new AxeBuilder(page).withTags(WCAG_TAGS).analyze();

      assertTrue(
          results.violationFree(), where + ", " + theme + ": " + describe(results.getViolations()));
      toggleTheme(page);
    }

    var original = page.viewportSize();
    for (var width : List.of(320, 1280)) {
      page.setViewportSize(width, 800);
      var scrollsSideways =
          (Boolean)
              page.evaluate(
                  "document.documentElement.scrollWidth > document.documentElement.clientWidth");

      assertFalse(
          scrollsSideways,
          where
              + " scrolls sideways at "
              + width
              + "px, past the edge: "
              + page.evaluate(OVERFLOW));
    }
    page.setViewportSize(original.width, original.height);
  }

  private static String describe(List<Rule> violations) {
    return violations.stream()
        .map(
            rule ->
                rule.getId()
                    + " ("
                    + rule.getImpact()
                    + ") at "
                    + rule.getNodes().stream()
                        .map(node -> node.getTarget() + " " + node.getFailureSummary())
                        .collect(Collectors.joining(", ")))
        .collect(Collectors.joining("; "));
  }

  /** Text is set in IBM Plex Sans, served with the app. */
  private static void checkTypography(Page page) {
    assertTrue(
        ((String) page.evaluate("getComputedStyle(document.body).fontFamily"))
            .startsWith("\"IBM Plex Sans Variable\""));
    assertTrue(
        (Boolean)
            page.evaluate(
                "document.fonts.ready.then(() => document.fonts.check('14px \"IBM Plex Sans Variable\"'))"),
        "the IBM Plex Sans font file loads");
  }

  /**
   * The skip link is the first stop and moves focus to the main content; the theme toggle works
   * from the keyboard and reports its state.
   */
  private static void checkKeyboardBasics(Page page) {
    page.keyboard().press("Tab");
    assertEquals(
        "Skip to main content", page.evaluate("document.activeElement.textContent.trim()"));
    page.keyboard().press("Enter");
    assertEquals("main", page.evaluate("document.activeElement.id"));
    assertTrue(page.url().endsWith("/"), "the skip link stays on the page");

    var toggle = themeToggle(page);
    var before = toggle.getAttribute("aria-pressed");
    toggle.focus();
    page.keyboard().press("Space");
    assertThat(toggle).not().hasAttribute("aria-pressed", before);
    page.keyboard().press("Enter");
    assertThat(toggle).hasAttribute("aria-pressed", before);
  }

  private static Locator themeToggle(Page page) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Dark theme"));
  }

  private static void toggleTheme(Page page) {
    var toggle = themeToggle(page);
    var before = toggle.getAttribute("aria-pressed");
    toggle.click();
    assertThat(toggle).not().hasAttribute("aria-pressed", before);
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

  private static Process start(
      PostgreSQLContainer database, int port, String logName, String... settings) throws Exception {
    var jar = Path.of("target/configuration-rule-engine-0.1.0.jar").toAbsolutePath();
    var log = Path.of("target/packaged-application-" + logName + ".log").toFile();
    var arguments =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar",
                jar.toString(),
                "--server.port=" + port));
    arguments.addAll(List.of(settings));
    var command = new ProcessBuilder(arguments);

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
