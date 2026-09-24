import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Catalog, CatalogService, toProblem } from '../../core/catalog.service';
import { ConfigurationTester } from '../../shared/configuration-tester';
import { RelationshipList } from '../../shared/relationship-list';

/** A read-only catalog: its features, relationships, and a configuration tester. */
@Component({
  imports: [ConfigurationTester, RelationshipList],
  selector: 'app-catalog-viewer',
  template: `
    @let current = catalog();
    @if (current) {
      <article class="catalog" [attr.aria-label]="current.name + ' catalog'">
        <h3>{{ current.name }}</h3>
        <p>{{ current.features.length }} optional features. Read-only.</p>
        <h4>Relationships</h4>
        <app-relationship-list [catalog]="current" />
        <app-configuration-tester [catalog]="current" />
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
