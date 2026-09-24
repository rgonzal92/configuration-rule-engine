package dev.rgonz.cre.suggestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Proves the OpenAI chat model is switched on only when an API key is configured. */
class OpenAiChatSwitchTest {
  private static StandardEnvironment environment(String apiKey) {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("spring.ai.openai.api-key", apiKey)));

    return environment;
  }

  @Test
  void turnsChatOffWithoutAKey() {
    var environment = environment(" ");

    new OpenAiChatSwitch().postProcessEnvironment(environment, null);

    assertEquals("none", environment.getProperty("spring.ai.model.chat"));
  }

  @Test
  void seesAKeyAddedAfterStartup() {
    var environment = environment("");
    new OpenAiChatSwitch().postProcessEnvironment(environment, null);

    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("later", Map.of("spring.ai.openai.api-key", "sk-test")));

    assertNull(environment.getProperty("spring.ai.model.chat"));
  }

  @Test
  void anExplicitSettingWins() {
    var environment = environment("");
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("explicit", Map.of("spring.ai.model.chat", "openai")));

    new OpenAiChatSwitch().postProcessEnvironment(environment, null);

    assertEquals("openai", environment.getProperty("spring.ai.model.chat"));
  }

  @Test
  void leavesChatOnWithAKey() {
    var environment = environment("sk-test");

    new OpenAiChatSwitch().postProcessEnvironment(environment, null);

    assertNull(environment.getProperty("spring.ai.model.chat"));
  }
}
