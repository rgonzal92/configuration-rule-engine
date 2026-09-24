package dev.rgonz.cre.suggestion;

import dev.rgonz.cre.suggestion.SuggestionViews.ModeView;
import dev.rgonz.cre.suggestion.SuggestionViews.SuggestionRequest;
import dev.rgonz.cre.suggestion.SuggestionViews.SuggestionView;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Proposes a relationship from plain English for the owner of a guest catalog to review. */
@RestController
class SuggestionController {
  private final SuggestionService suggestions;

  SuggestionController(SuggestionService suggestions) {
    this.suggestions = suggestions;
  }

  @PostMapping("/api/catalogs/{id}/suggestions")
  SuggestionView suggest(
      @PathVariable UUID id,
      @RequestBody SuggestionRequest request,
      Authentication authentication) {
    return suggestions.suggest(id, request.text(), authentication);
  }

  /** Lets the page label the assistant before the visitor asks anything. */
  @GetMapping("/api/suggestions/mode")
  ModeView mode() {
    return new ModeView(suggestions.mode());
  }
}
