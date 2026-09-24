package dev.rgonz.cre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rgonz.cre.catalog.CatalogRepository;
import dev.rgonz.cre.catalog.RuleGraph;
import dev.rgonz.cre.workspace.WorkspacePurger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

/** Exercises catalogs, drafts, checks, and atomic apply over real HTTP. */
class CatalogIT extends ApplicationIT {
  static final String SHOWCASE_LAPTOP = "00000000-0000-0000-0000-00000000c001";
  static final String SHOWCASE_AUTOMOTIVE = "00000000-0000-0000-0000-00000000c002";
  static final String GPU = "00000000-0000-0000-0000-00000000f001";
  static final String CHARGER = "00000000-0000-0000-0000-00000000f002";
  static final String TOUCHSCREEN = "00000000-0000-0000-0000-00000000f004";
  static final String STYLUS = "00000000-0000-0000-0000-00000000f005";
  static final String HIRES = "00000000-0000-0000-0000-00000000f006";
  static final String TOW_PACKAGE = "00000000-0000-0000-0000-00000000f101";

  @Autowired CatalogRepository catalogs;
  @Autowired WorkspacePurger purger;

  /** A started guest and the ID of its own laptop catalog. */
  record Guest(GuestClient client, String catalogId) {
    String path(String suffix) {
      return "/api/catalogs/" + catalogId + suffix;
    }
  }

  private Guest guest() throws Exception {
    var client = visitor();
    client.start();

    var list = client.get("/api/catalogs").body();
    assertEquals("GUEST", list.get(0).path("kind").asString());

    return new Guest(client, list.get(0).path("id").asString());
  }

  private static Map<String, Object> create(String source, String kind, String... targets) {
    return Map.of(
        "type", "CREATE", "sourceFeatureId", source, "kind", kind, "targetFeatureIds", targets);
  }

  private static Map<String, Object> update(String groupId, String... targets) {
    return Map.of("type", "UPDATE", "groupId", groupId, "targetFeatureIds", targets);
  }

  private static Map<String, Object> draft(long version, long revision, Object... operations) {
    return Map.of(
        "expectedDraftVersion", version,
        "expectedRevision", revision,
        "operations", List.of(operations));
  }

  private static Map<String, Object> apply(String commandId, long checkedDraftVersion) {
    return Map.of("commandId", commandId, "checkedDraftVersion", checkedDraftVersion);
  }

  private static List<String> targetsOf(JsonNode catalog, String source) {
    var targets = new ArrayList<String>();

    for (var group : catalog.path("groups")) {
      if (group.path("sourceFeatureId").asString().equals(source)) {
        group.path("targetFeatureIds").forEach(target -> targets.add(target.asString()));
      }
    }

    return targets;
  }

  @Test
  void eachGuestGetsItsOwnCopyOfTheLaptopSeed() throws Exception {
    var first = guest();
    var second = guest();

    var catalog = first.client().get(first.path("")).body();
    assertEquals(11, catalog.path("features").size());
    assertEquals(1, catalog.path("groups").size());
    assertEquals(List.of(CHARGER), targetsOf(catalog, GPU));
    assertEquals(1, catalog.path("revision").asLong());
    assertFalse(catalog.path("readOnly").asBoolean());

    assertFalse(first.catalogId().equals(second.catalogId()));

    var list = first.client().get("/api/catalogs").body();
    assertEquals(3, list.size());
    assertEquals(SHOWCASE_LAPTOP, list.get(1).path("id").asString());
    assertEquals(SHOWCASE_AUTOMOTIVE, list.get(2).path("id").asString());
    assertTrue(list.get(1).path("readOnly").asBoolean());
  }

  @Test
  void anonymousVisitorsSeeOnlyTheShowcase() throws Exception {
    var list = visitor().get("/api/catalogs").body();

    assertEquals(2, list.size());
    assertEquals(SHOWCASE_LAPTOP, list.get(0).path("id").asString());
  }

  @Test
  void everySeededFeatureCanBeChosen() {
    for (var id : List.of(SHOWCASE_LAPTOP, SHOWCASE_AUTOMOTIVE)) {
      var snapshot = catalogs.snapshot(UUID.fromString(id)).orElseThrow();
      var graph = RuleGraph.of(snapshot.features(), snapshot.groups());

      for (var feature : snapshot.features()) {
        var closure = graph.closure(feature.id());

        for (var exclusion : graph.exclusions()) {
          assertFalse(
              closure.contains(exclusion.source()) && closure.contains(exclusion.target()),
              feature.name());
        }
      }
    }
  }

