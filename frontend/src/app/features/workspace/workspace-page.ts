import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, inject, viewChild } from '@angular/core';
import { SessionService } from '../../core/session.service';

/** Starts or resumes the visitor's private guest workspace. */
@Component({
  imports: [DatePipe],
  selector: 'app-workspace-page',
  template: `
    <section aria-labelledby="workspace-heading">
      <h2 id="workspace-heading">Your workspace</h2>
      @let state = session.state();
      <p #status role="status" tabindex="-1">
        @switch (state.kind) {
          @case ('loading') {
            Checking your workspace…
          }
          @case ('anonymous') {
            A guest workspace gives you a private laptop catalog for four hours.
          }
          @case ('guest') {
            @let until = state.expiresAt | date: 'shortTime';
            Your private workspace is available until
            <time [attr.datetime]="state.expiresAt">{{ until }}</time>
          }
          @case ('expired') {
            Your guest workspace expired, and its changes were removed.
          }
          @case ('limit') {
            Guest workspaces are busy right now. Please try again later.
          }
          @case ('error') {
            The workspace could not be loaded. Refresh the page to try again.
          }
        }
      </p>
      @if (state.kind === 'anonymous') {
        <button type="button" (click)="start()">Start guest workspace</button>
      } @else if (state.kind === 'expired') {
        <button type="button" (click)="start()">Start a new guest workspace</button>
      }
    </section>
  `,
})
export class WorkspacePage implements OnInit {
  protected readonly session = inject(SessionService);

  private readonly status = viewChild.required<ElementRef<HTMLElement>>('status');

  ngOnInit(): void {
    void this.session.load();
  }

  /** The clicked button disappears, so focus moves to the status that replaces it. */
  protected async start(): Promise<void> {
    await this.session.start();

    this.status().nativeElement.focus();
  }
}
