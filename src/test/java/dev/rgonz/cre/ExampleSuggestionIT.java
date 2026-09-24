package dev.rgonz.cre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises the assistant without an API key, where only the labeled example parser answers. */
class ExampleSuggestionIT extends ApplicationIT {
  static final String TOUCHSCREEN = "00000000-0000-0000-0000-00000000f004";
  static final String STYLUS = "00000000-0000-0000-0000-00000000f005";

  private GuestClient.Reply suggest(GuestClient guest, String catalogId, String text)
      throws Exception {
    return guest.postJson("/api/catalogs/" + catalogId + "/suggestions", Map.of("text", text));
  }

  @Test
  void theParserAnswersWhenNoKeyIsConfigured() throws Exception {
    assertEquals("EXAMPLE", visitor().get("/api/suggestions/mode").text("mode"));

    var guest = visitor();
    guest.start();
    var catalogId = guest.get("/api/catalogs").body().get(0).path("id").asString();

    var reply = suggest(guest, catalogId, "stylus support is required with touchscreen");
    assertEquals("SUGGESTION", reply.text("status"));
    assertEquals("EXAMPLE", reply.text("mode"));
    assertEquals(STYLUS, reply.body().path("rule").path("sourceFeatureId").asString());
    assertEquals("REQUIRED_WITH", reply.body().path("rule").path("kind").asString());
    assertEquals(TOUCHSCREEN, reply.body().path("rule").path("targetFeatureIds").get(0).asString());

    var unclear = suggest(guest, catalogId, "make touch screens need pens");
    assertEquals("CLARIFICATION", unclear.text("status"));
    assertTrue(unclear.text("message").contains("A requires B"));

    assertEquals(
        0L, jdbc.sql("SELECT count(*) FROM suggestion_attempt").query(Long.class).single());
  }
}
