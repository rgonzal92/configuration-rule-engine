package dev.rgonz.cre.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A catalog's features and active relationship groups at one revision. */
public record CatalogSnapshot(
    UUID id, long revision, List<Feature> features, List<RelationshipGroup> groups) {
  public CatalogSnapshot {
    features = List.copyOf(features);
    groups = List.copyOf(groups);
  }

  public boolean hasFeature(UUID featureId) {
    return features.stream().anyMatch(feature -> feature.id().equals(featureId));
  }

  public Optional<RelationshipGroup> group(UUID groupId) {
    return groups.stream().filter(group -> group.id().equals(groupId)).findFirst();
  }

  /** Feature names by ID, in catalog order. */
  public Map<UUID, String> names() {
    var names = new LinkedHashMap<UUID, String>();
    features.forEach(feature -> names.put(feature.id(), feature.name()));

    return names;
  }
}
