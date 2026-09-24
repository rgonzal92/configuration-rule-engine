package dev.rgonz.cre.catalog;

import static dev.rgonz.cre.catalog.LaptopSeed.CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU_NEEDS_CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.STYLUS;
import static dev.rgonz.cre.catalog.LaptopSeed.TOUCHSCREEN;
import static dev.rgonz.cre.catalog.LaptopSeed.group;
import static dev.rgonz.cre.catalog.LaptopSeed.ids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves how a selected set of features is explained against the active rules. */
class ConfigurationCheckerTest {
  private final CatalogSnapshot catalog =
      LaptopSeed.catalog(
          GPU_NEEDS_CHARGER,
          group("stylus-touch", STYLUS, RelationshipKind.REQUIRED_WITH, TOUCHSCREEN),
          group("gpu-stylus", GPU, RelationshipKind.NOT_ALLOWED_WITH, STYLUS));

  @Test
  void emptyAndSatisfiedSelectionsAreValid() {
    assertTrue(ConfigurationChecker.check(catalog, List.of()).valid());
    assertTrue(ConfigurationChecker.check(catalog, ids(GPU, CHARGER)).valid());
  }

  @Test
  void explainsMissingRequirements() {
    var result = ConfigurationChecker.check(catalog, ids(GPU));

    assertFalse(result.valid());
    assertEquals(CHARGER.id(), result.missing().getFirst().requiredFeatureId());
    assertEquals(
        "Dedicated GPU requires High-wattage charger", result.missing().getFirst().message());
  }

  @Test
  void requiredWithIsExplainedFromTheChosenFeature() {
    var result = ConfigurationChecker.check(catalog, ids(TOUCHSCREEN));

    assertEquals(STYLUS.id(), result.missing().getFirst().requiredFeatureId());
    assertEquals("Touchscreen requires Stylus support", result.missing().getFirst().message());
  }

  @Test
  void explainsForbiddenPairs() {
    var result = ConfigurationChecker.check(catalog, ids(GPU, CHARGER, STYLUS, TOUCHSCREEN));

    assertFalse(result.valid());
    assertTrue(result.missing().isEmpty());
    assertEquals(
        "Dedicated GPU and Stylus support can't be chosen together",
        result.conflicts().getFirst().message());
  }

  @Test
  void rejectsUnknownFeatures() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfigurationChecker.check(catalog, List.of(UUID.randomUUID())));
  }
}
