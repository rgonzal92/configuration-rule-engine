import { Component, inject, input, linkedSignal, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { Ripple } from 'primeng/ripple';
import { Tag } from 'primeng/tag';
import { Catalog, CatalogService, ConfigurationResult, toProblem } from '../core/catalog.service';

let testerCount = 0;

/** Lets a visitor choose features and see whether the active rules allow that combination. */
@Component({
  imports: [ButtonDirective, Checkbox, FormsModule, Ripple, Tag],
  selector: 'app-configuration-tester',
  template: `
    <section [attr.aria-labelledby]="headingId">
      <h3 [id]="headingId" class="font-semibold">Test a configuration</h3>
      <fieldset class="choices mt-3" [disabled]="busy()">
        <legend class="px-1 font-semibold">Features to choose</legend>
        @for (feature of catalog().features; track feature.id) {
          @let inputId = headingId + '-' + feature.id;
          <div class="choice">
            <p-checkbox
              [inputId]="inputId"
              [binary]="true"
              [ngModel]="selected().includes(feature.id)"
              (ngModelChange)="toggle(feature.id)"
            />
            <label [for]="inputId">{{ feature.name }}</label>
          </div>
        }
      </fieldset>
      <button
        pButton
        pRipple
        type="button"
        class="mt-3 min-h-11"
        [disabled]="busy()"
        (click)="test()"
      >
        Test configuration
      </button>

      <div role="status" class="mt-3">
        @let outcome = result();
        @if (problem()) {
          <p>{{ problem() }}</p>
        } @else if (outcome?.valid) {
          <p-tag severity="success" icon="pi pi-check" value="This configuration is allowed." />
        } @else if (outcome) {
          <p-tag severity="danger" icon="pi pi-times" value="This configuration is not allowed:" />
          <ul class="rules">
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
