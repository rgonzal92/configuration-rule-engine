package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.Operation.Create;
import dev.rgonz.cre.catalog.Operation.Delete;
import dev.rgonz.cre.catalog.Operation.Update;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Structural limits a draft must meet before it is stored or checked. */
final class DraftRules {
  public static final int MAX_OPERATIONS = 32;
  public static final int MAX_TARGETS = 10;

  private DraftRules() {}

  /**
   * Lists every structural problem in the draft, or nothing when it is well formed. Relationship
   * meaning, such as duplicates or conflicts, is left to {@link BatchChecker}.
   */
  public static List<String> validate(CatalogSnapshot catalog, List<Operation> operations) {
    var errors = new ArrayList<String>();

    if (operations.size() > MAX_OPERATIONS) {
      errors.add("A batch can hold at most " + MAX_OPERATIONS + " changes");
    }

    var addressedGroups = new HashSet<UUID>();

    for (int i = 0; i < operations.size(); i++) {
      var prefix = "Change " + (i + 1) + ": ";

      switch (operations.get(i)) {
        case Create create -> {
          if (create.sourceId() == null) {
            errors.add(prefix + "choose a source feature");
          } else if (!catalog.hasFeature(create.sourceId())) {
            errors.add(prefix + "the source feature is not in this catalog");
          }

          if (create.kind() == null) {
            errors.add(prefix + "choose a relationship type");
          }

          checkTargets(catalog, create.targetIds(), prefix, errors);
        }

        case Update update -> {
          checkGroup(catalog, update.groupId(), addressedGroups, prefix, errors);
          checkTargets(catalog, update.targetIds(), prefix, errors);
        }

        case Delete delete ->
            checkGroup(catalog, delete.groupId(), addressedGroups, prefix, errors);

        case null -> errors.add(prefix + "the change is empty");
      }
    }

    return errors;
  }

  private static void checkGroup(
      CatalogSnapshot catalog,
      UUID groupId,
      HashSet<UUID> addressedGroups,
      String prefix,
      List<String> errors) {
    if (groupId == null || catalog.group(groupId).isEmpty()) {
      errors.add(prefix + "the relationship is not active in this catalog");
    } else if (!addressedGroups.add(groupId)) {
      errors.add(prefix + "another change already edits or removes this relationship");
    }
  }

  private static void checkTargets(
      CatalogSnapshot catalog, List<UUID> targetIds, String prefix, List<String> errors) {
    if (targetIds == null || targetIds.isEmpty() || targetIds.size() > MAX_TARGETS) {
      errors.add(prefix + "choose between 1 and " + MAX_TARGETS + " target features");
    } else if (new HashSet<>(targetIds).size() != targetIds.size()) {
      errors.add(prefix + "each target feature can be chosen only once");
    } else if (!targetIds.stream().allMatch(catalog::hasFeature)) {
      errors.add(prefix + "a target feature is not in this catalog");
    }
  }
}
