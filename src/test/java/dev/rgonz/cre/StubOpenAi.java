package dev.rgonz.cre;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A local stand-in for the OpenAI chat completions API. Tests queue the replies it should give and
 * read back every request it received, so they can prove how many calls reached the model.
 */
final class StubOpenAi implements AutoCloseable {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** One scripted reply: an HTTP status, a body, and an optional delay before answering. */
  record Reply(int status, String body, long delayMillis) {}

  private final HttpServer server;
  private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
  private final List<JsonNode> requests = new CopyOnWriteArrayList<>();

  StubOpenAi() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  /** The base URL to configure as {@code spring.ai.openai.base-url}. */
  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  /** Answers the next request with this structured-output object as the message content. */
  void answer(Map<String, Object> content) {
    replies.add(new Reply(200, completion(JSON.writeValueAsString(content), null), 0));
  }

  /** Answers the next request with raw message content, such as text that is not JSON. */
  void answerRaw(String content) {
    replies.add(new Reply(200, completion(content, null), 0));
  }

  void refuse() {
    replies.add(new Reply(200, completion(null, "I can't help with that."), 0));
  }

  void fail(int status) {
    replies.add(new Reply(status, "{\"error\":{\"message\":\"stub failure\"}}", 0));
  }

  void answerAfter(long delayMillis, Map<String, Object> content) {
    replies.add(new Reply(200, completion(JSON.writeValueAsString(content), null), delayMillis));
  }

  List<JsonNode> requests() {
    return List.copyOf(requests);
  }

  void reset() {
    replies.clear();
    requests.clear();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      requests.add(JSON.readTree(body));

      // An unexpected request gets a server error so it cannot pass as a valid answer.
      var reply = replies.poll();
      if (reply == null) {
        reply = new Reply(500, "{\"error\":{\"message\":\"no reply queued\"}}", 0);
      }

      if (reply.delayMillis() > 0) {
        Thread.sleep(reply.delayMillis());
      }

      var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(reply.status(), bytes.length);
      exchange.getResponseBody().write(bytes);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (IOException ignored) {
      // The client gave up first, as it does after a timeout.
    }
  }

  private static String completion(String content, String refusal) {
    var message = new LinkedHashMap<String, Object>();
    message.put("role", "assistant");
    message.put("content", content);
    message.put("refusal", refusal);

    var choice = new LinkedHashMap<String, Object>();
    choice.put("index", 0);
    choice.put("message", message);
    choice.put("finish_reason", "stop");
    choice.put("logprobs", null);

    return JSON.writeValueAsString(
        Map.of(
            "id",
            "chatcmpl-stub",
            "object",
            "chat.completion",
            "created",
            1,
            "model",
            "stub-model",
            "choices",
            List.of(choice),
            "usage",
            Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)));
  }
}
