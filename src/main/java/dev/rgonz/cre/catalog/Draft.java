package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.UUID;

/**
 * A catalog's pending batch.
 *
 * @param baseRevision the catalog revision the operations were written against
 * @param checkedDraftVersion the draft version of the last valid check, or {@code null}
 * @param checkedRevision the catalog revision of the last valid check, or {@code null}
 */
record Draft(
    UUID catalogId,
    List<Operation> operations,
    long draftVersion,
    long baseRevision,
    Long checkedDraftVersion,
    Long checkedRevision) {
  public Draft {
    operations = List.copyOf(operations);
  }

  /** Whether the last valid check still covers exactly this draft at this catalog revision. */
  public boolean isCheckedAt(long revision) {
    return Long.valueOf(draftVersion).equals(checkedDraftVersion)
        && Long.valueOf(revision).equals(checkedRevision);
  }
}
