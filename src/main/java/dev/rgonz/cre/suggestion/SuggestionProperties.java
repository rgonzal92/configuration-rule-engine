package dev.rgonz.cre.suggestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for the plain-English assistant.
 *
 * @param workspaceLimit live AI requests allowed for one guest workspace
 * @param dailyLimit live AI requests allowed across all visitors in one UTC day
 * @param maxTextLength longest visitor text accepted, in characters
 */
@ConfigurationProperties("cre.suggestions")
record SuggestionProperties(int workspaceLimit, int dailyLimit, int maxTextLength) {
  SuggestionProperties {
    if (workspaceLimit < 1 || dailyLimit < 1) {
      throw new IllegalArgumentException("cre.suggestions limits must be positive");
    }

    if (maxTextLength < 1) {
      throw new IllegalArgumentException("cre.suggestions.max-text-length must be positive");
    }
  }
}
