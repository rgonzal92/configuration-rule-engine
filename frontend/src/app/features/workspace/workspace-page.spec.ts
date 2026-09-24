import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WorkspacePage } from './workspace-page';

describe('WorkspacePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WorkspacePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(session: object) {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();

    http.expectOne('/api/session').flush(session);
    await fixture.whenStable();
    fixture.detectChanges();

    return fixture;
  }

  it('offers to start a guest workspace', async () => {
    const fixture = await render({ status: 'ANONYMOUS' });

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.textContent).toContain('Start guest workspace');

    button.click();
    http
      .expectOne('/api/demo/sessions')
      .flush({ status: 'GUEST', workspaceId: 'w1', expiresAt: '2026-01-01T04:00:00Z' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Your private workspace is available until',
    );
  });

  it('explains an expired workspace and offers a new one', async () => {
    const fixture = await render({ status: 'EXPIRED' });

    expect(fixture.nativeElement.textContent).toContain('Your guest workspace expired');
    expect(fixture.nativeElement.querySelector('button').textContent).toContain(
      'Start a new guest workspace',
    );
  });

  it('announces only the status text and keeps buttons outside the live region', async () => {
    const fixture = await render({ status: 'ANONYMOUS' });

    const status = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(status.textContent).toContain('private laptop catalog');
    expect(status.querySelector('button')).toBeNull();
  });

  it('moves focus to the new status after starting', async () => {
    const fixture = await render({ status: 'ANONYMOUS' });
    document.body.appendChild(fixture.nativeElement);

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.focus();
    button.click();

    http
      .expectOne('/api/demo/sessions')
      .flush({ status: 'GUEST', workspaceId: 'w1', expiresAt: '2026-01-01T04:00:00Z' });
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();

    const status = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(document.activeElement).toBe(status);
    expect(status.textContent).toContain('Your private workspace is available until');
  });
});
