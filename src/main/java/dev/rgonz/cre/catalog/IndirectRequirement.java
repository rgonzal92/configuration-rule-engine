package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.UUID;

/**
 * A requirement a batch creates only through a chain of other requirements.
 *
 * @param path every feature on the chain, from the chosen feature to the required one
 */
record IndirectRequirement(
    UUID featureId, UUID requiredFeatureId, List<UUID> path, String description) {
  public IndirectRequirement {
    path = List.copyOf(path);
  }
}
