package dev.rgonz.cre.catalog;

import dev.rgonz.cre.workspace.WorkspaceKind;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for the catalog API. */
final class CatalogViews {
  private CatalogViews() {}

  /** One catalog in the visitor's list. */
  public record CatalogSummary(UUID id, String name, WorkspaceKind kind, boolean readOnly) {}

  /** A catalog's features and active relationship groups. */
  public record CatalogView(
      UUID id,
      String name,
      boolean readOnly,
      long revision,
      List<Feature> features,
      List<GroupView> groups) {}

  /** One active relationship group. */
  public record GroupView(
      UUID id, UUID sourceFeatureId, RelationshipKind kind, List<UUID> targetFeatureIds) {
    static GroupView of(RelationshipGroup group) {
      return new GroupView(group.id(), group.sourceId(), group.kind(), group.targetIds());
    }
  }

  /**
   * The pending batch.
   *
   * @param checked whether a valid check covers exactly this draft at the current revision
   */
  public record DraftView(
      long draftVersion, long baseRevision, boolean checked, List<OperationJson> operations) {}

  /**
   * Replaces the pending batch if neither it nor the catalog changed since the browser read them.
   */
  public record DraftRequest(
      Long expectedDraftVersion, Long expectedRevision, List<OperationJson> operations) {}

  /** The check result for one draft version at one catalog revision. */
  public record CheckView(long draftVersion, long revision, CheckResult result) {}

  /**
   * Applies the checked draft.
   *
   * @param commandId chosen by the browser and reused on retry
   */
  public record ApplyRequest(UUID commandId, Long checkedDraftVersion) {}

  /** What an apply changed. A retry with the same command returns this same result. */
  public record ApplyResult(
      long catalogRevision, long draftVersion, List<EdgeChange> added, List<EdgeChange> removed) {}

  /** A set of features to test against the active rules. */
  public record ConfigurationRequest(List<UUID> featureIds) {}
}
