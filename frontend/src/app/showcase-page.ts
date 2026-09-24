import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Shows the shared public examples, which no visitor can change. */
@Component({
  selector: 'app-showcase-page',
  template: `
    <section aria-labelledby="showcase-heading">
      <h2 id="showcase-heading">Public catalog examples</h2>
      <p aria-live="polite">
        @switch (loaded()) {
          @case (true) {
            These examples are read-only. Start a guest workspace to make your own changes.
          }
          @case (false) {
            The examples could not be loaded. Refresh the page to try again.
          }
          @default {
            Loading examples…
          }
        }
      </p>
    </section>
  `,
})
export class ShowcasePage implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly loaded = signal<boolean | undefined>(undefined);

  async ngOnInit(): Promise<void> {
    try {
      await firstValueFrom(this.http.get('/api/showcase'));
      this.loaded.set(true);
    } catch {
      this.loaded.set(false);
    }
  }
}
