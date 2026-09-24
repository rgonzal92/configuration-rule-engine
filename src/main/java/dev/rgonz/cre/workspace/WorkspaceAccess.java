package dev.rgonz.cre.workspace;

import java.time.Instant;
import java.util.UUID;

/** Decides whether a visitor may read or change a workspace. */
public final class WorkspaceAccess {
  private WorkspaceAccess() {}

  /**
   * Anyone may read the showcase; only the owner may read a live guest workspace.
   *
   * @param ownerId the visitor's owner ID from the server session, or {@code null} if anonymous
   */
  public static boolean canRead(UUID ownerId, Workspace workspace, Instant now) {
    return switch (workspace.kind()) {
      case SHOWCASE -> true;
      case GUEST -> ownsLive(ownerId, workspace, now);
    };
  }

  /** The showcase is never writable; only the owner may change a live guest workspace. */
  public static boolean canWrite(UUID ownerId, Workspace workspace, Instant now) {
    return switch (workspace.kind()) {
      case SHOWCASE -> false;
      case GUEST -> ownsLive(ownerId, workspace, now);
    };
  }

  private static boolean ownsLive(UUID ownerId, Workspace workspace, Instant now) {
    return workspace.ownerId().equals(ownerId) && workspace.isLiveAt(now);
  }
}
