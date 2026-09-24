package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.CatalogViews.CatalogSummary;
import dev.rgonz.cre.catalog.CatalogViews.CatalogView;
import dev.rgonz.cre.catalog.CatalogViews.GroupView;
import dev.rgonz.cre.core.ApiException;
import dev.rgonz.cre.workspace.WorkspaceKind;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Lists and reads catalogs and tests configurations against their active rules. */
@Service
class CatalogService {
  private final CatalogRepository catalogs;
  private final CatalogAccess access;

  public CatalogService(CatalogRepository catalogs, CatalogAccess access) {
    this.catalogs = catalogs;
    this.access = access;
  }

  /** The guest's own catalog first, then the showcase catalogs. */
  @Transactional(readOnly = true)
  public List<CatalogSummary> list(Authentication authentication) {
    var guest = CatalogAccess.guest(authentication);
    var workspaceId = guest == null ? null : guest.workspaceId();

    return catalogs.findVisible(workspaceId).stream()
        .map(
            row ->
                new CatalogSummary(
                    row.id(),
                    row.name(),
                    row.workspaceKind(),
                    row.workspaceKind() == WorkspaceKind.SHOWCASE))
        .toList();
  }

  /** Repeatable read keeps the revision, features, and groups from the same moment. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CatalogView read(UUID catalogId, Authentication authentication) {
    var allowed = access.readable(catalogId, authentication);
    var snapshot = catalogs.snapshot(catalogId).orElseThrow(ApiException::notFound);

    return new CatalogView(
        catalogId,
        allowed.catalog().name(),
        allowed.workspace().kind() == WorkspaceKind.SHOWCASE,
        snapshot.revision(),
        snapshot.features(),
        snapshot.groups().stream().map(GroupView::of).toList());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ConfigurationResult checkConfiguration(
      UUID catalogId, List<UUID> featureIds, Authentication authentication) {
    access.readable(catalogId, authentication);
    var snapshot = catalogs.snapshot(catalogId).orElseThrow(ApiException::notFound);

    if (featureIds == null || featureIds.contains(null)) {
      throw new ApiException(400, "INVALID_SELECTION", "Choose features from this catalog");
    }

    try {
      return ConfigurationChecker.check(snapshot, featureIds);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(400, "INVALID_SELECTION", exception.getMessage());
    }
  }
}
