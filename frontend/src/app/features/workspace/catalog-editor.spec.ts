import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CatalogEditor } from './catalog-editor';
import { Catalog, Draft, toProblem } from '../../core/catalog.service';

const catalog: Catalog = {
  id: 'c',
  name: 'Laptop',
  readOnly: false,
  revision: 1,
  features: [
    { id: 'gpu', code: 'GPU', name: 'Dedicated GPU' },
    { id: 'charger', code: 'CHARGER', name: 'High-wattage charger' },
    { id: 'touch', code: 'TOUCH', name: 'Touchscreen' },
    { id: 'stylus', code: 'STYLUS', name: 'Stylus support' },
  ],
  groups: [{ id: 'g1', sourceFeatureId: 'gpu', kind: 'REQUIRES', targetFeatureIds: ['charger'] }],
};

const emptyDraft: Draft = { draftVersion: 0, baseRevision: 1, checked: false, operations: [] };

const touchDraft: Draft = {
  draftVersion: 1,
  baseRevision: 1,
  checked: false,
  operations: [
    { type: 'CREATE', sourceFeatureId: 'touch', kind: 'REQUIRES', targetFeatureIds: ['stylus'] },
  ],
};

const validCheck = {
  draftVersion: 1,
  revision: 1,
  result: {
    valid: true,
    added: [{ description: 'Choosing Touchscreen also requires Stylus support' }],
    removed: [],
    indirect: [],
    blocking: [],
    validBefore: 12,
    validAfter: 9,
  },
};

