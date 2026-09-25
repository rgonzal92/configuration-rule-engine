import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Card } from 'primeng/card';
import { Divider } from 'primeng/divider';
import { Catalog, CatalogService, toProblem } from '../../core/catalog.service';
import { ConfigurationTester } from '../../shared/configuration-tester';
import { RelationshipList } from '../../shared/relationship-list';

/** A read-only catalog: its features, relationships, and a configuration tester. */
@Component({
  imports: [Card, ConfigurationTester, Divider, RelationshipList],
  selector: 'app-catalog-viewer',
  template: `
    @let current = catalog();
    @if (current) {
      <article [attr.aria-label]="current.name + ' catalog'">
        <p-card>
          <div class="flex flex-wrap items-baseline justify-between gap-x-4">
            <h2 class="text-lg font-semibold">{{ current.name }}</h2>
            <p class="meta">
              rev {{ current.revision }} · {{ current.features.length }} features · read-only
            </p>
          </div>
          <h3 class="label mt-4 mb-1">Relationships</h3>
          <app-relationship-list [catalog]="current" />
          <p-divider />
          <app-configuration-tester [catalog]="current" />
        </p-card>
      </article>
    } @else if (problem()) {
      <p role="status">{{ problem() }}</p>
    }
  `,
})
export class CatalogViewer implements OnInit {
  private readonly catalogs = inject(CatalogService);

  readonly catalogId = input.required<string>();

  protected readonly catalog = signal<Catalog | null>(null);
  protected readonly problem = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.catalog.set(await this.catalogs.catalog(this.catalogId()));
    } catch (error) {
      this.problem.set(toProblem(error).message);
    }
  }
}
