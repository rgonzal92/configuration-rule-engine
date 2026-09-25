import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { Textarea } from 'primeng/textarea';
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
  imports: [ButtonDirective, Ripple, Textarea],
  selector: 'app-rule-suggestion',
  template: `
    <section aria-labelledby="suggestion-heading">
      <h3 id="suggestion-heading" class="font-semibold">Describe a rule</h3>
      <p class="help">
        @switch (mode()) {
          @case ('OPENAI') {
            The AI assistant turns your description into a draft relationship in the form below.
            Review the draft before staging it; nothing changes until you do.
          }
          @case ('EXAMPLE') {
            This example parser does not use AI. It recognizes exact feature names in three
            patterns: "A requires B", "A is required with B", and "A can't be chosen with B".
          }
          @default {
            Loading the assistant…
          }
        }
      </p>
      <label class="field">
        Rule in plain English
        <textarea
          pTextarea
          class="w-full font-normal"
          rows="2"
          maxlength="500"
          placeholder="For example: Fanless chassis can't be chosen with Dedicated GPU"
          [value]="text()"
          (input)="text.set($any($event.target).value)"
        ></textarea>
      </label>
      <button
        pButton
        pRipple
        type="button"
        class="min-h-11"
        [disabled]="busy() || !text().trim()"
        (click)="suggest()"
      >
        Suggest
      </button>
      <p role="status" class="mt-3">{{ outcome() }}</p>
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
