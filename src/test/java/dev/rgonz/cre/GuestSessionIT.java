package dev.rgonz.cre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rgonz.cre.workspace.WorkspacePurger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** Exercises guest sessions, expiry, CSRF, isolation, and the creation limit over real HTTP. */
class GuestSessionIT extends ApplicationIT {
  @Autowired WorkspacePurger purger;
  @Autowired ScheduledTaskHolder scheduledTasks;
  @Autowired ServerProperties server;

  @Test
  void startedGuestKeepsOneWorkspace() throws Exception {
    var guest = visitor();
    assertEquals("ANONYMOUS", guest.get("/api/session").text("status"));

    var started = guest.start();
    assertEquals(201, started.status());

    var workspaceId = started.text("workspaceId");
    assertEquals(START.plus(Duration.ofHours(4)), Instant.parse(started.text("expiresAt")));

    var session = guest.get("/api/session");
    assertEquals("GUEST", session.text("status"));
    assertEquals(workspaceId, session.text("workspaceId"));

    var again = guest.post("/api/demo/sessions");
    assertEquals(200, again.status());
    assertEquals(workspaceId, again.text("workspaceId"));
    assertEquals(1L, guestCount());

    var storedTimeout =
        jdbc.sql("SELECT max_inactive_interval FROM spring_session").query(Integer.class).single();
    assertEquals(Duration.ofHours(4).toSeconds(), storedTimeout.longValue());
  }

  @Test
  void workspaceExpiresAtItsExactExpiryAndInvalidatesTheSession() throws Exception {
    var guest = visitor();
    guest.start();
    var sessionCookie = guest.cookie("SESSION");

    clock.set(START.plus(Duration.ofHours(4)).minusSeconds(1));
    assertEquals("GUEST", guest.get("/api/session").text("status"));

    clock.set(START.plus(Duration.ofHours(4)));
    assertEquals("EXPIRED", guest.get("/api/session").text("status"));

    guest.setCookie("SESSION", sessionCookie);
    assertEquals("ANONYMOUS", guest.get("/api/session").text("status"));
  }

  @Test
  void pageAndAssetRequestsLeaveTheExpiryForTheApiToReport() throws Exception {
    var guest = visitor();
    guest.start();
    clock.set(START.plus(Duration.ofHours(4)));

    guest.get("/workspace");
    guest.get("/favicon.ico");
    assertEquals("EXPIRED", guest.get("/api/session").text("status"));
  }

  @Test
  void guestOnlyRequestAfterExpiryExplainsTheExpiry() throws Exception {
    var guest = visitor();
    guest.start();
    clock.set(START.plus(Duration.ofHours(4)));

    var write = guest.post("/api/catalogs");
    assertEquals(401, write.status());
    assertEquals("SESSION_EXPIRED", write.text("code"));
  }

  @Test
  void purgeRemovesOnlyExpiredWorkspaces() throws Exception {
    var expiring = visitor();
    expiring.start();

    clock.set(START.plus(Duration.ofHours(2)));
    var live = visitor();
    live.start();

    clock.set(START.plus(Duration.ofHours(4)));
    purger.purgeExpired();

    assertEquals(1L, guestCount());
    assertEquals("GUEST", live.get("/api/session").text("status"));
    assertEquals("EXPIRED", expiring.get("/api/session").text("status"));
  }

  @Test
  void purgeRunsOnASchedule() {
    var purgeTask = WorkspacePurger.class.getName() + ".purgeExpired";

    var scheduled =
        scheduledTasks.getScheduledTasks().stream()
            .map(ScheduledTask::getTask)
            .filter(FixedDelayTask.class::isInstance)
            .anyMatch(task -> task.toString().equals(purgeTask));

    assertTrue(scheduled);
  }

  @Test
  void aPlantedSessionIsNeverReusedForANewWorkspace() throws Exception {
    var attacker = visitor();
    attacker.start();
    var planted = attacker.cookie("SESSION");

    clock.set(START.plus(Duration.ofHours(4)));

    var victim = visitor();
    victim.get("/api/session");
    victim.setCookie("SESSION", planted);
    assertEquals(201, victim.post("/api/demo/sessions").status());

    assertNotEquals(planted, victim.cookie("SESSION"));
    assertEquals(1L, jdbc.sql("SELECT count(*) FROM spring_session").query(Long.class).single());
  }

