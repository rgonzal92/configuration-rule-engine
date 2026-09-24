package dev.rgonz.cre.suggestion;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Request and response bodies for the plain-English assistant. */
final class SuggestionViews {
  private SuggestionViews() {}

  /** Which assistant answers: the live model or the example parser. */
  enum Mode {
    OPENAI,
    EXAMPLE
  }

  /** What the assistant could do with the text. */
  enum Status {
    SUGGESTION,
    CLARIFICATION,
    UNAVAILABLE
  }

  /** The visitor's description of one relationship. */
  record SuggestionRequest(String text) {}

  /**
   * The assistant's answer. Nothing is staged; a suggested rule only fills the form for review.
   *
   * @param mode the assistant that produced this answer
   * @param rule the proposed relationship, present only for {@code SUGGESTION}
   * @param message a question or explanation to show as plain text, when there is one
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record SuggestionView(Status status, Mode mode, ProposedRule rule, String message) {}

  /** The assistant available before any request is made. */
  record ModeView(Mode mode) {}
}
