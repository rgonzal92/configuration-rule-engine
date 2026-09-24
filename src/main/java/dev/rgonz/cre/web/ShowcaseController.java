package dev.rgonz.cre.web;

import dev.rgonz.cre.domain.WorkspaceAccess;
import dev.rgonz.cre.persistence.WorkspaceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the shared public examples to anyone, without a session. */
@RestController
public class ShowcaseController {
  private final WorkspaceRepository workspaces;
  private final Clock clock;

  public ShowcaseController(WorkspaceRepository workspaces, Clock clock) {
    this.workspaces = workspaces;
    this.clock = clock;
  }

  /** The public examples and whether visitors may change them. */
  public record ShowcaseView(UUID workspaceId, boolean readOnly) {}

  @GetMapping("/api/showcase")
  public ShowcaseView showcase() {
    var showcase = workspaces.findShowcase();

    return new ShowcaseView(
        showcase.id(), !WorkspaceAccess.canWrite(null, showcase, clock.instant()));
  }
}
