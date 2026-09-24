package dev.rgonz.cre.catalog;

import static dev.rgonz.cre.catalog.LaptopSeed.GPU;
import static dev.rgonz.cre.catalog.LaptopSeed.STYLUS;
import static dev.rgonz.cre.catalog.LaptopSeed.TOUCHSCREEN;
import static dev.rgonz.cre.catalog.LaptopSeed.ids;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves a single proposed relationship is held to the same rules as manual entry. */
class CatalogRulesTest {
  private final CatalogSnapshot catalog = LaptopSeed.seeded();

  private boolean valid(UUID source, RelationshipKind kind, List<UUID> targets) {
    return CatalogRules.validateNewRule(catalog, source, kind, targets).isEmpty();
  }

  @Test
  void acceptsARuleBetweenCatalogFeatures() {
    assertTrue(valid(TOUCHSCREEN.id(), RelationshipKind.REQUIRES, ids(STYLUS)));
  }

  @Test
  void rejectsFeaturesOutsideTheCatalog() {
    assertFalse(valid(UUID.randomUUID(), RelationshipKind.REQUIRES, ids(STYLUS)));
    assertFalse(valid(GPU.id(), RelationshipKind.REQUIRES, List.of(UUID.randomUUID())));
  }

  @Test
  void rejectsMissingOrRepeatedOrTooManyTargets() {
    var eleven = LaptopSeed.FEATURES.stream().map(Feature::id).toList();

    assertFalse(valid(GPU.id(), RelationshipKind.REQUIRES, List.of()));
    assertFalse(valid(GPU.id(), RelationshipKind.REQUIRES, null));
    assertFalse(valid(GPU.id(), RelationshipKind.REQUIRES, ids(STYLUS, STYLUS)));
    assertFalse(valid(GPU.id(), RelationshipKind.REQUIRES, eleven));
    assertFalse(valid(GPU.id(), null, ids(STYLUS)));
  }

  @Test
  void rejectsAFeatureRelatedToItself() {
    assertFalse(valid(GPU.id(), RelationshipKind.NOT_ALLOWED_WITH, ids(GPU)));
  }
}
