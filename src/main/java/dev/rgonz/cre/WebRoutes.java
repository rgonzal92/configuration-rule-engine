package dev.rgonz.cre;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Forwards known browser routes to the packaged Angular application. */
@Controller
public class WebRoutes {
  @GetMapping({"/", "/workspace", "/showcase"})
  public String shell() {
    return "forward:/index.html";
  }
}
