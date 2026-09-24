package dev.rgonz.cre.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Guards the settings that keep the rolling-hour guest count exact. */
class GuestPropertiesTest {
  private static final Duration HOUR = Duration.ofHours(1);

  @Test
  void acceptsTheShippedSettings() {
    assertDoesNotThrow(() -> new GuestProperties(Duration.ofHours(4), 50, Duration.ofMinutes(5)));
    assertDoesNotThrow(() -> new GuestProperties(HOUR, 1, Duration.ofSeconds(1)));
  }

  @Test
  void rejectsALifetimeShorterThanTheCountingWindow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GuestProperties(HOUR.minusSeconds(1), 50, Duration.ofMinutes(5)));
  }

  @Test
  void rejectsNonPositiveLimitsAndIntervals() {
    assertThrows(IllegalArgumentException.class, () -> new GuestProperties(HOUR, 0, HOUR));
    assertThrows(IllegalArgumentException.class, () -> new GuestProperties(HOUR, 1, Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> new GuestProperties(null, 1, HOUR));
  }
}
