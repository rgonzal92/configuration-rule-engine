package dev.rgonz.cre.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Why a batch cannot be applied.
 *
 * @param featureIds the features involved, starting with the one the reason is about
 */
record BlockingReason(Code code, List<UUID> featureIds, String message) {
  /** The kinds of problems that block a batch. */
  public enum Code {
    SELF_RELATION,
    DUPLICATE_RELATIONSHIP,
    FEATURE_UNAVAILABLE
  }

  public BlockingReason {
    featureIds = List.copyOf(featureIds);
  }
}
