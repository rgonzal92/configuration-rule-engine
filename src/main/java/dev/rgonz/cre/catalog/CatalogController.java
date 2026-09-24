package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.CatalogViews.ApplyRequest;
import dev.rgonz.cre.catalog.CatalogViews.ApplyResult;
import dev.rgonz.cre.catalog.CatalogViews.CatalogSummary;
import dev.rgonz.cre.catalog.CatalogViews.CatalogView;
import dev.rgonz.cre.catalog.CatalogViews.CheckView;
import dev.rgonz.cre.catalog.CatalogViews.ConfigurationRequest;
import dev.rgonz.cre.catalog.CatalogViews.DraftRequest;
import dev.rgonz.cre.catalog.CatalogViews.DraftView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalog reads, the pending-batch workflow, and configuration testing. */
@RestController
@RequestMapping("/api/catalogs")
class CatalogController {
  private final CatalogService catalogs;
  private final DraftService drafts;

  public CatalogController(CatalogService catalogs, DraftService drafts) {
    this.catalogs = catalogs;
    this.drafts = drafts;
  }

  @GetMapping
  public List<CatalogSummary> list(Authentication authentication) {
    return catalogs.list(authentication);
  }

  @GetMapping("/{id}")
  public CatalogView read(@PathVariable UUID id, Authentication authentication) {
    return catalogs.read(id, authentication);
  }

  @GetMapping("/{id}/draft")
  public DraftView draft(@PathVariable UUID id, Authentication authentication) {
    return drafts.read(id, authentication);
  }

  @PutMapping("/{id}/draft")
  public DraftView saveDraft(
      @PathVariable UUID id, @RequestBody DraftRequest request, Authentication authentication) {
    return drafts.save(id, request, authentication);
  }

  @PostMapping("/{id}/draft/check")
  public CheckView check(@PathVariable UUID id, Authentication authentication) {
    return drafts.check(id, authentication);
  }

  @PostMapping("/{id}/draft/apply")
  public ApplyResult apply(
      @PathVariable UUID id, @RequestBody ApplyRequest request, Authentication authentication) {
    return drafts.apply(id, request, authentication);
  }

  @PostMapping("/{id}/configurations/check")
  public ConfigurationResult checkConfiguration(
      @PathVariable UUID id,
      @RequestBody ConfigurationRequest request,
      Authentication authentication) {
    return catalogs.checkConfiguration(id, request.featureIds(), authentication);
  }
}
