import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import {
  CatalogService,
  ProposedRule,
  SuggestionMode,
  toProblem,
} from '../../core/catalog.service';

/**
 * Reads one relationship from plain English and hands it to the form for review. It never stages
 * anything, and every message it shows is plain text.
 */
@Component({
  selector: 'app-rule-suggestion',
  template: `
    <section class="panel" aria-labelledby="suggestion-heading">
      <h4 id="suggestion-heading">Describe a rule</h4>
      <p class="help">
        @switch (mode()) {
          @case ('OPENAI') {
            AI assistant. It proposes a relationship for you to review; nothing changes until you
            stage it.
          }
          @case ('EXAMPLE') {
            Example parser, not AI. It understands only "A requires B", "A is required with B", and
            "A can't be chosen with B", using exact feature names.
          }
          @default {
            Loading the assistant…
          }
        }
      </p>
      <label class="field">
        Rule in plain English
        <textarea
          rows="2"
          maxlength="500"
          [value]="text()"
          (input)="text.set($any($event.target).value)"
        ></textarea>
      </label>
      <button type="button" [disabled]="busy() || !text().trim()" (click)="suggest()">
        Suggest
      </button>
      <p role="status">{{ outcome() }}</p>
    </section>
  `,
})
export class RuleSuggestion implements OnInit {
  private readonly catalogs = inject(CatalogService);

  readonly catalogId = input.required<string>();
  readonly suggested = output<ProposedRule>();

  protected readonly mode = signal<SuggestionMode | null>(null);
  protected readonly text = signal('');
  protected readonly busy = signal(false);
  protected readonly outcome = signal('');

  async ngOnInit(): Promise<void> {
    try {
      this.mode.set(await this.catalogs.suggestionMode());
    } catch {
      this.outcome.set('The assistant could not be loaded. You can still use the form.');
    }
  }

  protected async suggest(): Promise<void> {
    this.busy.set(true);
    this.outcome.set('Reading your rule…');

    try {
      const answer = await this.catalogs.suggest(this.catalogId(), this.text().trim());

      if (answer.status === 'SUGGESTION' && answer.rule) {
        this.suggested.emit(answer.rule);
        const note = answer.message ? `${answer.message} ` : '';
        this.outcome.set(
          `${note}The form below now holds the suggestion. Review it, then stage it.`,
        );
      } else {
        this.outcome.set(answer.message ?? 'No suggestion. You can still use the form.');
      }
    } catch (error) {
      this.outcome.set(toProblem(error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
