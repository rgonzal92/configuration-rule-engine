package dev.rgonz.cre.workspace;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** The only identity stored in a guest's server session. */
public record GuestPrincipal(UUID ownerId, UUID workspaceId) implements Principal, Serializable {
  @Serial private static final long serialVersionUID = 1L;

  /** Spring Session indexes sessions by this name, so it must fit its 100-character column. */
  @Override
  public String getName() {
    return ownerId.toString();
  }
}
