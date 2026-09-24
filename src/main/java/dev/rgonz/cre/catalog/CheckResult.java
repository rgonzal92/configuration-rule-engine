package dev.rgonz.cre.catalog;

import java.util.List;

/**
 * The effect of a whole pending batch on a catalog.
 *
 * @param validBefore valid configurations under the active rules, including the empty one
 * @param validAfter valid configurations if the batch were applied
 */
record CheckResult(
    boolean valid,
    List<EdgeChange> added,
    List<EdgeChange> removed,
    List<IndirectRequirement> indirect,
    List<BlockingReason> blocking,
    long validBefore,
    long validAfter) {
  public CheckResult {
    added = List.copyOf(added);
    removed = List.copyOf(removed);
    indirect = List.copyOf(indirect);
    blocking = List.copyOf(blocking);
  }
}
