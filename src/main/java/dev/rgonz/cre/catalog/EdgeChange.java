package dev.rgonz.cre.catalog;

/** A logical relationship a batch adds or removes, with its plain-language meaning. */
record EdgeChange(Edge edge, String description) {}
