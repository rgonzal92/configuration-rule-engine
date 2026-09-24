import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

/** What the browser knows about the visitor's guest workspace. */
export type SessionState =
  | { kind: 'loading' }
  | { kind: 'anonymous' }
  | { kind: 'guest'; workspaceId: string; expiresAt: string }
  | { kind: 'expired' }
  | { kind: 'limit' }
  | { kind: 'error' };

interface SessionResponse {
  status: 'ANONYMOUS' | 'GUEST' | 'EXPIRED';
  workspaceId?: string;
  expiresAt?: string;
}

/** Loads and starts the visitor's server-side guest session. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  readonly state = signal<SessionState>({ kind: 'loading' });

  /** Also receives the CSRF cookie that later writes echo back. */
  load(): Promise<void> {
    return this.track(this.http.get<SessionResponse>('/api/session'));
  }

  start(): Promise<void> {
    this.state.set({ kind: 'loading' });

    return this.track(this.http.post<SessionResponse>('/api/demo/sessions', null));
  }

  private async track(request: Observable<SessionResponse>): Promise<void> {
    try {
      this.state.set(toState(await firstValueFrom(request)));
    } catch (error) {
      const limited = error instanceof HttpErrorResponse && error.status === 429;
      this.state.set({ kind: limited ? 'limit' : 'error' });
    }
  }
}

function toState(response: SessionResponse): SessionState {
  switch (response.status) {
    case 'GUEST':
      return {
        kind: 'guest',
        workspaceId: response.workspaceId ?? '',
        expiresAt: response.expiresAt ?? '',
      };

    case 'EXPIRED':
      return { kind: 'expired' };

    default:
      return { kind: 'anonymous' };
  }
}
