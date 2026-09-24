package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.UUID;

/** Whether a selected set of features satisfies the active rules, and why not. */
record ConfigurationResult(
    boolean valid, List<MissingRequirement> missing, List<ForbiddenPair> conflicts) {
  /** A chosen feature whose required feature was not chosen. */
  public record MissingRequirement(UUID featureId, UUID requiredFeatureId, String message) {}

  /** Two chosen features that can't be chosen together. */
  public record ForbiddenPair(UUID firstId, UUID secondId, String message) {}

  public ConfigurationResult {
    missing = List.copyOf(missing);
    conflicts = List.copyOf(conflicts);
  }
}
