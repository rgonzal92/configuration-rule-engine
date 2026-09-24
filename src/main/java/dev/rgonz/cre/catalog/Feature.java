package dev.rgonz.cre.catalog;

import java.util.UUID;

/** An optional product feature that a configuration may include. */
public record Feature(UUID id, String code, String name) {}
