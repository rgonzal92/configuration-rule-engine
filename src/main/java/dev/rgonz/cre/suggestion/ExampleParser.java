package dev.rgonz.cre.suggestion;

import dev.rgonz.cre.catalog.Feature;
import dev.rgonz.cre.catalog.RelationshipKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads one relationship from a few fixed phrases that use exact feature names. It stands in for
 * the AI assistant when no API key is configured and backs it up when a live request fails.
 */
final class ExampleParser {
  /** Tells the visitor what the parser understands. */
  static final String HELP =
      "Describe one rule as \"A requires B\", \"A is required with B\", or \"A can't be chosen"
          + " with B\", using feature names from this catalog.";

  private static final Map<String, RelationshipKind> PHRASES = new LinkedHashMap<>();

  static {
    PHRASES.put("requires", RelationshipKind.REQUIRES);
    PHRASES.put("is required with", RelationshipKind.REQUIRED_WITH);
    PHRASES.put("can't be chosen with", RelationshipKind.NOT_ALLOWED_WITH);
    PHRASES.put("cannot be chosen with", RelationshipKind.NOT_ALLOWED_WITH);
    PHRASES.put("is not allowed with", RelationshipKind.NOT_ALLOWED_WITH);
  }

  private ExampleParser() {}

  /**
   * Every way the text reads as "source phrase target" for two different features. One entry is a
   * match; none means the wording is unsupported, and several mean it is ambiguous.
   */
  static List<ProposedRule> matches(String text, List<Feature> features) {
    var wanted = normalize(text);
    var readings = new ArrayList<ProposedRule>();

    for (var source : features) {
      for (var target : features) {
        if (source.equals(target)) {
          continue;
        }

        for (var phrase : PHRASES.entrySet()) {
          var sentence =
              normalize(source.name()) + " " + phrase.getKey() + " " + normalize(target.name());

          if (sentence.equals(wanted)) {
            readings.add(new ProposedRule(source.id(), phrase.getValue(), List.of(target.id())));
          }
        }
      }
    }

    return readings;
  }

  /** Lower case with single spaces, a plain apostrophe, and no final period. */
  private static String normalize(String text) {
    var normalized =
        text.replace('’', '\'').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);

    return normalized.endsWith(".")
        ? normalized.substring(0, normalized.length() - 1).trim()
        : normalized;
  }
}
