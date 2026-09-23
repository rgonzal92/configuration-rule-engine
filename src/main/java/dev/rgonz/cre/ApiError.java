package dev.rgonz.cre;

/** Public error body shared by API endpoints. */
public record ApiError(String code, String message) {}
