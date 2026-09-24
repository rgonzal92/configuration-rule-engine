package dev.rgonz.cre.catalog;

import static dev.rgonz.cre.catalog.LaptopSeed.BATTERY;
import static dev.rgonz.cre.catalog.LaptopSeed.CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.FANLESS;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU_NEEDS_CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.HIRES;
import static dev.rgonz.cre.catalog.LaptopSeed.STYLUS;
import static dev.rgonz.cre.catalog.LaptopSeed.TOUCHSCREEN;
import static dev.rgonz.cre.catalog.LaptopSeed.group;
import static dev.rgonz.cre.catalog.LaptopSeed.ids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rgonz.cre.catalog.BlockingReason.Code;
import dev.rgonz.cre.catalog.Operation.Create;
import dev.rgonz.cre.catalog.Operation.Delete;
import dev.rgonz.cre.catalog.Operation.Update;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the whole-batch check: rule effects, blocking reasons, and configuration counts. */
class BatchCheckerTest {
  private static CheckResult check(CatalogSnapshot catalog, Operation... operations) {
    return BatchChecker.check(catalog, List.of(operations));
  }

  private static List<Edge> edges(List<EdgeChange> changes) {
    return changes.stream().map(EdgeChange::edge).toList();
  }

  @Test
  void touchscreenNeedingTwoFeaturesLeaves960Configurations() {
    var result =
        check(
            LaptopSeed.seeded(),
            new Create(TOUCHSCREEN.id(), RelationshipKind.REQUIRES, ids(STYLUS, HIRES)));

    assertTrue(result.valid());
    assertEquals(1536, result.validBefore());
    assertEquals(960, result.validAfter());
    assertEquals(
        List.of(
            Edge.implies(TOUCHSCREEN.id(), STYLUS.id()),
            Edge.implies(TOUCHSCREEN.id(), HIRES.id())),
        edges(result.added()));
    assertEquals(
        "Choosing Touchscreen also requires Stylus support", result.added().get(0).description());
  }

  @Test
  void droppingOnePendingTargetLeaves1152Configurations() {
    var result =
        check(
            LaptopSeed.seeded(),
            new Create(TOUCHSCREEN.id(), RelationshipKind.REQUIRES, ids(STYLUS)));

    assertEquals(1152, result.validAfter());
  }

  @Test
  void removingOneActiveTargetRemovesOnlyThatEdge() {
    var touch = group("touch", TOUCHSCREEN, RelationshipKind.REQUIRES, STYLUS, HIRES);
    var catalog = LaptopSeed.catalog(GPU_NEEDS_CHARGER, touch);

    var result = check(catalog, new Update(touch.id(), ids(STYLUS)));

    assertTrue(result.valid());
    assertEquals(960, result.validBefore());
    assertEquals(1152, result.validAfter());
    assertEquals(List.of(Edge.implies(TOUCHSCREEN.id(), HIRES.id())), edges(result.removed()));
    assertTrue(result.added().isEmpty());
  }

  @Test
  void deletingAGroupRemovesAllItsEdges() {
    var result = check(LaptopSeed.seeded(), new Delete(GPU_NEEDS_CHARGER.id()));

    assertTrue(result.valid());
    assertEquals(2048, result.validAfter());
    assertEquals(List.of(Edge.implies(GPU.id(), CHARGER.id())), edges(result.removed()));
  }

  @Test
  void sourceOrTypeIsReplacedByDeletingAndCreating() {
    var result =
        check(
            LaptopSeed.seeded(),
            new Delete(GPU_NEEDS_CHARGER.id()),
            new Create(GPU.id(), RelationshipKind.NOT_ALLOWED_WITH, ids(FANLESS)));

    assertTrue(result.valid());
    assertEquals(1536, result.validAfter());
    assertEquals(List.of(Edge.excludes(GPU.id(), FANLESS.id())), edges(result.added()));
    assertEquals(List.of(Edge.implies(GPU.id(), CHARGER.id())), edges(result.removed()));
    assertEquals(
        "Dedicated GPU and Fanless chassis can't be chosen together",
        result.added().getFirst().description());
  }

