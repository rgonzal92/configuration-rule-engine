import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProposedRule } from '../../core/catalog.service';
import { RuleSuggestion } from './rule-suggestion';

describe('RuleSuggestion', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<RuleSuggestion>;
  let element: HTMLElement;
  let suggested: ProposedRule[];

  /** Lets the component's awaited request chain finish, then renders. */
  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function create(mode: 'OPENAI' | 'EXAMPLE'): Promise<void> {
    fixture = TestBed.createComponent(RuleSuggestion);
    fixture.componentRef.setInput('catalogId', 'c');
    element = fixture.nativeElement;
    suggested = [];
    fixture.componentInstance.suggested.subscribe((rule) => suggested.push(rule));
    fixture.detectChanges();

    http.expectOne('/api/suggestions/mode').flush({ mode });
    await settle();
  }

  async function ask(text: string): Promise<void> {
    const box = element.querySelector('textarea') as HTMLTextAreaElement;
    box.value = text;
    box.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    element.querySelector('button')?.click();
  }

  function status(): string {
    return element.querySelector('[role="status"]')?.textContent ?? '';
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RuleSuggestion],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('labels the example parser as not AI and lists what it understands', async () => {
    await create('EXAMPLE');

    expect(element.textContent).toContain('This example parser does not use AI.');
    expect(element.textContent).toContain('A requires B');
  });

  it('shows an example rule in the empty text box', async () => {
    await create('EXAMPLE');

    const box = element.querySelector('textarea') as HTMLTextAreaElement;
    expect(box.placeholder).toBe("For example: Fanless chassis can't be chosen with Dedicated GPU");
  });

  it('labels the live assistant', async () => {
    await create('OPENAI');

    expect(element.textContent).toContain('The AI assistant turns your description into a draft');
  });

  it('hands a suggestion to the form without staging it', async () => {
    await create('OPENAI');

    await ask('Touchscreen needs a stylus');
    const request = http.expectOne('/api/catalogs/c/suggestions');
    expect(request.request.body).toEqual({ text: 'Touchscreen needs a stylus' });

    const rule: ProposedRule = {
      sourceFeatureId: 'touch',
      kind: 'REQUIRES',
      targetFeatureIds: ['stylus'],
    };
    request.flush({ status: 'SUGGESTION', mode: 'OPENAI', rule });
    await settle();

    expect(suggested).toEqual([rule]);
    expect(status()).toContain('Review it, then stage it');
  });

  it('shows a question or an unavailable message as text only', async () => {
    await create('OPENAI');

    await ask('stylus please');
    http.expectOne('/api/catalogs/c/suggestions').flush({
      status: 'CLARIFICATION',
      mode: 'OPENAI',
      message: '<b>Which</b> feature needs the stylus?',
    });
    await settle();

    expect(suggested).toEqual([]);
    expect(status()).toContain('<b>Which</b> feature needs the stylus?');
    expect(element.querySelector('[role="status"] b')).toBeNull();
  });
});
