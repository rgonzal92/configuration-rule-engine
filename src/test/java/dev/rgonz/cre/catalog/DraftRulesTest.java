package dev.rgonz.cre.catalog;

import static dev.rgonz.cre.catalog.LaptopSeed.CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU;
import static dev.rgonz.cre.catalog.LaptopSeed.GPU_NEEDS_CHARGER;
import static dev.rgonz.cre.catalog.LaptopSeed.STYLUS;
import static dev.rgonz.cre.catalog.LaptopSeed.TOUCHSCREEN;
import static dev.rgonz.cre.catalog.LaptopSeed.ids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rgonz.cre.catalog.Operation.Create;
import dev.rgonz.cre.catalog.Operation.Delete;
import dev.rgonz.cre.catalog.Operation.Update;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves the structural limits every draft must meet before it is stored. */
class DraftRulesTest {
  private final CatalogSnapshot catalog = LaptopSeed.seeded();

  private List<String> errors(Operation... operations) {
    return DraftRules.validate(catalog, List.of(operations));
  }

  @Test
  void acceptsTheThreeOperationShapes() {
    assertTrue(
        errors(
                new Create(TOUCHSCREEN.id(), RelationshipKind.REQUIRES, ids(STYLUS)),
                new Update(GPU_NEEDS_CHARGER.id(), ids(CHARGER, STYLUS)))
            .isEmpty());
    assertTrue(errors(new Delete(GPU_NEEDS_CHARGER.id())).isEmpty());
    assertTrue(DraftRules.validate(catalog, List.of()).isEmpty());
  }

  @Test
  void limitsTheBatchToThirtyTwoOperations() {
    var create = new Create(TOUCHSCREEN.id(), RelationshipKind.REQUIRES, ids(STYLUS));

    assertTrue(DraftRules.validate(catalog, Collections.nCopies(32, create)).isEmpty());
    assertEquals(1, DraftRules.validate(catalog, Collections.nCopies(33, create)).size());
  }

  @Test
  void requiresOneToTenDistinctTargets() {
    var eleven = LaptopSeed.FEATURES.stream().map(Feature::id).toList();

    assertEquals(1, errors(new Create(GPU.id(), RelationshipKind.REQUIRES, List.of())).size());
    assertEquals(1, errors(new Create(GPU.id(), RelationshipKind.REQUIRES, eleven)).size());
    assertEquals(
        1, errors(new Create(GPU.id(), RelationshipKind.REQUIRES, ids(STYLUS, STYLUS))).size());
    assertEquals(1, errors(new Update(GPU_NEEDS_CHARGER.id(), List.of())).size());
  }

  @Test
  void rejectsUnknownFeaturesAndGroups() {
    var unknown = UUID.randomUUID();

    assertEquals(1, errors(new Create(unknown, RelationshipKind.REQUIRES, ids(STYLUS))).size());
    assertEquals(
        1, errors(new Create(GPU.id(), RelationshipKind.REQUIRES, List.of(unknown))).size());
    assertEquals(1, errors(new Update(unknown, ids(STYLUS))).size());
    assertEquals(1, errors(new Delete(unknown)).size());
  }

  @Test
  void rejectsMissingFields() {
    assertEquals(1, errors(new Create(null, RelationshipKind.REQUIRES, ids(STYLUS))).size());
    assertEquals(1, errors(new Create(GPU.id(), null, ids(STYLUS))).size());
  }

  @Test
  void allowsOnlyOneOperationPerActiveGroup() {
    var errors =
        errors(new Update(GPU_NEEDS_CHARGER.id(), ids(STYLUS)), new Delete(GPU_NEEDS_CHARGER.id()));

    assertEquals(1, errors.size());
  }
}
