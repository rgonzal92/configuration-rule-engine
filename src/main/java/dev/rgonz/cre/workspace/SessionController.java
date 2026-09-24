package dev.rgonz.cre.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.rgonz.cre.core.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports the visitor's session state and starts guest workspaces. */
@RestController
@RequestMapping("/api")
class SessionController {
  private final GuestWorkspaceService guests;
  private final WorkspaceRepository workspaces;
  private final SecurityContextRepository contexts;
  private final Clock clock;

  public SessionController(
      GuestWorkspaceService guests,
      WorkspaceRepository workspaces,
      SecurityContextRepository contexts,
      Clock clock) {
    this.guests = guests;
    this.workspaces = workspaces;
    this.contexts = contexts;
    this.clock = clock;
  }

  /** Session state as seen by the browser. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SessionView(String status, UUID workspaceId, Instant expiresAt) {
    static SessionView of(Workspace workspace) {
      return new SessionView("GUEST", workspace.id(), workspace.expiresAt());
    }
  }

  /** Also issues the CSRF cookie so the browser can make its first write. */
  @GetMapping("/session")
  public SessionView session(
      HttpServletRequest request, Authentication authentication, CsrfToken csrf) {
    csrf.getToken();

    if (request.getAttribute(WorkspaceExpiryFilter.EXPIRED) != null) {
      return new SessionView("EXPIRED", null, null);
    }

    return liveWorkspace(authentication)
        .map(SessionView::of)
        .orElse(new SessionView("ANONYMOUS", null, null));
  }

  /** Returns the visitor's live workspace, or creates one in a fresh session. */
  @PostMapping("/demo/sessions")
  public ResponseEntity<SessionView> start(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    var existing = liveWorkspace(authentication);
    if (existing.isPresent()) {
      return ResponseEntity.ok(SessionView.of(existing.get()));
    }

    var workspace = guests.create();

    // Never reuse a session the visitor arrived with.
    var previous = request.getSession(false);
    if (previous != null) {
      previous.invalidate();
    }
    request.getSession(true);

    var principal = new GuestPrincipal(workspace.ownerId(), workspace.id());
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);

    return ResponseEntity.status(201).body(SessionView.of(workspace));
  }

  @ExceptionHandler(GuestLimitReachedException.class)
  ResponseEntity<ApiError> limitReached() {
    return ResponseEntity.status(429)
        .body(
            new ApiError(
                "GUEST_LIMIT_REACHED", "Guest workspaces are busy right now; try again later"));
  }

  private Optional<Workspace> liveWorkspace(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof GuestPrincipal guest)) {
      return Optional.empty();
    }

    var now = clock.instant();
    return workspaces
        .findById(guest.workspaceId())
        .filter(workspace -> WorkspaceAccess.canRead(guest.ownerId(), workspace, now));
  }
}
