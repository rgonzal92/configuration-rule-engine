package dev.rgonz.cre.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves who may read or change guest and showcase workspaces. */
class WorkspaceAccessTest {
  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant EXPIRES = CREATED.plus(Duration.ofHours(4));
  private final UUID owner = UUID.randomUUID();
  private final Workspace guest = Workspace.guest(UUID.randomUUID(), owner, CREATED, EXPIRES);
  private final Workspace showcase = Workspace.showcase(UUID.randomUUID(), CREATED);

  @Test
  void showcaseIsReadableByAnyoneAndWritableByNobody() {
    assertTrue(WorkspaceAccess.canRead(null, showcase, CREATED));
    assertTrue(WorkspaceAccess.canRead(owner, showcase, CREATED));
    assertFalse(WorkspaceAccess.canWrite(null, showcase, CREATED));
    assertFalse(WorkspaceAccess.canWrite(owner, showcase, CREATED));
  }

  @Test
  void onlyTheOwnerMayUseALiveGuestWorkspace() {
    assertTrue(WorkspaceAccess.canRead(owner, guest, CREATED));
    assertTrue(WorkspaceAccess.canWrite(owner, guest, CREATED));
    assertFalse(WorkspaceAccess.canRead(UUID.randomUUID(), guest, CREATED));
    assertFalse(WorkspaceAccess.canWrite(UUID.randomUUID(), guest, CREATED));
    assertFalse(WorkspaceAccess.canRead(null, guest, CREATED));
    assertFalse(WorkspaceAccess.canWrite(null, guest, CREATED));
  }

  @Test
  void guestWorkspaceEndsExactlyAtExpiry() {
    var oneSecondBefore = EXPIRES.minusSeconds(1);
    assertTrue(guest.isLiveAt(oneSecondBefore));
    assertTrue(WorkspaceAccess.canWrite(owner, guest, oneSecondBefore));
    assertFalse(guest.isLiveAt(EXPIRES));
    assertFalse(WorkspaceAccess.canRead(owner, guest, EXPIRES));
    assertFalse(WorkspaceAccess.canWrite(owner, guest, EXPIRES));
    assertTrue(showcase.isLiveAt(EXPIRES.plus(Duration.ofDays(365))));
  }
}
