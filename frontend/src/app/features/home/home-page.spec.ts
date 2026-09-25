import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HomePage } from './home-page';

describe('HomePage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideRouter([])],
    });
  });

  it('introduces the relationship workflow and offers two destinations', async () => {
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent?.trim()).toBe('Configuration Rule Engine');
    expect(compiled.textContent).toContain('Create, check, and apply feature relationships.');
    expect(compiled.querySelector('a[href="/workspace"]')?.textContent).toContain('Open workspace');
    expect(compiled.querySelector('a[href="/showcase"]')?.textContent).toContain(
      'Explore showcase',
    );
  });

  it('shows one sample rule of each relationship type', async () => {
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    const kinds = [...compiled.querySelectorAll('[data-kind]')].map((tag) =>
      tag.getAttribute('data-kind'),
    );

    expect(kinds).toEqual(['REQUIRES', 'REQUIRED_WITH', 'NOT_ALLOWED_WITH']);
  });
});
