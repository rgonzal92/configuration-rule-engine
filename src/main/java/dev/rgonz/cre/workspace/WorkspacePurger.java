package dev.rgonz.cre.workspace;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically deletes expired guest workspaces. */
@Component
public class WorkspacePurger {
  private final WorkspaceRepository workspaces;
  private final Clock clock;

  public WorkspacePurger(WorkspaceRepository workspaces, Clock clock) {
    this.workspaces = workspaces;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${cre.guest.purge-interval}",
      initialDelayString = "${cre.guest.purge-interval}")
  public void purgeExpired() {
    workspaces.deleteGuestsExpiredAt(clock.instant());
  }
}
