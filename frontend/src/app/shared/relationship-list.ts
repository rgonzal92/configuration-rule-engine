import { Component, input, output } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { Chip } from 'primeng/chip';
import { DataView } from 'primeng/dataview';
import { Ripple } from 'primeng/ripple';
import { Tag } from 'primeng/tag';
import { Catalog, Group } from '../core/catalog.service';
import { KIND_ICONS, KIND_LABELS, describeRule, featureName } from './rule-text';

/**
 * The catalog's active relationships as rows of source, type, and targets. Screen readers hear
 * each row as one plain-language sentence instead of the visual columns. A relationship with a
 * pending change can't be edited again until that change is undone, so one relationship never has
 * two changes.
 */
@Component({
  imports: [ButtonDirective, Chip, DataView, Ripple, Tag],
  selector: 'app-relationship-list',
  template: `
    <p-dataview [value]="catalog().groups" emptyMessage="No active relationships.">
      <ng-template #list>
        <div class="rel-table">
          @if (catalog().groups.length > 0) {
            <div class="rel-head label" aria-hidden="true">
              <span>Source</span>
              <span>Type</span>
              <span>Targets</span>
            </div>
          }
          <ul class="rel-rows">
            @for (group of catalog().groups; track group.id) {
              @let sentence = describe(group);
              <li class="rel-row">
                <span class="sr-only">{{ sentence }}</span>
                <span aria-hidden="true" class="min-w-0 font-medium">{{
                  name(group.sourceFeatureId)
                }}</span>
                <span aria-hidden="true" class="min-w-0">
                  <p-tag
                    [attr.data-kind]="group.kind"
                    [icon]="kindIcons[group.kind]"
                    [value]="kindLabels[group.kind]"
                  />
                </span>
                <span
                  aria-hidden="true"
                  class="flex min-w-0 basis-full flex-wrap gap-1 md:basis-auto"
                >
                  @for (target of group.targetFeatureIds; track target) {
                    <p-chip class="max-w-full text-xs">{{ name(target) }}</p-chip>
                  }
                </span>
                @if (pendingGroupIds().includes(group.id)) {
                  <p-tag severity="warn" value="change pending" />
                } @else if (editable()) {
                  <span class="flex gap-2">
                    <button
                      pButton
                      pRipple
                      type="button"
                      size="small"
                      [outlined]="true"
                      class="min-h-11 sm:min-h-9"
                      [attr.aria-label]="'Edit: ' + sentence"
                      [disabled]="disabled()"
                      (click)="edit.emit(group)"
                    >
                      Edit
                    </button>
                    <button
                      pButton
                      pRipple
                      type="button"
                      size="small"
                      severity="danger"
                      [outlined]="true"
                      class="min-h-11 sm:min-h-9"
                      [attr.aria-label]="'Remove: ' + sentence"
                      [disabled]="disabled()"
                      (click)="remove.emit(group)"
                    >
                      Remove
                    </button>
                  </span>
                }
              </li>
            }
          </ul>
        </div>
      </ng-template>
    </p-dataview>
  `,
})
export class RelationshipList {
  readonly catalog = input.required<Catalog>();
  readonly editable = input(false);
  readonly pendingGroupIds = input<string[]>([]);
  readonly disabled = input(false);

  readonly edit = output<Group>();
  readonly remove = output<Group>();

  protected readonly kindLabels = KIND_LABELS;
  protected readonly kindIcons = KIND_ICONS;

  protected name(id: string): string {
    return featureName(this.catalog(), id);
  }

  protected describe(group: Group): string {
    return describeRule(this.catalog(), group.sourceFeatureId, group.kind, group.targetFeatureIds);
  }
}
