package dev.rgonz.cre.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checks one proposed relationship against a catalog with the same rules as manual entry, for
 * features outside this package that suggest relationships but never store them.
 */
public final class CatalogRules {
  private CatalogRules() {}

  /**
   * Lists every problem with a new relationship, or nothing when it could be staged as written.
   * Meaning across the whole batch, such as duplicates or conflicts, is left to the check.
   */
  public static List<String> validateNewRule(
      CatalogSnapshot catalog, UUID sourceId, RelationshipKind kind, List<UUID> targetIds) {
    var create = new Operation.Create(sourceId, kind, targetIds);
    var errors = new ArrayList<>(DraftRules.validate(catalog, List.of(create)));

    if (sourceId != null && targetIds != null && targetIds.contains(sourceId)) {
      errors.add("A feature can't be related to itself");
    }

    return errors;
  }
}
