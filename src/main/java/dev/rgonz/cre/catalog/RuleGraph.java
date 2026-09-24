package dev.rgonz.cre.catalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The logical relationships formed by a set of groups: directed implications and unordered
 * exclusions. Self-relations form no edge; {@link BatchChecker} reports them instead.
 */
public final class RuleGraph {
  /** Exact counting enumerates every subset, so it is limited to 2^12 = 4,096 subsets. */
  static final int MAX_COUNTED_FEATURES = 12;

  /**
   * One logical relationship and the authored group it came from.
   *
   * @param source the group's source feature, kept for wording exclusions as they were written
   * @param target the target feature, kept for the same reason
   */
  public record SourcedEdge(Edge edge, UUID source, UUID target, RelationshipGroup group) {}

  private final List<Feature> features;
  private final List<SourcedEdge> edges;
  private final Map<UUID, List<UUID>> required = new HashMap<>();

  private RuleGraph(List<Feature> features, List<SourcedEdge> edges) {
    this.features = List.copyOf(features);
    this.edges = List.copyOf(edges);

    for (var sourced : edges) {
      var edge = sourced.edge();
      if (edge.type() == Edge.Type.IMPLIES) {
        required.computeIfAbsent(edge.from(), id -> new ArrayList<>()).add(edge.to());
      }
    }
  }

  public static RuleGraph of(List<Feature> features, List<RelationshipGroup> groups) {
    var edges = new ArrayList<SourcedEdge>();

    for (var group : groups) {
      for (var target : group.targetIds()) {
        if (!target.equals(group.sourceId())) {
          edges.add(new SourcedEdge(edgeOf(group, target), group.sourceId(), target, group));
        }
      }
    }

    return new RuleGraph(features, edges);
  }

  private static Edge edgeOf(RelationshipGroup group, UUID target) {
    return switch (group.kind()) {
      case REQUIRES -> Edge.implies(group.sourceId(), target);
      case REQUIRED_WITH -> Edge.implies(target, group.sourceId());
      case NOT_ALLOWED_WITH -> Edge.excludes(group.sourceId(), target);
    };
  }

  List<SourcedEdge> sourcedEdges() {
    return edges;
  }

  /** Distinct logical edges in authored order. */
  List<Edge> logicalEdges() {
    return edges.stream().map(SourcedEdge::edge).distinct().toList();
  }

  public List<SourcedEdge> exclusions() {
    return edges.stream().filter(e -> e.edge().type() == Edge.Type.EXCLUDES).toList();
  }

  boolean requiresDirectly(UUID chosen, UUID other) {
    return required.getOrDefault(chosen, List.of()).contains(other);
  }

  /** The feature itself plus everything it requires directly or through a chain. */
  public Set<UUID> closure(UUID featureId) {
    return shortestPaths(featureId).keySet();
  }

  /**
   * The shortest requirement chain from one feature to another, including both ends, or an empty
   * list when choosing the first never requires the second.
   */
  List<UUID> requirementPath(UUID from, UUID to) {
    var path = shortestPaths(from).get(to);

    return path == null ? List.of() : path;
  }

  /** Breadth-first search that records the shortest chain to every reachable feature. */
  private Map<UUID, List<UUID>> shortestPaths(UUID start) {
    var paths = new LinkedHashMap<UUID, List<UUID>>();
    paths.put(start, List.of(start));

    var queue = new ArrayDeque<UUID>();
    queue.add(start);

    while (!queue.isEmpty()) {
      var current = queue.poll();

      for (var next : required.getOrDefault(current, List.of())) {
        if (!paths.containsKey(next)) {
          var path = new ArrayList<>(paths.get(current));
          path.add(next);
          paths.put(next, List.copyOf(path));
          queue.add(next);
        }
      }
    }

    return paths;
  }

  /**
   * Counts every subset of features, including the empty one, that satisfies all relationships.
   * Each subset is a bit mask over the features, so the count is exact.
   */
  long countValidSubsets() {
    if (features.size() > MAX_COUNTED_FEATURES) {
      throw new IllegalStateException(
          "Exact counting supports at most " + MAX_COUNTED_FEATURES + " features");
    }

    var bit = new HashMap<UUID, Integer>();
    for (int i = 0; i < features.size(); i++) {
      bit.put(features.get(i).id(), 1 << i);
    }

    var edgeMasks = new ArrayList<int[]>();
    for (var edge : logicalEdges()) {
      int kind = edge.type() == Edge.Type.IMPLIES ? 0 : 1;
      edgeMasks.add(new int[] {kind, bit.get(edge.from()), bit.get(edge.to())});
    }

    long valid = 0;
    for (int subset = 0; subset < 1 << features.size(); subset++) {
      if (satisfies(subset, edgeMasks)) {
        valid++;
      }
    }

    return valid;
  }

  private static boolean satisfies(int subset, List<int[]> edgeMasks) {
    for (var edge : edgeMasks) {
      boolean first = (subset & edge[1]) != 0;
      boolean second = (subset & edge[2]) != 0;

      boolean implicationBroken = edge[0] == 0 && first && !second;
      boolean exclusionBroken = edge[0] == 1 && first && second;
      if (implicationBroken || exclusionBroken) {
        return false;
      }
    }

    return true;
  }
}
