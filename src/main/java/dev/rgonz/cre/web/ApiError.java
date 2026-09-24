package dev.rgonz.cre.web;

/** Public error body shared by API endpoints. */
public record ApiError(String code, String message) {}
