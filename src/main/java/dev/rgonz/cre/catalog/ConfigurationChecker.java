package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.ConfigurationResult.ForbiddenPair;
import dev.rgonz.cre.catalog.ConfigurationResult.MissingRequirement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Explains whether a selected set of features satisfies a catalog's active relationships. */
final class ConfigurationChecker {
  private ConfigurationChecker() {}

  /**
   * Lists every missing requirement and forbidden pair among the selected features.
   *
   * @throws IllegalArgumentException if a selected feature is not in the catalog
   */
  public static ConfigurationResult check(CatalogSnapshot catalog, List<UUID> selectedIds) {
    var selected = new LinkedHashSet<>(selectedIds);
    if (!selected.stream().allMatch(catalog::hasFeature)) {
      throw new IllegalArgumentException("A selected feature is not in this catalog");
    }

    var names = catalog.names();
    var graph = RuleGraph.of(catalog.features(), catalog.groups());

    var missing = new ArrayList<MissingRequirement>();
    var conflicts = new ArrayList<ForbiddenPair>();
    var reported = new HashSet<Edge>();

    for (var sourced : graph.sourcedEdges()) {
      var edge = sourced.edge();
      boolean first = selected.contains(edge.from());
      boolean second = selected.contains(edge.to());

      if (!reported.add(edge)) {
        continue;
      }

      if (edge.type() == Edge.Type.IMPLIES && first && !second) {
        missing.add(
            new MissingRequirement(
                edge.from(),
                edge.to(),
                names.get(edge.from()) + " requires " + names.get(edge.to())));
      }

      if (edge.type() == Edge.Type.EXCLUDES && first && second) {
        conflicts.add(
            new ForbiddenPair(
                sourced.source(), sourced.target(), RuleText.describe(sourced, names)));
      }
    }

    return new ConfigurationResult(missing.isEmpty() && conflicts.isEmpty(), missing, conflicts);
  }
}
