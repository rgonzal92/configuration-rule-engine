package dev.rgonz.cre.workspace;

import dev.rgonz.cre.catalog.CatalogRepository;
import dev.rgonz.cre.catalog.DraftRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates guest workspaces, each with its own laptop catalog, within the shared limit. */
@Service
class GuestWorkspaceService {
  private final WorkspaceRepository workspaces;
  private final CatalogRepository catalogs;
  private final DraftRepository drafts;
  private final GuestProperties properties;
  private final Clock clock;

  public GuestWorkspaceService(
      WorkspaceRepository workspaces,
      CatalogRepository catalogs,
      DraftRepository drafts,
      GuestProperties properties,
      Clock clock) {
    this.workspaces = workspaces;
    this.catalogs = catalogs;
    this.drafts = drafts;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Admits and creates one guest workspace. The advisory lock makes the count and insert atomic, so
   * concurrent requests cannot exceed the limit. Purging never removes a row younger than its
   * lifetime of at least one hour, so the rolling-hour count stays exact.
   */
  @Transactional
  public Workspace create() {
    // PostgreSQL stores microseconds; match it so every response shows the same expiry.
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);

    workspaces.lockGuestCreation();
    if (workspaces.countGuestsCreatedAfter(now.minus(Duration.ofHours(1)))
        >= properties.hourlyLimit()) {
      throw new GuestLimitReachedException();
    }

    var workspace =
        Workspace.guest(UUID.randomUUID(), UUID.randomUUID(), now, now.plus(properties.lifetime()));
    workspaces.insert(workspace);

    // Each guest edits a private copy of the laptop seed, starting with an empty draft.
    var catalogId = catalogs.cloneLaptopSeed(workspace.id());
    drafts.create(catalogId, CatalogRepository.FIRST_REVISION);

    return workspace;
  }
}
