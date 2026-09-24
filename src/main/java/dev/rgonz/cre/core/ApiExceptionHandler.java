package dev.rgonz.cre.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns request failures into the shared JSON error body. */
@RestControllerAdvice
class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException exception) {
    return ResponseEntity.status(exception.status()).body(exception.error());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> unreadable() {
    return ResponseEntity.badRequest()
        .body(new ApiError("INVALID_REQUEST", "The request body could not be read"));
  }

  /** A path ID that is not a UUID cannot name any resource. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> mismatch() {
    return ResponseEntity.status(404).body(ApiException.notFound().error());
  }

  /**
   * The transaction has already rolled back when this runs, so nothing was changed. The cause is
   * logged because handled exceptions are otherwise invisible at the default log level.
   */
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> database(DataAccessException exception) {
    LOG.error("Database request failed", exception);

    return ResponseEntity.internalServerError()
        .body(new ApiError("INTERNAL_ERROR", "Something went wrong; nothing was changed"));
  }
}
