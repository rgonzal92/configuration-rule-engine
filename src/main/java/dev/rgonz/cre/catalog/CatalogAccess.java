package dev.rgonz.cre.catalog;

import dev.rgonz.cre.core.ApiException;
import dev.rgonz.cre.workspace.GuestPrincipal;
import dev.rgonz.cre.workspace.Workspace;
import dev.rgonz.cre.workspace.WorkspaceAccess;
import dev.rgonz.cre.workspace.WorkspaceKind;
import dev.rgonz.cre.workspace.WorkspaceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Applies workspace access rules to catalog requests. The owner always comes from the server
 * session. A catalog the visitor may not read is reported as missing, so its existence is not
 * revealed.
 */
@Component
class CatalogAccess {
  /** A catalog the visitor may use, with its workspace. */
  public record Allowed(CatalogRow catalog, Workspace workspace) {}

  private final CatalogRepository catalogs;
  private final WorkspaceRepository workspaces;
  private final Clock clock;

  public CatalogAccess(CatalogRepository catalogs, WorkspaceRepository workspaces, Clock clock) {
    this.catalogs = catalogs;
    this.workspaces = workspaces;
    this.clock = clock;
  }

  /** The visitor's guest principal, or {@code null} for an anonymous visitor. */
  public static GuestPrincipal guest(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof GuestPrincipal guest) {
      return guest;
    }

    return null;
  }

  public Allowed readable(UUID catalogId, Authentication authentication) {
    var allowed = load(catalogId);
    var owner = ownerId(authentication);

    if (!WorkspaceAccess.canRead(owner, allowed.workspace(), clock.instant())) {
      throw ApiException.notFound();
    }

    return allowed;
  }

  public Allowed writable(UUID catalogId, Authentication authentication) {
    var allowed = readable(catalogId, authentication);
    var owner = ownerId(authentication);

    if (!WorkspaceAccess.canWrite(owner, allowed.workspace(), clock.instant())) {
      throw allowed.workspace().kind() == WorkspaceKind.SHOWCASE
          ? new ApiException(403, "READ_ONLY", "Showcase catalogs can't be changed")
          : ApiException.notFound();
    }

    return allowed;
  }

  private Allowed load(UUID catalogId) {
    var catalog = catalogs.find(catalogId).orElseThrow(ApiException::notFound);
    var workspace = workspaces.findById(catalog.workspaceId()).orElseThrow(ApiException::notFound);

    return new Allowed(catalog, workspace);
  }

  private static UUID ownerId(Authentication authentication) {
    var guest = guest(authentication);

    return guest == null ? null : guest.ownerId();
  }
}
