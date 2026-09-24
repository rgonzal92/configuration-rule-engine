package dev.rgonz.cre.suggestion;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admits live assistant requests within the per-workspace and daily limits. An attempt is recorded
 * and committed before the model is called, so a request that times out still counts, and a refused
 * request never reaches the model.
 */
@Service
class SuggestionQuota {
  /** Attempts are kept long enough to cover any UTC day still being counted. */
  private static final Duration RETENTION = Duration.ofDays(2);

  /** The outcome of asking for one live request. */
  enum Reservation {
    RESERVED,
    WORKSPACE_LIMIT,
    DAILY_LIMIT
  }

  private final SuggestionAttemptRepository attempts;
  private final SuggestionProperties properties;
  private final Clock clock;

  SuggestionQuota(
      SuggestionAttemptRepository attempts, SuggestionProperties properties, Clock clock) {
    this.attempts = attempts;
    this.properties = properties;
    this.clock = clock;
  }

  /** The advisory lock makes the counts and the insert atomic across concurrent requests. */
  @Transactional
  public Reservation reserve(UUID workspaceId) {
    var now = clock.instant();
    var startOfDay = now.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC);

    attempts.lockAttempts();

    if (attempts.countForWorkspace(workspaceId) >= properties.workspaceLimit()) {
      return Reservation.WORKSPACE_LIMIT;
    }

    if (attempts.countSince(startOfDay.toInstant()) >= properties.dailyLimit()) {
      return Reservation.DAILY_LIMIT;
    }

    attempts.insert(workspaceId, now);

    return Reservation.RESERVED;
  }

  @Scheduled(
      fixedDelayString = "${cre.guest.purge-interval}",
      initialDelayString = "${cre.guest.purge-interval}")
  public void forgetOldAttempts() {
    attempts.deleteBefore(clock.instant().minus(RETENTION));
  }
}
