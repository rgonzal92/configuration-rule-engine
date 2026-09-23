import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('introduces the relationship workflow and offers two destinations', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toBe('Configuration Rule Engine');
    expect(compiled.textContent).toContain('Create, check, and apply feature relationships.');
    expect(compiled.querySelector('a[href="/workspace"]')?.textContent).toContain('Open workspace');
    expect(compiled.querySelector('a[href="/showcase"]')?.textContent).toContain(
      'Explore showcase',
    );
  });
});
