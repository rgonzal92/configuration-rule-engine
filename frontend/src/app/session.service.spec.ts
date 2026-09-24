import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionService } from './session.service';

describe('SessionService', () => {
  let service: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts loading and reports an anonymous visitor', async () => {
    expect(service.state()).toEqual({ kind: 'loading' });
    const loaded = service.load();
    http.expectOne('/api/session').flush({ status: 'ANONYMOUS' });
    await loaded;
    expect(service.state()).toEqual({ kind: 'anonymous' });
  });

  it('reports a live guest workspace', async () => {
    const loaded = service.load();
    http
      .expectOne('/api/session')
      .flush({ status: 'GUEST', workspaceId: 'w1', expiresAt: '2026-01-01T04:00:00Z' });
    await loaded;
    expect(service.state()).toEqual({
      kind: 'guest',
      workspaceId: 'w1',
      expiresAt: '2026-01-01T04:00:00Z',
    });
  });

  it('reports an expired workspace', async () => {
    const loaded = service.load();
    http.expectOne('/api/session').flush({ status: 'EXPIRED' });
    await loaded;
    expect(service.state()).toEqual({ kind: 'expired' });
  });

  it('starts a guest workspace with a POST', async () => {
    const started = service.start();
    const request = http.expectOne('/api/demo/sessions');
    expect(request.request.method).toBe('POST');
    request.flush({ status: 'GUEST', workspaceId: 'w2', expiresAt: '2026-01-01T04:00:00Z' });
    await started;
    expect(service.state()).toMatchObject({ kind: 'guest', workspaceId: 'w2' });
  });

  it('reports the shared creation limit', async () => {
    const started = service.start();
    http
      .expectOne('/api/demo/sessions')
      .flush({ code: 'GUEST_LIMIT_REACHED' }, { status: 429, statusText: 'Too Many Requests' });
    await started;
    expect(service.state()).toEqual({ kind: 'limit' });
  });

  it('reports other failures as errors', async () => {
    const loaded = service.load();
    http.expectOne('/api/session').flush(null, { status: 500, statusText: 'Server Error' });
    await loaded;
    expect(service.state()).toEqual({ kind: 'error' });
  });
});
