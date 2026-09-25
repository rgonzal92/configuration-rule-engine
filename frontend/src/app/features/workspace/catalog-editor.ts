import {
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Badge } from 'primeng/badge';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { Checkbox } from 'primeng/checkbox';
import { Divider } from 'primeng/divider';
import { Message } from 'primeng/message';
import { Ripple } from 'primeng/ripple';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import {
  Catalog,
  CatalogService,
  CheckView,
  Draft,
  Group,
  Operation,
  ProposedRule,
  RelationshipKind,
  toProblem,
} from '../../core/catalog.service';
import { ConfigurationTester } from '../../shared/configuration-tester';
import { RelationshipList } from '../../shared/relationship-list';
import {
  KIND_HELP,
  KIND_LABELS,
  describeOperation,
  formatChange,
  formatCount,
} from '../../shared/rule-text';
import { RuleSuggestion } from './rule-suggestion';
import { editActive, removeActive, replacePending, stageNew, undo } from './staging';

const MAX_TARGETS = 10;
/** Matches DraftRules.MAX_OPERATIONS, the server's limit on changes in one batch. */
const MAX_CHANGES = 32;

/** The diff marker for each kind of pending change. */
const DIFF_MARKERS: Record<Operation['type'], 'add' | 'change' | 'remove'> = {
  CREATE: 'add',
  UPDATE: 'change',
  DELETE: 'remove',
};

/** What the relationship form is editing. */
type FormMode =
  | { type: 'new' }
  | { type: 'active'; group: Group }
  | { type: 'pending'; index: number };

/** The guest's catalog: stage relationship changes, check the whole batch, and apply it. */
@Component({
  imports: [
    Badge,
    ButtonDirective,
    Card,
    Checkbox,
    ConfigurationTester,
    Divider,
    FormsModule,
    Message,
    RelationshipList,
    Ripple,
    RuleSuggestion,
    Select,
    Tag,
  ],
  selector: 'app-catalog-editor',
  templateUrl: './catalog-editor.html',
})
export class CatalogEditor implements OnInit {
  private readonly catalogs = inject(CatalogService);
  private readonly injector = inject(Injector);
  private readonly sourceSelect = viewChild<Select>('sourceSelect');
  private readonly pendingHeading = viewChild<ElementRef<HTMLElement>>('pendingHeading');

  protected readonly kindOptions = (Object.keys(KIND_LABELS) as RelationshipKind[]).map((kind) => ({
    value: kind,
    label: KIND_LABELS[kind],
  }));
  protected readonly kindHelp = KIND_HELP;
  protected readonly maxTargets = MAX_TARGETS;
  protected readonly maxChanges = MAX_CHANGES;
  protected readonly diffOf = DIFF_MARKERS;
  protected readonly formatCount = formatCount;
  protected readonly formatChange = formatChange;

  protected readonly catalog = signal<Catalog | null>(null);
  protected readonly draft = signal<Draft | null>(null);
  protected readonly checkView = signal<CheckView | null>(null);
  protected readonly status = signal('Loading your catalog…');
  protected readonly details = signal<string[]>([]);
  protected readonly busy = signal(false);

  protected readonly mode = signal<FormMode>({ type: 'new' });
  protected readonly source = signal('');
  protected readonly kind = signal<RelationshipKind>('REQUIRES');
  protected readonly targets = signal<string[]>([]);

  /** Reused when an apply is retried, so the server can recognize the retry. */
  private command: { id: string; draftVersion: number } | null = null;

  protected readonly operations = computed(() => this.draft()?.operations ?? []);

  protected readonly pendingGroupIds = computed(() =>
    this.operations().flatMap((operation) =>
      operation.type === 'CREATE' ? [] : [operation.groupId],
    ),
  );

  protected readonly targetChoices = computed(
    () => this.catalog()?.features.filter((feature) => feature.id !== this.source()) ?? [],
  );

  protected readonly canApply = computed(() => {
    const check = this.checkView();

    return !!check && check.result.valid && check.draftVersion === this.draft()?.draftVersion;
  });

  async ngOnInit(): Promise<void> {
    try {
      const guestCatalog = (await this.catalogs.list()).find((catalog) => catalog.kind === 'GUEST');
      if (!guestCatalog) {
        this.status.set('Your catalog could not be found. Refresh the page to try again.');
        return;
      }

      await this.reload(guestCatalog.id);
      this.status.set('');
    } catch (error) {
      this.status.set(toProblem(error).message);
    }
  }

  protected describe(operation: Operation): string {
    const catalog = this.catalog();

    return catalog ? describeOperation(catalog, operation) : '';
  }

  protected chooseSource(id: string): void {
    this.source.set(id);
    this.targets.update((ids) => ids.filter((target) => target !== id));
  }

