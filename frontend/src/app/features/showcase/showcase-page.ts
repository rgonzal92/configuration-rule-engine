import { Component, OnInit, inject, signal } from '@angular/core';
import { CatalogService, CatalogSummary } from '../../core/catalog.service';
import { CatalogViewer } from './catalog-viewer';

/** Shows the shared public examples, which no visitor can change. */
@Component({
  imports: [CatalogViewer],
  selector: 'app-showcase-page',
  template: `
    <section aria-labelledby="showcase-heading">
      <h2 id="showcase-heading">Public catalog examples</h2>
      <p role="status">
        @switch (loaded()) {
          @case (true) {
            These examples are read-only. Start a guest workspace to make your own changes.
          }
          @case (false) {
            The examples could not be loaded. Refresh the page to try again.
          }
          @default {
            Loading examples…
          }
        }
      </p>
      @for (catalog of catalogs(); track catalog.id) {
        <app-catalog-viewer [catalogId]="catalog.id" />
      }
    </section>
  `,
})
export class ShowcasePage implements OnInit {
  private readonly catalogService = inject(CatalogService);

  protected readonly loaded = signal<boolean | undefined>(undefined);
  protected readonly catalogs = signal<CatalogSummary[]>([]);

  async ngOnInit(): Promise<void> {
    try {
      const all = await this.catalogService.list();

      this.catalogs.set(all.filter((catalog) => catalog.readOnly));
      this.loaded.set(true);
    } catch {
      this.loaded.set(false);
    }
  }
}
