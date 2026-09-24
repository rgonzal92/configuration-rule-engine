package dev.rgonz.cre.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gives unknown API requests a stable JSON error response. */
@RestController
public class ApiFallback {
  @RequestMapping("/api/**")
  public ResponseEntity<ApiError> notFound() {
    return ResponseEntity.status(404)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ApiError("NOT_FOUND", "Resource not found"));
  }
}
