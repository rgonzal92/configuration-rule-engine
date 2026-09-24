package dev.rgonz.cre;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Calls the real OpenAI API once to prove connectivity, the configured model, and that its
 * structured answer parses into a valid suggestion. It proves nothing about accuracy. It runs only
 * when {@code CRE_LIVE_AI_SMOKE=true} and {@code OPENAI_API_KEY} are set, because each run costs
 * money.
 */
@EnabledIfEnvironmentVariable(named = "CRE_LIVE_AI_SMOKE", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiLiveSmokeIT extends ApplicationIT {
  static final String TOUCHSCREEN = "00000000-0000-0000-0000-00000000f004";
  static final String STYLUS = "00000000-0000-0000-0000-00000000f005";

  @Test
  void theLiveModelProposesAValidRule() throws Exception {
    assertEquals("OPENAI", visitor().get("/api/suggestions/mode").text("mode"));

    var guest = visitor();
    guest.start();
    var catalogId = guest.get("/api/catalogs").body().get(0).path("id").asString();

    var reply =
        guest.postJson(
            "/api/catalogs/" + catalogId + "/suggestions",
            Map.of("text", "Every touchscreen laptop must also come with stylus support."));

    // OPENAI mode means the model's own answer passed validation; the parser was not used.
    assertEquals("SUGGESTION", reply.text("status"), reply.body().toString());
    assertEquals("OPENAI", reply.text("mode"));

    var rule = reply.body().path("rule");
    assertEquals(TOUCHSCREEN, rule.path("sourceFeatureId").asString());
    assertEquals("REQUIRES", rule.path("kind").asString());
    assertEquals(STYLUS, rule.path("targetFeatureIds").get(0).asString());
  }
}
