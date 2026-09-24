package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Plain-language wording for relationships, using feature names. */
final class RuleText {
  private RuleText() {}

  /** Describes an edge, keeping exclusions in the order the visitor wrote them. */
  static String describe(RuleGraph.SourcedEdge sourced, Map<UUID, String> names) {
    var edge = sourced.edge();

    if (edge.type() == Edge.Type.IMPLIES) {
      return "Choosing " + names.get(edge.from()) + " also requires " + names.get(edge.to());
    }

    return names.get(sourced.source())
        + " and "
        + names.get(sourced.target())
        + " can't be chosen together";
  }

  static String chain(List<UUID> path, Map<UUID, String> names) {
    return path.stream().map(names::get).collect(Collectors.joining(" → "));
  }
}
