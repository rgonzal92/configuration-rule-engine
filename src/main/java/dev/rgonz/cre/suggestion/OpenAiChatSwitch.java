package dev.rgonz.cre.suggestion;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * Switches the OpenAI chat model off when no API key is configured. Spring AI builds its OpenAI
 * client at startup and refuses to start without a credential, so without a key the app uses only
 * the example parser.
 *
 * <p>The key is read each time the setting is looked up rather than once at startup, so a key
 * supplied by a later property source still counts. The source is added last, so an explicit {@code
 * spring.ai.model.chat} setting always wins.
 */
class OpenAiChatSwitch implements EnvironmentPostProcessor {
  private static final String API_KEY = "spring.ai.openai.api-key";
  private static final String CHAT_MODEL = "spring.ai.model.chat";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addLast(
            new PropertySource<>("openAiChatSwitch") {
              @Override
              public Object getProperty(String name) {
                if (!CHAT_MODEL.equals(name)) {
                  return null;
                }

                return StringUtils.hasText(environment.getProperty(API_KEY)) ? null : "none";
              }
            });
  }
}
