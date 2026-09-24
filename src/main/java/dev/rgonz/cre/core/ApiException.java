package dev.rgonz.cre.core;

import java.util.List;

/** A request failure with the HTTP status and public error body to return. */
public class ApiException extends RuntimeException {
  private final int status;
  private final ApiError error;

  public ApiException(int status, String code, String message) {
    this(status, new ApiError(code, message));
  }

  public ApiException(int status, ApiError error) {
    super(error.message());
    this.status = status;
    this.error = error;
  }

  public static ApiException notFound() {
    return new ApiException(404, "NOT_FOUND", "Resource not found");
  }

  public static ApiException invalidDraft(List<String> details) {
    return new ApiException(
        400, new ApiError("INVALID_DRAFT", "Some pending changes are not valid", details));
  }

  public int status() {
    return status;
  }

  public ApiError error() {
    return error;
  }
}
