package dev.rgonz.cre.workspace;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates guest workspaces within the shared creation limit. */
@Service
class GuestWorkspaceService {
  private final WorkspaceRepository workspaces;
  private final GuestProperties properties;
  private final Clock clock;

  public GuestWorkspaceService(
      WorkspaceRepository workspaces, GuestProperties properties, Clock clock) {
    this.workspaces = workspaces;
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

    return workspace;
  }
}