  @Test
  void guestsCannotReadEachOthersCatalogs() throws Exception {
    var owner = guest();
    var other = guest();

    assertEquals(404, other.client().get(owner.path("")).status());
    assertEquals(404, other.client().get(owner.path("/draft")).status());
    assertEquals(404, other.client().putJson(owner.path("/draft"), draft(0, 1)).status());
    assertEquals(404, visitor().get(owner.path("")).status());
  }

  @Test
  void theDatabaseRejectsATargetFromAnotherCatalog() throws Exception {
    var guest = guest();
    var groupId =
        jdbc.sql("SELECT id FROM relationship_group WHERE catalog_id = ?::uuid")
            .param(guest.catalogId())
            .query(UUID.class)
            .single();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.sql(
                    """
                    INSERT INTO relationship_target (catalog_id, group_id, target_feature_id, position)
                    VALUES (?::uuid, ?, ?::uuid, 9)
                    """)
                .params(guest.catalogId(), groupId, TOW_PACKAGE)
                .update());
  }

  @Test
  void purgingAWorkspaceRemovesItsCatalogDraftAndReceipts() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());
    var applied =
        guest.client().postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));
    assertEquals(200, applied.status());
    assertEquals(
        1L,
        jdbc.sql("SELECT count(*) FROM apply_receipt WHERE catalog_id = ?::uuid")
            .param(guest.catalogId())
            .query(Long.class)
            .single());

    clock.set(START.plus(Duration.ofHours(4)));
    purger.purgeExpired();

    for (var table : List.of("catalog", "pending_batch", "apply_receipt", "relationship_group")) {
      var column = table.equals("catalog") ? "id" : "catalog_id";
      var remaining =
          jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid")
              .param(guest.catalogId())
              .query(Long.class)
              .single();
      assertEquals(0L, remaining, table);
    }
  }

  @Test
  void savingADraftBumpsItsVersionAndValidatesStructure() throws Exception {
    var guest = guest();

    var saved =
        guest
            .client()
            .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    assertEquals(200, saved.status());
    assertEquals(1, saved.body().path("draftVersion").asLong());
    assertEquals(1, saved.body().path("operations").size());

    var invalid =
        guest.client().putJson(guest.path("/draft"), draft(1, 1, create(TOUCHSCREEN, "REQUIRES")));
    assertEquals(400, invalid.status());
    assertEquals("INVALID_DRAFT", invalid.text("code"));
    assertEquals(1, invalid.body().path("details").size());

    var tooMany =
        Collections.nCopies(33, (Object) create(TOUCHSCREEN, "REQUIRES", STYLUS)).toArray();
    assertEquals(400, guest.client().putJson(guest.path("/draft"), draft(1, 1, tooMany)).status());

    var unknownType = Map.of("type", "RENAME");
    assertEquals(
        400, guest.client().putJson(guest.path("/draft"), draft(1, 1, unknownType)).status());

    assertEquals(1, guest.client().get(guest.path("/draft")).body().path("draftVersion").asLong());
  }

  @Test
  void concurrentDraftSavesCannotOverwriteEachOther() throws Exception {
    var guest = guest();
    var ready = new CountDownLatch(1);
    var tasks = new ArrayList<Callable<Integer>>();

    for (var target : List.of(STYLUS, HIRES)) {
      tasks.add(
          () -> {
            ready.await();
            var body = draft(0, 1, create(TOUCHSCREEN, "REQUIRES", target));

            return guest.client().putJson(guest.path("/draft"), body).status();
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

    Collections.sort(statuses);
    assertEquals(List.of(200, 409), statuses);
  }

  @Test
  void checkReportsTheBatchEffectAndMarksTheDraftChecked() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS, HIRES)));

    var check = guest.client().postJson(guest.path("/draft/check"), Map.of());
    assertEquals(200, check.status());

    var result = check.body().path("result");
    assertTrue(result.path("valid").asBoolean());
    assertEquals(1536, result.path("validBefore").asLong());
    assertEquals(960, result.path("validAfter").asLong());
    assertEquals(2, result.path("added").size());
    assertTrue(guest.client().get(guest.path("/draft")).body().path("checked").asBoolean());

    guest
        .client()
        .putJson(guest.path("/draft"), draft(1, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    assertFalse(guest.client().get(guest.path("/draft")).body().path("checked").asBoolean());
  }

  @Test
  void emptyAndBlockedDraftsCannotBeApplied() throws Exception {
    var guest = guest();

    var empty = guest.client().postJson(guest.path("/draft/check"), Map.of());
    assertEquals(400, empty.status());
    assertEquals("EMPTY_DRAFT", empty.text("code"));

    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(GPU, "NOT_ALLOWED_WITH", CHARGER)));
    var blocked = guest.client().postJson(guest.path("/draft/check"), Map.of());
    assertFalse(blocked.body().path("result").path("valid").asBoolean());
    assertEquals(
        "FEATURE_UNAVAILABLE",
        blocked.body().path("result").path("blocking").get(0).path("code").asString());

    var apply =
        guest.client().postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));
    assertEquals(409, apply.status());
    assertEquals("CHECK_STALE", apply.text("code"));
  }

  @Test
  void changingTheDraftAfterACheckMakesTheCheckStale() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());
    guest
        .client()
        .putJson(guest.path("/draft"), draft(1, 1, create(TOUCHSCREEN, "REQUIRES", HIRES)));

    var apply =
        guest.client().postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));

    assertEquals(409, apply.status());
    assertEquals("CHECK_STALE", apply.text("code"));
  }

  @Test
  void aDraftBasedOnAnOldRevisionMustBeCheckedAgain() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    jdbc.sql("UPDATE catalog SET revision = revision + 1 WHERE id = ?::uuid")
        .param(guest.catalogId())
        .update();

    var check = guest.client().postJson(guest.path("/draft/check"), Map.of());

    assertEquals(409, check.status());
    assertEquals("STALE_DRAFT", check.text("code"));
  }

  @Test
  void applyChangesTheCatalogAtomicallyAndStartsAFreshDraft() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS, HIRES)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());

    var applied =
        guest.client().postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));
    assertEquals(200, applied.status());
    assertEquals(2, applied.body().path("catalogRevision").asLong());

    var catalog = guest.client().get(guest.path("")).body();
    assertEquals(2, catalog.path("revision").asLong());
    assertEquals(List.of(STYLUS, HIRES), targetsOf(catalog, TOUCHSCREEN));

    var draft = guest.client().get(guest.path("/draft")).body();
    assertEquals(0, draft.path("operations").size());
    assertEquals(2, draft.path("draftVersion").asLong());
    assertEquals(2, draft.path("baseRevision").asLong());

    var groupId = catalog.path("groups").get(1).path("id").asString();
    var stale = guest.client().putJson(guest.path("/draft"), draft(2, 1, update(groupId, STYLUS)));
    assertEquals(409, stale.status());
    assertEquals("CATALOG_CHANGED", stale.text("code"));

    var next = guest.client().putJson(guest.path("/draft"), draft(2, 2, update(groupId, STYLUS)));
    assertEquals(200, next.status());

    var check = guest.client().postJson(guest.path("/draft/check"), Map.of());
    assertEquals(960, check.body().path("result").path("validBefore").asLong());
    assertEquals(1152, check.body().path("result").path("validAfter").asLong());
  }

  @Test
  void draftsKeepWorkingAtLargeRevisionNumbers() throws Exception {
    var guest = guest();
    jdbc.sql("UPDATE catalog SET revision = 200 WHERE id = ?::uuid")
        .param(guest.catalogId())
        .update();
    jdbc.sql("UPDATE pending_batch SET base_revision = 200 WHERE catalog_id = ?::uuid")
        .param(guest.catalogId())
        .update();

    var saved =
        guest
            .client()
            .putJson(guest.path("/draft"), draft(0, 200, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    assertEquals(200, saved.status());

    var check = guest.client().postJson(guest.path("/draft/check"), Map.of());
    assertEquals(200, check.status());

    var applied =
        guest.client().postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));
    assertEquals(201, applied.body().path("catalogRevision").asLong());
  }

  @Test
  void retryingAnApplyReturnsTheSameResultWithoutApplyingTwice() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());
    var command = UUID.randomUUID().toString();

    var first = guest.client().postJson(guest.path("/draft/apply"), apply(command, 1));
    var retry = guest.client().postJson(guest.path("/draft/apply"), apply(command, 1));

    assertEquals(200, retry.status());
    assertEquals(first.body(), retry.body());
    assertEquals(2, guest.client().get(guest.path("")).body().path("revision").asLong());

    var reused = guest.client().postJson(guest.path("/draft/apply"), apply(command, 2));
    assertEquals(409, reused.status());
    assertEquals("COMMAND_REUSED", reused.text("code"));
  }

  @Test
  void aFailureDuringApplyRollsBackEveryChange() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());

    jdbc.sql(
            """
            CREATE FUNCTION fail_revision() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'simulated failure'; END $$
            """)
        .update();
    jdbc.sql(
            "CREATE TRIGGER fail_revision BEFORE UPDATE OF revision ON catalog"
                + " FOR EACH ROW EXECUTE FUNCTION fail_revision()")
        .update();

    try {
      var apply =
          guest
              .client()
              .postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));
      assertEquals(500, apply.status());
    } finally {
      jdbc.sql("DROP TRIGGER fail_revision ON catalog").update();
      jdbc.sql("DROP FUNCTION fail_revision()").update();
    }

    var catalog = guest.client().get(guest.path("")).body();
    assertEquals(1, catalog.path("revision").asLong());
    assertEquals(1, catalog.path("groups").size());
    assertEquals(1, guest.client().get(guest.path("/draft")).body().path("operations").size());
  }

  @Test
  void aWorkspaceThatExpiresDuringApplyRollsTheApplyBack() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());

    // Expires the workspace as soon as apply writes its first target, inside the same transaction.
    jdbc.sql(
            """
            CREATE FUNCTION expire_workspace() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
              UPDATE workspace SET expires_at = created_at
              WHERE id = (SELECT workspace_id FROM catalog WHERE id = NEW.catalog_id);
              RETURN NEW;
            END $$
            """)
        .update();
    jdbc.sql(
            "CREATE TRIGGER expire_workspace AFTER INSERT ON relationship_target"
                + " FOR EACH ROW EXECUTE FUNCTION expire_workspace()")
        .update();

    try {
      var apply =
          guest
              .client()
              .postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));

      assertEquals(401, apply.status());
      assertEquals("SESSION_EXPIRED", apply.text("code"));
    } finally {
      jdbc.sql("DROP TRIGGER expire_workspace ON relationship_target").update();
      jdbc.sql("DROP FUNCTION expire_workspace()").update();
    }

    var catalog = guest.client().get(guest.path("")).body();
    assertEquals(1, catalog.path("revision").asLong());
    assertEquals(1, catalog.path("groups").size());
  }

  @Test
  void applyAfterExpiryIsRefusedAndChangesNothing() throws Exception {
    var guest = guest();
    guest
        .client()
        .putJson(guest.path("/draft"), draft(0, 1, create(TOUCHSCREEN, "REQUIRES", STYLUS)));
    guest.client().postJson(guest.path("/draft/check"), Map.of());

    clock.set(START.plus(Duration.ofHours(4)));
    var apply =
        guest.client().postJson(guest.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1));

    assertEquals(401, apply.status());
    assertEquals(
        1L,
        jdbc.sql("SELECT revision FROM catalog WHERE id = ?::uuid")
            .param(guest.catalogId())
            .query(Long.class)
            .single());
  }

  @Test
  void theShowcaseRefusesEveryChange() throws Exception {
    var guest = guest();
    var showcase = new Guest(guest.client(), SHOWCASE_LAPTOP);

    var refusals =
        List.of(
            guest.client().get(showcase.path("/draft")),
            guest
                .client()
                .putJson(showcase.path("/draft"), draft(0, 1, create(GPU, "REQUIRES", STYLUS))),
            guest.client().postJson(showcase.path("/draft/check"), Map.of()),
            guest
                .client()
                .postJson(showcase.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1)));

    for (var refusal : refusals) {
      assertEquals(403, refusal.status());
      assertEquals("READ_ONLY", refusal.text("code"));
    }

    var anonymous = visitor();
    anonymous.get("/api/session");
    assertEquals(401, anonymous.putJson(showcase.path("/draft"), draft(0, 1)).status());
    assertEquals(401, anonymous.postJson(showcase.path("/draft/check"), Map.of()).status());
    assertEquals(
        401,
        anonymous
            .postJson(showcase.path("/draft/apply"), apply(UUID.randomUUID().toString(), 1))
            .status());

    var catalog = anonymous.get(showcase.path("")).body();
    assertEquals(1, catalog.path("revision").asLong());
    assertTrue(catalog.path("readOnly").asBoolean());
  }

  @Test
  void anyoneCanTestAShowcaseConfiguration() throws Exception {
    // Like the showcase page: list the catalogs, which also sets the CSRF cookie, then test.
    var anonymous = visitor();
    anonymous.get("/api/catalogs");

    var result =
        anonymous.postJson(
            "/api/catalogs/" + SHOWCASE_LAPTOP + "/configurations/check",
            Map.of("featureIds", List.of(GPU)));

    assertEquals(200, result.status());
    assertFalse(result.body().path("valid").asBoolean());
    assertEquals(
        CHARGER, result.body().path("missing").get(0).path("requiredFeatureId").asString());

    var unknown =
        anonymous.postJson(
            "/api/catalogs/" + SHOWCASE_LAPTOP + "/configurations/check",
            Map.of("featureIds", List.of(TOW_PACKAGE)));
    assertEquals(400, unknown.status());
  }
}
