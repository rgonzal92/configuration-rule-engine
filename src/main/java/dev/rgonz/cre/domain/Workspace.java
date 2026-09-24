package dev.rgonz.cre.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A container for catalog data. A guest workspace has one owner and a fixed expiry; the showcase
 * has neither.
 */
public record Workspace(
    UUID id, WorkspaceKind kind, UUID ownerId, Instant createdAt, Instant expiresAt) {
  public Workspace {
    Objects.requireNonNull(id);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(createdAt);

    boolean guest = kind == WorkspaceKind.GUEST;
    if (guest != (ownerId != null) || guest != (expiresAt != null)) {
      throw new IllegalArgumentException("Only guest workspaces have an owner and expiry");
    }
  }

  public static Workspace guest(UUID id, UUID ownerId, Instant createdAt, Instant expiresAt) {
    return new Workspace(id, WorkspaceKind.GUEST, ownerId, createdAt, expiresAt);
  }

  public static Workspace showcase(UUID id, Instant createdAt) {
    return new Workspace(id, WorkspaceKind.SHOWCASE, null, createdAt, null);
  }

  /** A guest workspace stops being usable at the exact instant it expires. */
  public boolean isLiveAt(Instant now) {
    return kind == WorkspaceKind.SHOWCASE || now.isBefore(expiresAt);
  }
}
