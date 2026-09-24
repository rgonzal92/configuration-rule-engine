package dev.rgonz.cre.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Guards the browser fallback and unknown-API response contract. */
class WebRoutesTest {
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new WebRoutes(), new ApiFallback()).build();

  @Test
  void browserRoutesForwardToShell() throws Exception {
    for (String route : new String[] {"/", "/workspace", "/showcase"}) {
      mvc.perform(get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }
  }

  @Test
  void unknownApiRouteIsJsonNotHtml() throws Exception {
    mvc.perform(get("/api/unknown"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void unknownApiRouteStaysJsonWhenOpenedInBrowser() throws Exception {
    mvc.perform(get("/api/unknown").accept("text/html"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void unknownAssetDoesNotForwardToShell() throws Exception {
    mvc.perform(get("/missing.js")).andExpect(status().isNotFound());
  }
}
