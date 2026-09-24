package dev.rgonz.cre.workspace;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for guest workspaces.
 *
 * @param lifetime fixed time from creation until a guest workspace expires; at least one hour, so
 *     purging never removes a workspace still counted by the rolling-hour limit
 * @param hourlyLimit new guest workspaces allowed across all visitors in any rolling hour
 * @param purgeInterval delay between deletions of expired guest workspaces
 */
@ConfigurationProperties("cre.guest")
record GuestProperties(Duration lifetime, int hourlyLimit, Duration purgeInterval) {
  public GuestProperties {
    if (lifetime == null || lifetime.compareTo(Duration.ofHours(1)) < 0) {
      throw new IllegalArgumentException("cre.guest.lifetime must be at least one hour");
    }

    if (hourlyLimit < 1) {
      throw new IllegalArgumentException("cre.guest.hourly-limit must be positive");
    }

    if (purgeInterval == null || !purgeInterval.isPositive()) {
      throw new IllegalArgumentException("cre.guest.purge-interval must be positive");
    }
  }
}
