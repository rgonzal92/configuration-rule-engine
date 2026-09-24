package dev.rgonz.cre.core;

/** Public error body shared by API endpoints. */
public record ApiError(String code, String message) {}