  @Test
  void requiredWithPointsFromTargetToSource() {
    var result =
        check(
            LaptopSeed.seeded(),
            new Create(STYLUS.id(), RelationshipKind.REQUIRED_WITH, ids(TOUCHSCREEN)));

    assertEquals(List.of(Edge.implies(TOUCHSCREEN.id(), STYLUS.id())), edges(result.added()));
    assertEquals(1152, result.validAfter());
  }

  @Test
  void excludingTheRequiredChargerMakesTheGpuUnavailable() {
    var result =
        check(
            LaptopSeed.seeded(),
            new Create(GPU.id(), RelationshipKind.NOT_ALLOWED_WITH, ids(CHARGER)));

    assertFalse(result.valid());
    var reason = result.blocking().getFirst();
    assertEquals(Code.FEATURE_UNAVAILABLE, reason.code());
    assertEquals(GPU.id(), reason.featureIds().getFirst());
    assertTrue(
        reason.message().startsWith("Dedicated GPU could never be chosen"), reason.message());
  }

  @Test
  void conflictsAreFoundThroughRequirementChains() {
    var result =
        check(
            LaptopSeed.seeded(),
            new Create(CHARGER.id(), RelationshipKind.REQUIRES, ids(BATTERY)),
            new Create(GPU.id(), RelationshipKind.NOT_ALLOWED_WITH, ids(BATTERY)));

    assertFalse(result.valid());
    assertEquals(
        List.of(Code.FEATURE_UNAVAILABLE),
        result.blocking().stream().map(BlockingReason::code).distinct().toList());
    assertEquals(GPU.id(), result.blocking().getFirst().featureIds().getFirst());
  }

  @Test
  void newIndirectRequirementsAreReportedWithTheirPath() {
    var result =
        check(
            LaptopSeed.seeded(), new Create(CHARGER.id(), RelationshipKind.REQUIRES, ids(BATTERY)));

    assertTrue(result.valid());
    var indirect = result.indirect().getFirst();
    assertEquals(GPU.id(), indirect.featureId());
    assertEquals(BATTERY.id(), indirect.requiredFeatureId());
    assertEquals(ids(GPU, CHARGER, BATTERY), indirect.path());
    assertEquals(
        "Choosing Dedicated GPU also requires Extra battery through High-wattage charger",
        indirect.description());
  }

  @Test
  void requirementCyclesAreAllowed() {
    var result =
        check(LaptopSeed.seeded(), new Create(CHARGER.id(), RelationshipKind.REQUIRES, ids(GPU)));

    assertTrue(result.valid());
    assertEquals(1024, result.validAfter());
  }

  @Test
  void selfRelationsAreBlocked() {
    var result =
        check(LaptopSeed.seeded(), new Create(GPU.id(), RelationshipKind.REQUIRES, ids(GPU)));

    assertFalse(result.valid());
    assertEquals(Code.SELF_RELATION, result.blocking().getFirst().code());
  }

  @Test
  void theSameLogicalRuleCannotBeStatedTwice() {
    var reversed =
        check(
            LaptopSeed.seeded(),
            new Create(CHARGER.id(), RelationshipKind.REQUIRED_WITH, ids(GPU)));
    var swappedExclusion =
        check(
            LaptopSeed.seeded(),
            new Create(STYLUS.id(), RelationshipKind.NOT_ALLOWED_WITH, ids(HIRES)),
            new Create(HIRES.id(), RelationshipKind.NOT_ALLOWED_WITH, ids(STYLUS)));

    assertEquals(Code.DUPLICATE_RELATIONSHIP, reversed.blocking().getFirst().code());
    assertEquals(Code.DUPLICATE_RELATIONSHIP, swappedExclusion.blocking().getFirst().code());
  }

  @Test
  void structurallyInvalidDraftsAreRejectedBeforeChecking() {
    assertThrows(
        IllegalArgumentException.class,
        () -> check(LaptopSeed.seeded(), new Update(GPU_NEEDS_CHARGER.id(), List.of())));
    assertThrows(IllegalArgumentException.class, () -> check(LaptopSeed.seeded()));
  }
}
