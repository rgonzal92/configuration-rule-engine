package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.BlockingReason.Code;
import dev.rgonz.cre.catalog.Operation.Create;
import dev.rgonz.cre.catalog.Operation.Delete;
import dev.rgonz.cre.catalog.Operation.Update;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Evaluates a whole pending batch against the catalog's active relationships. */
final class BatchChecker {
  private BatchChecker() {}

  /**
   * Applies the batch to a copy of the active groups and compares the result with the catalog.
   *
   * @throws IllegalArgumentException if the batch is empty or structurally invalid
   */
  public static CheckResult check(CatalogSnapshot catalog, List<Operation> operations) {
    var errors = DraftRules.validate(catalog, operations);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("; ", errors));
    }

    if (operations.isEmpty()) {
      throw new IllegalArgumentException("The batch is empty");
    }

    var names = catalog.names();
    var proposed = apply(catalog.groups(), operations);

    var before = RuleGraph.of(catalog.features(), catalog.groups());
    var after = RuleGraph.of(catalog.features(), proposed);

    var blocking = new ArrayList<BlockingReason>();
    blocking.addAll(selfRelations(proposed, names));
    blocking.addAll(duplicates(after, names));
    blocking.addAll(unavailableFeatures(catalog, after, names));

    return new CheckResult(
        blocking.isEmpty(),
        difference(after, before, names),
        difference(before, after, names),
        newIndirectRequirements(catalog, before, after, names),
        blocking,
        before.countValidSubsets(),
        after.countValidSubsets());
  }

  /** The groups that would be active after the batch; new groups have no ID yet. */
  private static List<RelationshipGroup> apply(
      List<RelationshipGroup> active, List<Operation> operations) {
    var groups = new LinkedHashMap<UUID, RelationshipGroup>();
    active.forEach(group -> groups.put(group.id(), group));

    var created = new ArrayList<RelationshipGroup>();

    for (var operation : operations) {
      switch (operation) {
        case Create create ->
            created.add(
                new RelationshipGroup(null, create.sourceId(), create.kind(), create.targetIds()));

        case Update update -> {
          var group = groups.get(update.groupId());
          groups.put(
              group.id(),
              new RelationshipGroup(
                  group.id(), group.sourceId(), group.kind(), update.targetIds()));
        }

        case Delete delete -> groups.remove(delete.groupId());
      }
    }

    var result = new ArrayList<>(groups.values());
    result.addAll(created);

    return result;
  }

  /** Edges present in {@code first} but not in {@code second}. */
  private static List<EdgeChange> difference(
      RuleGraph first, RuleGraph second, Map<UUID, String> names) {
    var existing = new HashSet<>(second.logicalEdges());
    var seen = new HashSet<Edge>();
    var changes = new ArrayList<EdgeChange>();

    for (var sourced : first.sourcedEdges()) {
      if (!existing.contains(sourced.edge()) && seen.add(sourced.edge())) {
        changes.add(new EdgeChange(sourced.edge(), RuleText.describe(sourced, names)));
      }
    }

    return changes;
  }

  private static List<BlockingReason> selfRelations(
      List<RelationshipGroup> groups, Map<UUID, String> names) {
    var reasons = new ArrayList<BlockingReason>();

    for (var group : groups) {
      if (group.targetIds().contains(group.sourceId())) {
        var name = names.get(group.sourceId());
        reasons.add(
            new BlockingReason(
                Code.SELF_RELATION,
                List.of(group.sourceId()),
                name + " can't be related to itself"));
      }
    }

    return reasons;
  }

  /**
   * The same logical edge stated by more than one group, such as A requires B and B required with
   * A.
   */
  private static List<BlockingReason> duplicates(RuleGraph graph, Map<UUID, String> names) {
    var firstSeen = new LinkedHashMap<Edge, RuleGraph.SourcedEdge>();
    var reported = new HashSet<Edge>();
    var reasons = new ArrayList<BlockingReason>();

    for (var sourced : graph.sourcedEdges()) {
      var earlier = firstSeen.putIfAbsent(sourced.edge(), sourced);

      if (earlier != null && reported.add(sourced.edge())) {
        var edge = sourced.edge();
        reasons.add(
            new BlockingReason(
                Code.DUPLICATE_RELATIONSHIP,
                List.of(edge.from(), edge.to()),
                "\"" + RuleText.describe(earlier, names) + "\" is stated more than once"));
      }
    }

    return reasons;
  }

  /**
   * A feature can be chosen only if everything it requires can be chosen together. Its smallest
   * possible configuration is its requirement closure, so the feature is unavailable exactly when
   * that closure contains an excluded pair.
   */
  private static List<BlockingReason> unavailableFeatures(
      CatalogSnapshot catalog, RuleGraph graph, Map<UUID, String> names) {
    var reasons = new ArrayList<BlockingReason>();

    for (var feature : catalog.features()) {
      var closure = graph.closure(feature.id());

      for (var exclusion : graph.exclusions()) {
        var first = exclusion.source();
        var second = exclusion.target();

        if (closure.contains(first) && closure.contains(second)) {
          reasons.add(
              new BlockingReason(
                  Code.FEATURE_UNAVAILABLE,
                  List.of(feature.id(), first, second),
                  unavailableMessage(graph, feature.id(), first, second, names)));
          break;
        }
      }
    }

    return reasons;
  }

  private static String unavailableMessage(
      RuleGraph graph, UUID feature, UUID first, UUID second, Map<UUID, String> names) {
    var message =
        new StringBuilder(names.get(feature))
            .append(" could never be chosen: it needs both ")
            .append(names.get(first))
            .append(" and ")
            .append(names.get(second))
            .append(", which can't be chosen together.");

    for (var end : List.of(first, second)) {
      var path = graph.requirementPath(feature, end);

      if (path.size() > 1) {
        message.append(" Requirement chain: ").append(RuleText.chain(path, names)).append('.');
      }
    }

    return message.toString();
  }

  /** Requirements the batch creates only through chains, which were not already required. */
  private static List<IndirectRequirement> newIndirectRequirements(
      CatalogSnapshot catalog, RuleGraph before, RuleGraph after, Map<UUID, String> names) {
    var indirect = new ArrayList<IndirectRequirement>();

    for (var feature : catalog.features()) {
      var id = feature.id();
      var alreadyRequired = before.closure(id);

      for (var required : after.closure(id)) {
        boolean chained = !required.equals(id) && !after.requiresDirectly(id, required);

        if (chained && !alreadyRequired.contains(required)) {
          var path = after.requirementPath(id, required);
          var through = RuleText.chain(path.subList(1, path.size() - 1), names);

          indirect.add(
              new IndirectRequirement(
                  id,
                  required,
                  path,
                  "Choosing "
                      + names.get(id)
                      + " also requires "
                      + names.get(required)
                      + " through "
                      + through));
        }
      }
    }

    return indirect;
  }
}