  protected toggleTarget(id: string): void {
    this.targets.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  protected startEditActive(group: Group): void {
    this.fillForm(
      { type: 'active', group },
      group.sourceFeatureId,
      group.kind,
      group.targetFeatureIds,
    );
  }

  protected startEditPending(index: number): void {
    const operation = this.operations()[index];

    if (operation.type === 'CREATE') {
      this.fillForm(
        { type: 'pending', index },
        operation.sourceFeatureId,
        operation.kind,
        operation.targetFeatureIds,
      );
    }
  }

  /** Puts a suggested relationship in the form, where the visitor reviews it before staging. */
  protected useSuggestion(rule: ProposedRule): void {
    this.fillForm({ type: 'new' }, rule.sourceFeatureId, rule.kind, rule.targetFeatureIds);
    this.focusForm();
  }

  protected resetForm(): void {
    this.fillForm({ type: 'new' }, '', 'REQUIRES', []);
  }

  /** Stages the form's relationship as a new, edited, or removed relationship. */
  protected async submitForm(): Promise<void> {
    const input = {
      sourceFeatureId: this.source(),
      kind: this.kind(),
      targetFeatureIds: this.targets(),
    };
    const mode = this.mode();

    if (!input.sourceFeatureId) {
      this.status.set('Choose a source feature.');
      return;
    }

    if (mode.type === 'new' && input.targetFeatureIds.length === 0) {
      this.status.set('Choose at least one target feature.');
      return;
    }

    const operations = this.operations();
    const next =
      mode.type === 'active'
        ? editActive(operations, mode.group, input)
        : mode.type === 'pending'
          ? replacePending(operations, mode.index, input)
          : stageNew(operations, input);

    if (await this.save(next)) {
      this.resetForm();
    }
  }

  protected async removeGroup(group: Group): Promise<void> {
    await this.saveAndRefocus(removeActive(this.operations(), group));
  }

  protected async undoChange(index: number): Promise<void> {
    await this.saveAndRefocus(undo(this.operations(), index));
  }

  /**
   * The pressed button disappears after these saves, so focus moves to the pending list heading.
   * Positions in that list shift too, so an open edit form is closed.
   */
  private async saveAndRefocus(operations: Operation[]): Promise<void> {
    if (await this.save(operations)) {
      this.resetForm();
      afterNextRender(() => this.pendingHeading()?.nativeElement.focus(), {
        injector: this.injector,
      });
    }
  }

  protected async check(): Promise<void> {
    const catalog = this.catalog();
    if (!catalog) {
      return;
    }

    await this.run(async () => {
      this.checkView.set(await this.catalogs.check(catalog.id));
      this.status.set('Check finished.');
    });
  }

  /** Applies the checked batch. A retry after an unknown outcome reuses the same command. */
  protected async apply(): Promise<void> {
    const catalog = this.catalog();
    const check = this.checkView();
    if (!catalog || !check || !this.canApply()) {
      return;
    }

    if (this.command?.draftVersion !== check.draftVersion) {
      this.command = { id: crypto.randomUUID(), draftVersion: check.draftVersion };
    }

    const command = this.command;

    await this.run(async () => {
      const result = await this.catalogs.apply(catalog.id, command.id, command.draftVersion);

      this.command = null;
      this.checkView.set(null);
      const applied = `Changes applied. The catalog is now at revision ${result.catalogRevision}.`;

      try {
        await this.reload(catalog.id);
        this.status.set(applied);
      } catch {
        this.status.set(`${applied} Refresh the page to see them.`);
      }
    });
  }

  private async save(operations: Operation[]): Promise<boolean> {
    const catalog = this.catalog();
    const draft = this.draft();
    if (!catalog || !draft) {
      return false;
    }

    return this.run(async () => {
      this.draft.set(
        await this.catalogs.saveDraft(catalog.id, draft, catalog.revision, operations),
      );
      this.checkView.set(null);
      this.status.set('Pending changes saved.');
    });
  }

  /**
   * Runs one request and explains any failure. When the draft or catalog changed elsewhere, or an
   * earlier apply already cleared the draft, the latest state is loaded so the page is current.
   */
  private async run(action: () => Promise<void>): Promise<boolean> {
    this.busy.set(true);
    this.status.set('Working…');
    this.details.set([]);

    try {
      await action();
      return true;
    } catch (error) {
      const problem = toProblem(error);
      this.details.set(problem.details);

      if (problem.status === 0) {
        this.status.set('The server could not be reached. Try again; a repeated apply is safe.');
        return false;
      }

      const outdated = problem.status === 409 || problem.code === 'EMPTY_DRAFT';
      this.status.set(outdated ? await this.reloadAfter(problem.message) : problem.message);
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async reloadAfter(message: string): Promise<string> {
    const catalogId = this.catalog()?.id;
    if (!catalogId) {
      return message;
    }

    try {
      this.checkView.set(null);
      await this.reload(catalogId);

      return `${message} The latest version has been loaded.`;
    } catch {
      return `${message} Refresh the page to load the latest version.`;
    }
  }

  private async reload(catalogId: string): Promise<void> {
    const [catalog, draft] = await Promise.all([
      this.catalogs.catalog(catalogId),
      this.catalogs.draft(catalogId),
    ]);

    this.catalog.set(catalog);
    this.draft.set(draft);
    this.resetForm();
  }

  private fillForm(
    mode: FormMode,
    source: string,
    kind: RelationshipKind,
    targets: string[],
  ): void {
    this.mode.set(mode);
    this.source.set(source);
    this.kind.set(kind);
    this.targets.set([...targets]);

    // Editing starts at the form, which may be far from the button that was pressed.
    if (mode.type !== 'new') {
      this.focusForm();
    }
  }

  private focusForm(): void {
    afterNextRender(() => this.sourceSelect()?.focus(), {
      injector: this.injector,
    });
  }
}
