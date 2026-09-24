package dev.rgonz.cre.suggestion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Guards the limits on live assistant requests and visitor text. */
class SuggestionPropertiesTest {
  @Test
  void acceptsTheShippedSettings() {
    assertDoesNotThrow(() -> new SuggestionProperties(10, 100, 500));
    assertDoesNotThrow(() -> new SuggestionProperties(1, 1, 1));
  }

  @Test
  void rejectsNonPositiveLimits() {
    assertThrows(IllegalArgumentException.class, () -> new SuggestionProperties(0, 100, 500));
    assertThrows(IllegalArgumentException.class, () -> new SuggestionProperties(10, 0, 500));
    assertThrows(IllegalArgumentException.class, () -> new SuggestionProperties(10, 100, 0));
  }
}