  @Test
  void forwardedHeadersAreNeverTrusted() throws Exception {
    assertEquals(ServerProperties.ForwardHeadersStrategy.NONE, server.getForwardHeadersStrategy());
  }

  @Test
  void writesRequireTheEchoedCsrfToken() throws Exception {
    var guest = visitor();
    guest.get("/api/session");
    assertTrue(guest.cookie("XSRF-TOKEN") != null);

    var missing = guest.post("/api/demo/sessions", null);
    assertEquals(403, missing.status());
    assertEquals("CSRF_INVALID", missing.text("code"));

    var wrong = guest.post("/api/demo/sessions", "not-the-token");
    assertEquals(403, wrong.status());
    assertEquals("CSRF_INVALID", wrong.text("code"));
    assertEquals(0L, guestCount());
  }

  @Test
  void twoGuestsAreSeparated() throws Exception {
    var first = visitor();
    var second = visitor();

    var a = first.start().text("workspaceId");
    var b = second.start().text("workspaceId");

    assertNotEquals(a, b);
    assertEquals(a, first.get("/api/session").text("workspaceId"));
    assertEquals(b, second.get("/api/session").text("workspaceId"));

    assertEquals(
        2L,
        jdbc.sql("SELECT count(DISTINCT owner_id) FROM workspace WHERE kind = 'GUEST'")
            .query(Long.class)
            .single());
  }

  @Test
  void anonymousWritesAreRefusedAndShowcaseReadsSucceed() throws Exception {
    var anonymous = visitor();
    anonymous.get("/api/session");

    var write = anonymous.post("/api/catalogs");
    assertEquals(401, write.status());
    assertEquals("UNAUTHENTICATED", write.text("code"));

    var showcase = anonymous.get("/api/showcase");
    assertEquals(200, showcase.status());
    assertTrue(showcase.body().path("readOnly").asBoolean());
    assertEquals("00000000-0000-0000-0000-000000000001", showcase.text("workspaceId"));
  }

  @Test
  void cookiesAreSecureAndSameSiteLax() throws Exception {
    var guest = visitor();
    guest.start();

    var session = guest.setCookieHeaders("SESSION").getLast();
    assertTrue(session.contains("Secure"), session);
    assertTrue(session.contains("HttpOnly"), session);
    assertTrue(session.contains("SameSite=Lax"), session);

    var csrf = guest.setCookieHeaders("XSRF-TOKEN").getFirst();
    assertTrue(csrf.contains("Secure"), csrf);
    assertFalse(csrf.contains("HttpOnly"), "Angular must be able to read the CSRF cookie");
    assertTrue(csrf.contains("SameSite=Lax"), csrf);
  }

  @Test
  void concurrentStartsNeverExceedTheHourlyLimit() throws Exception {
    var ready = new CountDownLatch(1);
    var tasks = new ArrayList<Callable<Integer>>();

    for (int i = 0; i < 60; i++) {
      var guest = visitor();
      guest.get("/api/session");

      tasks.add(
          () -> {
            ready.await();
            return guest.post("/api/demo/sessions").status();
          });
    }

    var statuses = new ArrayList<Integer>();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = tasks.stream().map(pool::submit).toList();
      ready.countDown();

      for (var future : futures) {
        statuses.add(future.get());
      }
    }

    assertEquals(50, statuses.stream().filter(s -> s == 201).count());
    assertEquals(10, statuses.stream().filter(s -> s == 429).count());
    assertEquals(50L, guestCount());

    clock.set(START.plus(Duration.ofHours(1)).plusSeconds(1));
    assertEquals(201, visitor().start().status());
  }

  @Test
  void limitRejectionExplainsItself() throws Exception {
    jdbc.sql(
            """
            INSERT INTO workspace (id, kind, owner_id, created_at, expires_at)
            SELECT gen_random_uuid(), 'GUEST', gen_random_uuid(), :at, :at::timestamptz + interval '4 hours'
            FROM generate_series(1, 50)
            """)
        .param("at", java.sql.Timestamp.from(START.minusSeconds(60)))
        .update();

    var refused = visitor().start();
    assertEquals(429, refused.status());
    assertEquals("GUEST_LIMIT_REACHED", refused.text("code"));
  }
}
