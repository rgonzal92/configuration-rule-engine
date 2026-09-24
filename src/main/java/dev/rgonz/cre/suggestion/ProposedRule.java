package dev.rgonz.cre.suggestion;

import dev.rgonz.cre.catalog.RelationshipKind;
import java.util.List;
import java.util.UUID;

/** A relationship offered to the visitor for review. It is never staged automatically. */
record ProposedRule(UUID sourceFeatureId, RelationshipKind kind, List<UUID> targetFeatureIds) {
  ProposedRule {
    targetFeatureIds = List.copyOf(targetFeatureIds);
  }
}
