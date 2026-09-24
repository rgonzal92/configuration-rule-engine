import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ShowcasePage } from './showcase-page';

describe('ShowcasePage', () => {
  it('explains that the public examples are read-only', async () => {
    TestBed.configureTestingModule({
      imports: [ShowcasePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ShowcasePage);
    fixture.detectChanges();

    http.expectOne('/api/showcase').flush({ workspaceId: 's', readOnly: true });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Public catalog examples');
    expect(fixture.nativeElement.textContent).toContain('read-only');

    http.verify();
  });
});
