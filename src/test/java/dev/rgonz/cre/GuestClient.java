package dev.rgonz.cre;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One visitor's HTTP client. It keeps its own cookies by hand because the JDK cookie manager never
 * returns {@code Secure} cookies over plain-HTTP loopback test servers.
 */
final class GuestClient {
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String base;
  private final Map<String, String> cookies = new LinkedHashMap<>();
  private final List<String> setCookieHeaders = new ArrayList<>();

  GuestClient(String base) {
    this.base = base;
  }

  /** A JSON response reduced to what the tests assert. */
  record Reply(int status, JsonNode body) {
    String text(String field) {
      return body.path(field).asString();
    }
  }

  Reply get(String path) throws IOException, InterruptedException {
    return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
  }

  /** Posts with the CSRF header echoed from the {@code XSRF-TOKEN} cookie, as Angular does. */
  Reply post(String path) throws IOException, InterruptedException {
    return post(path, cookies.get("XSRF-TOKEN"));
  }

  Reply post(String path, String csrfHeader) throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create(base + path)).POST(HttpRequest.BodyPublishers.noBody());

    if (csrfHeader != null) {
      request.header("X-XSRF-TOKEN", csrfHeader);
    }

    return send(request);
  }

  /** Sends a JSON body with the echoed CSRF header. */
  Reply postJson(String path, Object body) throws IOException, InterruptedException {
    return sendJson("POST", path, body);
  }

  Reply putJson(String path, Object body) throws IOException, InterruptedException {
    return sendJson("PUT", path, body);
  }

  private Reply sendJson(String method, String path, Object body)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create(base + path))
            .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .header("Content-Type", "application/json");

    var csrf = cookies.get("XSRF-TOKEN");
    if (csrf != null) {
      request.header("X-XSRF-TOKEN", csrf);
    }

    return send(request);
  }

  /** Loads the session state and the CSRF cookie, then starts a guest workspace. */
  Reply start() throws IOException, InterruptedException {
    get("/api/session");

    return post("/api/demo/sessions");
  }

  String cookie(String name) {
    return cookies.get(name);
  }

  void setCookie(String name, String value) {
    cookies.put(name, value);
  }

  /** Every raw {@code Set-Cookie} header seen for the named cookie. */
  List<String> setCookieHeaders(String name) {
    return setCookieHeaders.stream().filter(h -> h.startsWith(name + "=")).toList();
  }

  private Reply send(HttpRequest.Builder request) throws IOException, InterruptedException {
    if (!cookies.isEmpty()) {
      var header =
          cookies.entrySet().stream()
              .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
              .collect(Collectors.joining("; "));
      request.header("Cookie", header);
    }

    var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());

    for (var header : response.headers().allValues("set-cookie")) {
      setCookieHeaders.add(header);

      var pair = header.split(";", 2)[0].split("=", 2);
      if (pair[1].isEmpty() || header.toLowerCase().contains("max-age=0")) {
        cookies.remove(pair[0]);
      } else {
        cookies.put(pair[0], pair[1]);
      }
    }

    boolean json = response.headers().firstValue("content-type").orElse("").contains("json");
    var body = json ? JSON.readTree(response.body()) : JSON.nullNode();

    return new Reply(response.statusCode(), body);
  }
}
