import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ShowcasePage } from './showcase-page';

describe('ShowcasePage', () => {
  it('shows each read-only catalog with its relationships explained', async () => {
    TestBed.configureTestingModule({
      imports: [ShowcasePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ShowcasePage);
    fixture.detectChanges();

    http.expectOne('/api/catalogs').flush([
      { id: 'mine', name: 'Laptop', kind: 'GUEST', readOnly: false },
      { id: 'car', name: 'Automotive (fictional)', kind: 'SHOWCASE', readOnly: true },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectOne('/api/catalogs/car').flush({
      id: 'car',
      name: 'Automotive (fictional)',
      readOnly: true,
      revision: 1,
      features: [
        { id: 'tow', code: 'TOW', name: 'Tow package' },
        { id: 'cool', code: 'COOL', name: 'Heavy-duty cooling' },
      ],
      groups: [{ id: 'g', sourceFeatureId: 'tow', kind: 'REQUIRES', targetFeatureIds: ['cool'] }],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('These examples are read-only.');
    expect(text).toContain('Choosing Tow package also requires Heavy-duty cooling.');
    expect(fixture.nativeElement.querySelector('button[aria-label^="Edit"]')).toBeNull();

    http.verify();
  });
});
