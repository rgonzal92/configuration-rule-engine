package dev.rgonz.cre.core;

import dev.rgonz.cre.workspace.WorkspaceExpiryFilter;
import dev.rgonz.cre.workspace.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guest sessions, CSRF protection for browser writes, and JSON security errors. Reads are open;
 * each read endpoint applies workspace access rules itself. Other API writes need a live guest.
 */
@Configuration
class SecurityConfig {
  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository contexts,
      WorkspaceRepository workspaces,
      Clock clock,
      JsonMapper json) {
    // Angular reads this cookie and echoes it in the X-XSRF-TOKEN header on writes.
    var csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));

    http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens));

    http.securityContext(context -> context.securityContextRepository(contexts));
    http.addFilterAfter(
        new WorkspaceExpiryFilter(workspaces, clock), SecurityContextHolderFilter.class);

    http.authorizeHttpRequests(
        requests ->
            requests
                .requestMatchers(HttpMethod.POST, "/api/demo/sessions")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/**")
                .permitAll()
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .permitAll());

    http.exceptionHandling(
        errors ->
            errors
                .authenticationEntryPoint(
                    (request, response, exception) ->
                        write(json, response, 401, unauthenticated(request)))
                .accessDeniedHandler(
                    (request, response, exception) ->
                        write(json, response, 403, forbidden(exception))));

    http.httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable);

    return http.build();
  }

  /** Tells an expired guest why a guest-only request was refused. */
  private static ApiError unauthenticated(HttpServletRequest request) {
    if (request.getAttribute(WorkspaceExpiryFilter.EXPIRED) != null) {
      return new ApiError("SESSION_EXPIRED", "Your guest workspace expired");
    }

    return new ApiError("UNAUTHENTICATED", "Start a guest workspace first");
  }

  private static ApiError forbidden(AccessDeniedException exception) {
    if (exception instanceof CsrfException) {
      return new ApiError("CSRF_INVALID", "Refresh the page and retry");
    }

    return new ApiError("FORBIDDEN", "Access denied");
  }

  private static void write(
      JsonMapper json, HttpServletResponse response, int status, ApiError body) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    json.writeValue(response.getOutputStream(), body);
  }
}
