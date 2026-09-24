package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.UUID;

/** One staged change to a catalog's relationship groups. */
sealed interface Operation {
  /** Adds a new group. */
  record Create(UUID sourceId, RelationshipKind kind, List<UUID> targetIds) implements Operation {}

  /** Replaces the targets of an active group. */
  record Update(UUID groupId, List<UUID> targetIds) implements Operation {}

  /** Removes an active group. */
  record Delete(UUID groupId) implements Operation {}
}
