package dev.rgonz.cre.catalog;

import java.util.UUID;

/**
 * One logical relationship between two features. An implication points from the feature that is
 * chosen to the feature it requires; an exclusion is unordered, so its ends are stored in a fixed
 * order and two exclusions between the same features are equal.
 */
record Edge(Type type, UUID from, UUID to) {
  /** Whether the edge requires or forbids its second feature. */
  public enum Type {
    IMPLIES,
    EXCLUDES
  }

  public static Edge implies(UUID chosen, UUID required) {
    return new Edge(Type.IMPLIES, chosen, required);
  }

  public static Edge excludes(UUID first, UUID second) {
    return first.compareTo(second) <= 0
        ? new Edge(Type.EXCLUDES, first, second)
        : new Edge(Type.EXCLUDES, second, first);
  }
}
