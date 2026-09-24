package dev.rgonz.cre.suggestion;

import dev.rgonz.cre.catalog.CatalogAccess;
import dev.rgonz.cre.catalog.CatalogRepository;
import dev.rgonz.cre.catalog.CatalogRules;
import dev.rgonz.cre.catalog.CatalogSnapshot;
import dev.rgonz.cre.catalog.RelationshipKind;
import dev.rgonz.cre.core.ApiException;
import dev.rgonz.cre.suggestion.SuggestionViews.Mode;
import dev.rgonz.cre.suggestion.SuggestionViews.Status;
import dev.rgonz.cre.suggestion.SuggestionViews.SuggestionView;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a guest's plain-English description into a proposed relationship. With an API key it asks
 * the live model once per admitted request; otherwise, or when that request fails or is refused by
 * a limit, it uses the example parser when the text matches it unambiguously. It never stages or
 * saves anything, and no database transaction is open while the model is called.
 */
@Service
class SuggestionService {
  private static final Logger LOG = LoggerFactory.getLogger(SuggestionService.class);

  private static final String UNAVAILABLE_MESSAGE =
      "The assistant is unavailable right now. You can still add the relationship with the form.";
  private static final String DAILY_LIMIT_MESSAGE =
      "The assistant has reached today's request limit. You can still add relationships with the"
          + " form.";
  private static final String AMBIGUOUS_PREFIX = "That wording can be read more than one way. ";
  private static final String PARSER_STEPPED_IN =
      "The assistant couldn't answer, so the example parser read your text.";

  private final CatalogAccess access;
  private final CatalogRepository catalogs;
  private final SuggestionQuota quota;
  private final SuggestionProperties properties;
  private final OpenAiSuggester openAi;

  SuggestionService(
      CatalogAccess access,
      CatalogRepository catalogs,
      SuggestionQuota quota,
      SuggestionProperties properties,
      ObjectProvider<ChatModel> chatModels,
      JsonMapper json,
      @Value("${spring.ai.openai.chat.timeout}") Duration timeout) {
    this.access = access;
    this.catalogs = catalogs;
    this.quota = quota;
    this.properties = properties;

    var chatModel = chatModels.getIfAvailable();
    this.openAi = chatModel == null ? null : new OpenAiSuggester(chatModel, json, timeout);
  }

  Mode mode() {
    return openAi == null ? Mode.EXAMPLE : Mode.OPENAI;
  }

  SuggestionView suggest(UUID catalogId, String text, Authentication authentication) {
    var workspace = access.writable(catalogId, authentication).workspace();
    var description = checkedText(text);
    var catalog = catalogs.snapshot(catalogId).orElseThrow(ApiException::notFound);

    if (openAi == null) {
      return fromParser(catalog, description);
    }

    return switch (quota.reserve(workspace.id())) {
      case RESERVED -> fromModel(catalog, description);
      case WORKSPACE_LIMIT -> fallBack(catalog, description, workspaceLimitMessage());
      case DAILY_LIMIT -> fallBack(catalog, description, DAILY_LIMIT_MESSAGE);
    };
  }

  private String checkedText(String text) {
    var description = text == null ? "" : text.strip();

    if (description.isEmpty() || description.length() > properties.maxTextLength()) {
      throw new ApiException(
          400,
          "INVALID_REQUEST",
          "Describe the rule in 1 to " + properties.maxTextLength() + " characters");
    }

    return description;
  }

  private SuggestionView fromModel(CatalogSnapshot catalog, String text) {
    OpenAiSuggester.ModelReply reply;
    try {
      reply = openAi.ask(catalog, text);
    } catch (RuntimeException exception) {
      // Only the type is logged: provider messages can echo the visitor's text.
      LOG.warn("Assistant request failed: {}", exception.getClass().getName());
      return fallBack(catalog, text, UNAVAILABLE_MESSAGE);
    }

    if (reply != null && "SUGGESTION".equals(reply.status())) {
      var rule = toRule(catalog, reply);
      if (rule != null) {
        return new SuggestionView(Status.SUGGESTION, Mode.OPENAI, rule, null);
      }
    }

    // The visitor sees fixed help rather than anything the model wrote.
    if (reply != null && "CLARIFICATION".equals(reply.status())) {
      return new SuggestionView(Status.CLARIFICATION, Mode.OPENAI, null, ExampleParser.HELP);
    }

    return fallBack(catalog, text, UNAVAILABLE_MESSAGE);
  }

  /** The model's rule, or {@code null} when any ID, type, or limit is not valid here. */
  private static ProposedRule toRule(CatalogSnapshot catalog, OpenAiSuggester.ModelReply reply) {
    try {
      var source = UUID.fromString(reply.sourceFeatureId());
      var kind = RelationshipKind.valueOf(reply.kind());
      List<UUID> targets = reply.targetFeatureIds().stream().map(UUID::fromString).toList();

      return CatalogRules.validateNewRule(catalog, source, kind, targets).isEmpty()
          ? new ProposedRule(source, kind, targets)
          : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  /** The parser's single unambiguous reading, or the reason the model path gave up. */
  private static SuggestionView fallBack(CatalogSnapshot catalog, String text, String reason) {
    var reading = singleValidReading(catalog, ExampleParser.matches(text, catalog.features()));

    return reading == null
        ? new SuggestionView(Status.UNAVAILABLE, Mode.OPENAI, null, reason)
        : new SuggestionView(Status.SUGGESTION, Mode.EXAMPLE, reading, PARSER_STEPPED_IN);
  }

  private static SuggestionView fromParser(CatalogSnapshot catalog, String text) {
    var readings = ExampleParser.matches(text, catalog.features());

    if (readings.size() > 1) {
      return new SuggestionView(
          Status.CLARIFICATION, Mode.EXAMPLE, null, AMBIGUOUS_PREFIX + ExampleParser.HELP);
    }

    var reading = singleValidReading(catalog, readings);

    return reading == null
        ? new SuggestionView(Status.CLARIFICATION, Mode.EXAMPLE, null, ExampleParser.HELP)
        : new SuggestionView(Status.SUGGESTION, Mode.EXAMPLE, reading, null);
  }

  /** The only reading, when there is exactly one and it is valid in this catalog. */
  private static ProposedRule singleValidReading(
      CatalogSnapshot catalog, List<ProposedRule> readings) {
    if (readings.size() != 1) {
      return null;
    }

    var rule = readings.getFirst();
    var errors =
        CatalogRules.validateNewRule(
            catalog, rule.sourceFeatureId(), rule.kind(), rule.targetFeatureIds());

    return errors.isEmpty() ? rule : null;
  }

  private String workspaceLimitMessage() {
    return "This workspace has used all "
        + properties.workspaceLimit()
        + " assistant requests. You can still add relationships with the form.";
  }
}
