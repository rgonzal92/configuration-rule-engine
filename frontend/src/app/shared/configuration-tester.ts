import { Component, inject, input, linkedSignal, signal } from '@angular/core';
import { Catalog, CatalogService, ConfigurationResult, toProblem } from '../core/catalog.service';

let testerCount = 0;

/** Lets a visitor choose features and see whether the active rules allow that combination. */
@Component({
  selector: 'app-configuration-tester',
  template: `
    <section class="panel" [attr.aria-labelledby]="headingId">
      <h4 [id]="headingId">Test a configuration</h4>
      <fieldset [disabled]="busy()">
        <legend>Features to choose</legend>
        @for (feature of catalog().features; track feature.id) {
          <label class="choice">
            <input
              type="checkbox"
              [checked]="selected().includes(feature.id)"
              (change)="toggle(feature.id)"
            />
            {{ feature.name }}
          </label>
        }
      </fieldset>
      <button type="button" [disabled]="busy()" (click)="test()">Test configuration</button>

      <div role="status">
        @let outcome = result();
        @if (problem()) {
          <p>{{ problem() }}</p>
        } @else if (outcome?.valid) {
          <p>This configuration is allowed.</p>
        } @else if (outcome) {
          <p>This configuration is not allowed:</p>
          <ul>
            @for (item of outcome.missing; track item.message) {
              <li>{{ item.message }}</li>
            }
            @for (item of outcome.conflicts; track item.message) {
              <li>{{ item.message }}</li>
            }
          </ul>
        }
      </div>
    </section>
  `,
})
export class ConfigurationTester {
  private readonly catalogs = inject(CatalogService);

  readonly catalog = input.required<Catalog>();

  protected readonly selected = signal<string[]>([]);
  /** Cleared whenever the catalog changes, so an old verdict is never shown for new rules. */
  protected readonly result = linkedSignal<Catalog, ConfigurationResult | null>({
    source: this.catalog,
    computation: () => null,
  });
  protected readonly problem = linkedSignal<Catalog, string | null>({
    source: this.catalog,
    computation: () => null,
  });
  protected readonly busy = signal(false);

  /** Unique per tester, because the showcase shows one tester for each catalog. */
  protected readonly headingId = `tester-heading-${++testerCount}`;

  protected toggle(id: string): void {
    this.selected.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
    this.result.set(null);
  }

  /** A response that arrives after the catalog changed describes old rules, so it is dropped. */
  protected async test(): Promise<void> {
    const catalog = this.catalog();
    this.problem.set(null);
    this.busy.set(true);

    try {
      const result = await this.catalogs.testConfiguration(catalog.id, this.selected());

      if (this.catalog() === catalog) {
        this.result.set(result);
      }
    } catch (error) {
      if (this.catalog() === catalog) {
        this.problem.set(toProblem(error).message);
      }
    } finally {
      this.busy.set(false);
    }
  }
}
