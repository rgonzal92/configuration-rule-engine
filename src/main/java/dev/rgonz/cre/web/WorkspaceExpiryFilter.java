package dev.rgonz.cre.web;

import dev.rgonz.cre.domain.WorkspaceAccess;
import dev.rgonz.cre.persistence.WorkspaceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a guest session once its workspace has expired or been purged. The workspace expiry wins
 * over the longer-lived stored session. Only API requests are checked, so page and asset loads
 * leave the expiry for the API to report to the browser.
 */
public class WorkspaceExpiryFilter extends OncePerRequestFilter {
  /** Request attribute set when this request ended an expired guest session. */
  public static final String EXPIRED = WorkspaceExpiryFilter.class.getName() + ".EXPIRED";

  private final WorkspaceRepository workspaces;
  private final Clock clock;

  public WorkspaceExpiryFilter(WorkspaceRepository workspaces, Clock clock) {
    this.workspaces = workspaces;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.getPrincipal() instanceof GuestPrincipal guest) {
      var now = clock.instant();
      boolean live =
          workspaces
              .findById(guest.workspaceId())
              .filter(workspace -> WorkspaceAccess.canRead(guest.ownerId(), workspace, now))
              .isPresent();

      if (!live) {
        var session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }

        SecurityContextHolder.clearContext();
        request.setAttribute(EXPIRED, true);
      }
    }

    chain.doFilter(request, response);
  }
}
