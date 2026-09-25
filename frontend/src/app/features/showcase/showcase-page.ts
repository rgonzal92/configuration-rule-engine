import { Component, OnInit, inject, signal } from '@angular/core';
import { CatalogService, CatalogSummary } from '../../core/catalog.service';
import { CatalogViewer } from './catalog-viewer';

/** Shows the shared public examples, which no visitor can change. */
@Component({
  imports: [CatalogViewer],
  selector: 'app-showcase-page',
  template: `
    <section aria-labelledby="showcase-heading">
      <h1 id="showcase-heading" class="text-xl font-semibold">Public catalog examples</h1>
      <p role="status" class="help mt-1">
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
      <div class="mt-6 grid gap-4">
        @for (catalog of catalogs(); track catalog.id) {
          <app-catalog-viewer [catalogId]="catalog.id" />
        }
      </div>
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
