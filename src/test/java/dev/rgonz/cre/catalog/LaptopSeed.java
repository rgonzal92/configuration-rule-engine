package dev.rgonz.cre.catalog;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** The 11-feature laptop catalog used by checker tests, with its single seeded rule. */
final class LaptopSeed {
  static final Feature GPU = feature("DEDICATED_GPU", "Dedicated GPU");
  static final Feature CHARGER = feature("HIGH_WATTAGE_CHARGER", "High-wattage charger");
  static final Feature FANLESS = feature("FANLESS_CHASSIS", "Fanless chassis");
  static final Feature TOUCHSCREEN = feature("TOUCHSCREEN", "Touchscreen");
  static final Feature STYLUS = feature("STYLUS_SUPPORT", "Stylus support");
  static final Feature HIRES = feature("HIGH_RESOLUTION_DISPLAY", "High-resolution display");
  static final Feature BACKLIT = feature("BACKLIT_KEYBOARD", "Backlit keyboard");
  static final Feature FINGERPRINT = feature("FINGERPRINT_READER", "Fingerprint reader");
  static final Feature LTE = feature("LTE_MODEM", "LTE modem");
  static final Feature BATTERY = feature("EXTRA_BATTERY", "Extra battery");
  static final Feature PRIVACY = feature("PRIVACY_SCREEN", "Privacy screen");

  static final List<Feature> FEATURES =
      List.of(
          GPU,
          CHARGER,
          FANLESS,
          TOUCHSCREEN,
          STYLUS,
          HIRES,
          BACKLIT,
          FINGERPRINT,
          LTE,
          BATTERY,
          PRIVACY);

  static final RelationshipGroup GPU_NEEDS_CHARGER =
      new RelationshipGroup(
          id("group:gpu-charger"), GPU.id(), RelationshipKind.REQUIRES, List.of(CHARGER.id()));

  private LaptopSeed() {}

  static CatalogSnapshot catalog(RelationshipGroup... groups) {
    return new CatalogSnapshot(id("catalog:laptop"), 1, FEATURES, List.of(groups));
  }

  static CatalogSnapshot seeded() {
    return catalog(GPU_NEEDS_CHARGER);
  }

  static RelationshipGroup group(
      String name, Feature source, RelationshipKind kind, Feature... targets) {
    var targetIds = Arrays.stream(targets).map(Feature::id).toList();

    return new RelationshipGroup(id("group:" + name), source.id(), kind, targetIds);
  }

  static List<UUID> ids(Feature... features) {
    return Arrays.stream(features).map(Feature::id).toList();
  }

  private static Feature feature(String code, String name) {
    return new Feature(id("feature:" + code), code, name);
  }

  private static UUID id(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }
}
