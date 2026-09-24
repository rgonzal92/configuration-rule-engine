package dev.rgonz.cre.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Public error body shared by API endpoints.
 *
 * @param details individual problems, such as each invalid change in a draft; omitted when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<String> details) {
  public ApiError(String code, String message) {
    this(code, message, List.of());
  }
}
