package dev.rgonz.cre.suggestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rgonz.cre.catalog.Feature;
import dev.rgonz.cre.catalog.RelationshipKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves the example parser accepts only its documented single-target phrases. */
class ExampleParserTest {
  private static final Feature TOUCHSCREEN = feature("Touchscreen");
  private static final Feature STYLUS = feature("Stylus support");
  private static final Feature GPU = feature("Dedicated GPU");
  private static final List<Feature> FEATURES = List.of(TOUCHSCREEN, STYLUS, GPU);

  private static Feature feature(String name) {
    return new Feature(UUID.randomUUID(), name.toUpperCase(), name);
  }

  private static ProposedRule rule(Feature source, RelationshipKind kind, Feature target) {
    return new ProposedRule(source.id(), kind, List.of(target.id()));
  }

  @Test
  void readsEachDocumentedPhrase() {
    assertEquals(
        List.of(rule(TOUCHSCREEN, RelationshipKind.REQUIRES, STYLUS)),
        ExampleParser.matches("Touchscreen requires Stylus support", FEATURES));
    assertEquals(
        List.of(rule(STYLUS, RelationshipKind.REQUIRED_WITH, TOUCHSCREEN)),
        ExampleParser.matches("Stylus support is required with Touchscreen", FEATURES));

    for (var phrase :
        List.of("can't be chosen with", "cannot be chosen with", "is not allowed with")) {
      assertEquals(
          List.of(rule(GPU, RelationshipKind.NOT_ALLOWED_WITH, STYLUS)),
          ExampleParser.matches("Dedicated GPU " + phrase + " Stylus support", FEATURES),
          phrase);
    }
  }

  @Test
  void ignoresCaseSpacingAndAFinalPeriod() {
    assertEquals(
        List.of(rule(TOUCHSCREEN, RelationshipKind.REQUIRES, STYLUS)),
        ExampleParser.matches("  touchscreen   REQUIRES stylus support. ", FEATURES));
    assertEquals(
        List.of(rule(GPU, RelationshipKind.NOT_ALLOWED_WITH, STYLUS)),
        ExampleParser.matches("Dedicated GPU can’t be chosen with Stylus support", FEATURES));
  }

  @Test
  void matchesNothingForOtherWordingOrUnknownNames() {
    assertTrue(ExampleParser.matches("Touchscreen needs Stylus support", FEATURES).isEmpty());
    assertTrue(ExampleParser.matches("Touchscreen requires a keyboard", FEATURES).isEmpty());
    assertTrue(ExampleParser.matches("Touchscreen requires Touchscreen", FEATURES).isEmpty());
    assertTrue(
        ExampleParser.matches("Touchscreen requires Stylus support and Dedicated GPU", FEATURES)
            .isEmpty());
  }

  @Test
  void reportsEveryReadingWhenTheWordingIsAmbiguous() {
    var features =
        List.of(
            feature("Alpha"),
            feature("Alpha requires Beta"),
            feature("Beta"),
            feature("Beta requires Gamma"),
            feature("Gamma"));

    assertEquals(2, ExampleParser.matches("Alpha requires Beta requires Gamma", features).size());
  }
}
