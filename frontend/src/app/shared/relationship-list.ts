import { Component, input, output } from '@angular/core';
import { Catalog, Group } from '../core/catalog.service';
import { describeRule } from './rule-text';

/**
 * The catalog's active relationships, each in plain language. A relationship with a pending change
 * can't be edited again until that change is undone, so one relationship never has two changes.
 */
@Component({
  selector: 'app-relationship-list',
  template: `
    @if (catalog().groups.length === 0) {
      <p>No active relationships.</p>
    } @else {
      <ul class="rules">
        @for (group of catalog().groups; track group.id) {
          @let sentence = describe(group);
          <li>
            <span>{{ sentence }}</span>
            @if (pendingGroupIds().includes(group.id)) {
              <span class="tag">change pending</span>
            } @else if (editable()) {
              <span class="actions">
                <button
                  type="button"
                  [attr.aria-label]="'Edit: ' + sentence"
                  [disabled]="disabled()"
                  (click)="edit.emit(group)"
                >
                  Edit
                </button>
                <button
                  type="button"
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
    }
  `,
})
export class RelationshipList {
  readonly catalog = input.required<Catalog>();
  readonly editable = input(false);
  readonly pendingGroupIds = input<string[]>([]);
  readonly disabled = input(false);

  readonly edit = output<Group>();
  readonly remove = output<Group>();

  protected describe(group: Group): string {
    return describeRule(this.catalog(), group.sourceFeatureId, group.kind, group.targetFeatureIds);
  }
}
