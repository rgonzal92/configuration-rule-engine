import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { Ripple } from 'primeng/ripple';
import { Tag } from 'primeng/tag';
import { Catalog } from '../../core/catalog.service';
import { RelationshipList } from '../../shared/relationship-list';
import { formatChange, formatCount } from '../../shared/rule-text';

/** A small catalog with one rule of each type, which doubles as a legend. */
const SAMPLE: Catalog = {
  id: 'sample',
  name: 'Sample',
  readOnly: true,
  revision: 1,
  features: [
    { id: 'gpu', code: 'GPU', name: 'Dedicated GPU' },
    { id: 'charger', code: 'CHARGER', name: 'High-wattage charger' },
    { id: 'fanless', code: 'FANLESS', name: 'Fanless chassis' },
    { id: 'touch', code: 'TOUCH', name: 'Touchscreen' },
    { id: 'stylus', code: 'STYLUS', name: 'Stylus support' },
  ],
  groups: [
    { id: 'g1', sourceFeatureId: 'gpu', kind: 'REQUIRES', targetFeatureIds: ['charger'] },
    { id: 'g2', sourceFeatureId: 'touch', kind: 'REQUIRED_WITH', targetFeatureIds: ['stylus'] },
    { id: 'g3', sourceFeatureId: 'fanless', kind: 'NOT_ALLOWED_WITH', targetFeatureIds: ['gpu'] },
  ],
};

/** Valid combinations of the sample's five features before and after its third rule is added. */
const SAMPLE_BEFORE = 18;
const SAMPLE_AFTER = 15;

/** Explains what the engine does and leads to the workspace and the showcase. */
@Component({
  imports: [ButtonDirective, Card, RelationshipList, Ripple, RouterLink, Tag],
  selector: 'app-home-page',
  template: `
    <h1 class="text-3xl font-semibold tracking-tight wrap-break-word sm:text-4xl">
      Configuration Rule Engine
    </h1>
    <p class="mt-2 text-lg">Create, check, and apply feature relationships.</p>
    <p class="help mt-2 max-w-prose">
      Explore how product options depend on or exclude one another. Stage changes to a private
      laptop catalog, check their combined effect, and apply them together.
    </p>
    <div class="mt-6 flex flex-wrap gap-2">
      <a pButton pRipple routerLink="/workspace" class="min-h-11">Open workspace</a>
      <a pButton pRipple routerLink="/showcase" [outlined]="true" class="min-h-11">
        Explore showcase
      </a>
    </div>

    <div class="mt-10 grid gap-4 md:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <figure class="m-0">
        <figcaption class="label mb-2">Relationships</figcaption>
        <p-card>
          <app-relationship-list [catalog]="sample" />
        </p-card>
      </figure>
      <figure class="m-0">
        <figcaption class="label mb-2">Check before applying</figcaption>
        <p-card>
          <ul class="diff-list">
            <li class="diff" data-diff="add">
              Fanless chassis and Dedicated GPU can't be chosen together.
            </li>
          </ul>
          <p class="mt-3">
            Valid configurations:
            <span class="metric-value block"
              >{{ formatCount(before) }} → {{ formatCount(after) }}</span
            >
          </p>
          <p-tag severity="secondary" class="font-mono" [value]="formatChange(before, after)" />
        </p-card>
      </figure>
    </div>
  `,
})
export class HomePage {
  protected readonly sample = SAMPLE;
  protected readonly before = SAMPLE_BEFORE;
  protected readonly after = SAMPLE_AFTER;
  protected readonly formatCount = formatCount;
  protected readonly formatChange = formatChange;
}