describe('CatalogEditor', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CatalogEditor>;
  let element: HTMLElement;

  /** Lets the component's awaited request chain finish, then renders. */
  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function flushLoad(current: Catalog, draft: Draft): Promise<void> {
    await settle();
    http.expectOne('/api/catalogs/c').flush(current);
    http.expectOne('/api/catalogs/c/draft').flush(draft);
    await settle();
  }

  function button(name: string): HTMLButtonElement {
    const match = [...element.querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === name,
    );

    return match as HTMLButtonElement;
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CatalogEditor],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(CatalogEditor);
    element = fixture.nativeElement;
    fixture.detectChanges();

    http.expectOne('/api/catalogs').flush([
      { id: 'c', name: 'Laptop', kind: 'GUEST', readOnly: false },
      { id: 's', name: 'Laptop', kind: 'SHOWCASE', readOnly: true },
    ]);
    await flushLoad(catalog, emptyDraft);
  });

  afterEach(() => http.verify());

  it('explains the active relationships', () => {
    expect(element.textContent).toContain(
      'Choosing Dedicated GPU also requires High-wattage charger.',
    );
    expect(element.textContent).toContain('No pending changes.');
  });

  it('stages a relationship from the form with the expected versions', async () => {
    const [sourceSelect] = element.querySelectorAll('select');
    sourceSelect.value = 'touch';
    sourceSelect.dispatchEvent(new Event('change'));
    await settle();

    const stylus = [...element.querySelectorAll('label.choice')]
      .filter((label) => label.closest('form'))
      .find((label) => label.textContent?.includes('Stylus support'));
    stylus?.querySelector('input')?.click();
    button('Stage change').click();

    const request = http.expectOne('/api/catalogs/c/draft');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      expectedDraftVersion: 0,
      expectedRevision: 1,
      operations: touchDraft.operations,
    });

    request.flush(touchDraft);
    await settle();
    expect(element.textContent).toContain(
      'Add: Choosing Touchscreen also requires Stylus support.',
    );
  });

  it('enables apply only after a valid check of the current draft', async () => {
    await reloadWith(touchDraft);
    expect(button('Apply changes').disabled).toBe(true);

    button('Check pending changes').click();
    http.expectOne('/api/catalogs/c/draft/check').flush(validCheck);
    await settle();

    expect(element.textContent).toContain('Valid configurations: 12 → 9');
    expect(button('Apply changes').disabled).toBe(false);
  });

  it('reuses the same command when an apply is retried after a lost response', async () => {
    await reloadWith(touchDraft);
    button('Check pending changes').click();
    http.expectOne('/api/catalogs/c/draft/check').flush(validCheck);
    await settle();

    button('Apply changes').click();
    const first = http.expectOne('/api/catalogs/c/draft/apply');
    first.error(new ProgressEvent('error'));
    await settle();
    expect(element.textContent).toContain('a repeated apply is safe');

    button('Apply changes').click();
    const retry = http.expectOne('/api/catalogs/c/draft/apply');
    expect(retry.request.body).toEqual(first.request.body);

    retry.flush({ catalogRevision: 2, draftVersion: 2, added: [], removed: [] });
    await flushLoad(
      { ...catalog, revision: 2 },
      { ...emptyDraft, draftVersion: 2, baseRevision: 2 },
    );
    expect(element.textContent).toContain('The catalog is now at revision 2.');
  });

  it('reloads when the draft changed in another tab', async () => {
    button('Remove').click();
    http.expectOne('/api/catalogs/c/draft').flush(
      {
        code: 'DRAFT_CONFLICT',
        message: 'The pending changes were edited elsewhere; reload them',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await flushLoad(catalog, touchDraft);
    expect(element.textContent).toContain('The latest version has been loaded.');
    expect(element.textContent).toContain(
      'Add: Choosing Touchscreen also requires Stylus support.',
    );
  });

  it('loads a pending new relationship into the form for editing', async () => {
    await reloadWith(touchDraft);

    const edit = element.querySelector<HTMLButtonElement>('button[aria-label^="Edit pending"]');
    edit?.click();
    await settle();

    expect(element.querySelector('form h4')?.textContent).toContain('Edit relationship');
    const stylus = [
      ...element.querySelectorAll<HTMLInputElement>('form input[type="checkbox"]'),
    ].find((input) => input.parentElement?.textContent?.includes('Stylus support'));
    expect(stylus?.checked).toBe(true);
  });

  it('offers no second change for a relationship that already has one pending', async () => {
    await reloadWith({
      ...emptyDraft,
      draftVersion: 1,
      operations: [{ type: 'DELETE', groupId: 'g1' }],
    });

    expect(element.textContent).toContain('change pending');
    expect(element.querySelector('button[aria-label^="Edit: "]')).toBeNull();
    expect(element.querySelector('button[aria-label^="Remove: "]')).toBeNull();
  });

  it('resets the form when a conflict reloads the draft', async () => {
    await reloadWith(touchDraft);
    element.querySelector<HTMLButtonElement>('button[aria-label^="Edit pending"]')?.click();
    await settle();
    expect(element.querySelector('form h4')?.textContent).toContain('Edit relationship');

    button('Check pending changes').click();
    http
      .expectOne('/api/catalogs/c/draft/check')
      .flush({ code: 'STALE_DRAFT', message: 'Stale.' }, { status: 409, statusText: 'Conflict' });
    await flushLoad(catalog, emptyDraft);

    expect(element.querySelector('form h4')?.textContent).toContain('Stage a relationship');
    expect(element.textContent).toContain('Stale. The latest version has been loaded.');
  });

  it('closes the edit form when a pending change is undone', async () => {
    await reloadWith({
      ...touchDraft,
      operations: [...touchDraft.operations, { type: 'DELETE', groupId: 'g1' }],
    });
    element.querySelector<HTMLButtonElement>('button[aria-label^="Edit pending"]')?.click();
    await settle();

    element.querySelector<HTMLButtonElement>('button[aria-label^="Undo: Add"]')?.click();
    http
      .expectOne('/api/catalogs/c/draft')
      .flush({ ...emptyDraft, draftVersion: 2, operations: [{ type: 'DELETE', groupId: 'g1' }] });
    await settle();

    expect(element.querySelector('form h4')?.textContent).toContain('Stage a relationship');
  });

  describe('toProblem', () => {
    it('keeps the server error body', () => {
      const error = new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_DRAFT', message: 'Not valid', details: ['Change 1: bad'] },
      });

      expect(toProblem(error)).toEqual({
        status: 400,
        code: 'INVALID_DRAFT',
        message: 'Not valid',
        details: ['Change 1: bad'],
      });
    });

    it('explains an unreachable server and unexpected failures', () => {
      expect(toProblem(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
      expect(toProblem(new Error('boom')).code).toBe('UNKNOWN');
    });
  });

  async function reloadWith(draft: Draft): Promise<void> {
    button('Remove').click();
    http
      .expectOne('/api/catalogs/c/draft')
      .flush(
        { code: 'DRAFT_CONFLICT', message: 'reload' },
        { status: 409, statusText: 'Conflict' },
      );

    await flushLoad(catalog, draft);
  }
});
