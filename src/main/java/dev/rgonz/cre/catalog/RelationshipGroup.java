package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.UUID;

/**
 * One source feature related to one or more targets by a single kind. Each target forms one logical
 * relationship with the source.
 *
 * @param id the stored group ID, or {@code null} for a group that exists only in a pending draft
 */
public record RelationshipGroup(
    UUID id, UUID sourceId, RelationshipKind kind, List<UUID> targetIds) {
  public RelationshipGroup {
    targetIds = List.copyOf(targetIds);
  }
}
