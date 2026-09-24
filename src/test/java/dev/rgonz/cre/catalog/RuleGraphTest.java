package dev.rgonz.cre.catalog;

import static dev.rgonz.cre.catalog.LaptopSeed.BATTERY;
import static dev.rgonz.cre.catalog.LaptopSeed.CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU;
import static dev.rgonz.cre.catalog.LaptopSeed.STYLUS;
import static dev.rgonz.cre.catalog.LaptopSeed.TOUCHSCREEN;
import static dev.rgonz.cre.catalog.LaptopSeed.group;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves how relationship groups become logical edges and how configurations are counted. */
class RuleGraphTest {
  @Test
  void seededLaptopCatalogHas1536ValidConfigurations() {
    var graph = RuleGraph.of(LaptopSeed.FEATURES, List.of(LaptopSeed.GPU_NEEDS_CHARGER));

    assertEquals(1536, graph.countValidSubsets());
  }

  @Test
  void catalogWithoutRulesCountsEverySubsetIncludingTheEmptyOne() {
    assertEquals(2048, RuleGraph.of(LaptopSeed.FEATURES, List.of()).countValidSubsets());
  }

  @Test
  void eachKindProducesItsOwnDirection() {
    var requires = group("a", TOUCHSCREEN, RelationshipKind.REQUIRES, STYLUS);
    var requiredWith = group("b", STYLUS, RelationshipKind.REQUIRED_WITH, TOUCHSCREEN);
    var excluded = group("c", STYLUS, RelationshipKind.NOT_ALLOWED_WITH, TOUCHSCREEN);

    assertEquals(
        List.of(Edge.implies(TOUCHSCREEN.id(), STYLUS.id())),
        RuleGraph.of(LaptopSeed.FEATURES, List.of(requires)).logicalEdges());
    assertEquals(
        List.of(Edge.implies(TOUCHSCREEN.id(), STYLUS.id())),
        RuleGraph.of(LaptopSeed.FEATURES, List.of(requiredWith)).logicalEdges());
    assertEquals(
        List.of(Edge.excludes(TOUCHSCREEN.id(), STYLUS.id())),
        RuleGraph.of(LaptopSeed.FEATURES, List.of(excluded)).logicalEdges());
  }

  @Test
  void exclusionsAreUnorderedPairs() {
    assertEquals(Edge.excludes(GPU.id(), CHARGER.id()), Edge.excludes(CHARGER.id(), GPU.id()));
  }

  @Test
  void requirementPathsFollowChains() {
    var chain =
        List.of(
            LaptopSeed.GPU_NEEDS_CHARGER,
            group("charger-battery", CHARGER, RelationshipKind.REQUIRES, BATTERY));
    var graph = RuleGraph.of(LaptopSeed.FEATURES, chain);

    assertEquals(
        List.of(GPU.id(), CHARGER.id(), BATTERY.id()),
        graph.requirementPath(GPU.id(), BATTERY.id()));
    assertTrue(graph.requirementPath(BATTERY.id(), GPU.id()).isEmpty());
    assertTrue(graph.closure(GPU.id()).containsAll(List.of(GPU.id(), CHARGER.id(), BATTERY.id())));
  }

  @Test
  void refusesCatalogsLargerThanTwelveFeatures() {
    var features = new ArrayList<>(LaptopSeed.FEATURES);
    features.add(new Feature(UUID.randomUUID(), "EXTRA_1", "Extra 1"));
    features.add(new Feature(UUID.randomUUID(), "EXTRA_2", "Extra 2"));
    var graph = RuleGraph.of(features, List.of());

    assertThrows(IllegalStateException.class, graph::countValidSubsets);
  }
}
