import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type RelationshipKind = 'REQUIRES' | 'REQUIRED_WITH' | 'NOT_ALLOWED_WITH';

export interface Feature {
  id: string;
  code: string;
  name: string;
}

export interface Group {
  id: string;
  sourceFeatureId: string;
  kind: RelationshipKind;
  targetFeatureIds: string[];
}

export interface Catalog {
  id: string;
  name: string;
  readOnly: boolean;
  revision: number;
  features: Feature[];
  groups: Group[];
}

export interface CatalogSummary {
  id: string;
  name: string;
  kind: 'GUEST' | 'SHOWCASE';
  readOnly: boolean;
}

/** One staged change, in the shape the API stores it. */
export type Operation =
  | { type: 'CREATE'; sourceFeatureId: string; kind: RelationshipKind; targetFeatureIds: string[] }
  | { type: 'UPDATE'; groupId: string; targetFeatureIds: string[] }
  | { type: 'DELETE'; groupId: string };

export interface Draft {
  draftVersion: number;
  baseRevision: number;
  checked: boolean;
  operations: Operation[];
}

export interface EdgeChange {
  description: string;
}

export interface CheckResult {
  valid: boolean;
  added: EdgeChange[];
  removed: EdgeChange[];
  indirect: { description: string }[];
  blocking: { code: string; message: string }[];
  validBefore: number;
  validAfter: number;
}

export interface CheckView {
  draftVersion: number;
  revision: number;
  result: CheckResult;
}

export interface ApplyResult {
  catalogRevision: number;
  draftVersion: number;
}

export interface ConfigurationResult {
  valid: boolean;
  missing: { message: string }[];
  conflicts: { message: string }[];
}

/** A failed request, reduced to what the page shows. */
export interface ApiProblem {
  status: number;
  code: string;
  message: string;
  details: string[];
}

/** Turns any request failure into a problem the page can explain. */
export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    const body = (error.error ?? {}) as Partial<ApiProblem>;

    return {
      status: error.status,
      code: body.code ?? 'NETWORK',
      message: body.message ?? 'The server could not be reached. Try again.',
      details: body.details ?? [],
    };
  }

  return { status: 0, code: 'UNKNOWN', message: 'Something went wrong. Try again.', details: [] };
}

/** Calls the catalog API. */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  list(): Promise<CatalogSummary[]> {
    return firstValueFrom(this.http.get<CatalogSummary[]>('/api/catalogs'));
  }

  catalog(id: string): Promise<Catalog> {
    return firstValueFrom(this.http.get<Catalog>(`/api/catalogs/${id}`));
  }

  draft(id: string): Promise<Draft> {
    return firstValueFrom(this.http.get<Draft>(`/api/catalogs/${id}/draft`));
  }

  /** Replaces the pending changes if neither they nor the catalog changed elsewhere. */
  saveDraft(id: string, draft: Draft, revision: number, operations: Operation[]): Promise<Draft> {
    const body = {
      expectedDraftVersion: draft.draftVersion,
      expectedRevision: revision,
      operations,
    };

    return firstValueFrom(this.http.put<Draft>(`/api/catalogs/${id}/draft`, body));
  }

  check(id: string): Promise<CheckView> {
    return firstValueFrom(this.http.post<CheckView>(`/api/catalogs/${id}/draft/check`, {}));
  }

  apply(id: string, commandId: string, checkedDraftVersion: number): Promise<ApplyResult> {
    const body = { commandId, checkedDraftVersion };

    return firstValueFrom(this.http.post<ApplyResult>(`/api/catalogs/${id}/draft/apply`, body));
  }

  testConfiguration(id: string, featureIds: string[]): Promise<ConfigurationResult> {
    return firstValueFrom(
      this.http.post<ConfigurationResult>(`/api/catalogs/${id}/configurations/check`, {
        featureIds,
      }),
    );
  }
}
