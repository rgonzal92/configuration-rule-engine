import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: 'showcase', children: [] }])],
    }).compileComponents();
  });

  it('links to both pages and marks the current one', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    const workspace = compiled.querySelector('nav a[href="/workspace"]');
    const showcase = compiled.querySelector('nav a[href="/showcase"]');
    expect(workspace?.textContent).toContain('Workspace');
    expect(showcase?.textContent).toContain('Showcase');

    await TestBed.inject(Router).navigateByUrl('/showcase');
    await fixture.whenStable();

    expect(showcase?.getAttribute('aria-current')).toBe('page');
    expect(workspace?.hasAttribute('aria-current')).toBe(false);
  });

  it('shows the logo beside the app name without announcing it twice', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const home = (fixture.nativeElement as HTMLElement).querySelector('header a[href="/"]');
    const logo = home?.querySelector('img');

    expect(logo?.getAttribute('src')).toBe('logo.png');
    expect(logo?.getAttribute('alt')).toBe('');
    expect(home?.textContent?.trim()).toBe('Configuration Rule Engine');
  });

  it('offers a skip link to the main content first', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    const first = compiled.querySelector('a, button') as HTMLAnchorElement;
    expect(first.textContent?.trim()).toBe('Skip to main content');
    expect(first.getAttribute('href')).toBe('#main');
    expect(compiled.querySelector('main#main')?.getAttribute('tabindex')).toBe('-1');
  });

  it('moves focus to the main content without leaving the page', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    document.body.appendChild(compiled);

    const skip = compiled.querySelector('a[href="#main"]') as HTMLAnchorElement;
    const click = new MouseEvent('click', { bubbles: true, cancelable: true });
    skip.dispatchEvent(click);

    expect(click.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(compiled.querySelector('main'));
    compiled.remove();
  });

  it('links to the source code and the author profile in a new tab', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    const github = compiled.querySelector(
      'a[href="https://github.com/rgonzal92/configuration-rule-engine"]',
    );
    const linkedin = compiled.querySelector('a[href="https://www.linkedin.com/in/rgonzal92"]');

    for (const link of [github, linkedin]) {
      expect(link?.getAttribute('target')).toBe('_blank');
      expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
      expect(link?.getAttribute('aria-label')).toContain('opens in a new tab');
    }
    expect(github?.getAttribute('aria-label')).toBe('Source code on GitHub (opens in a new tab)');
  });

  it('gives the header links and buttons a ripple effect', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('header [pButton]');

    expect(buttons.length).toBe(5);
    buttons.forEach((button) => expect(button.classList).toContain('p-ripple'));
  });

  it('switches between light and dark themes with a pressed toggle', async () => {
    localStorage.setItem('cre-theme', 'light');
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const toggle = (fixture.nativeElement as HTMLElement).querySelector(
      'button[aria-label="Dark theme"]',
    ) as HTMLButtonElement;

    expect(toggle.getAttribute('aria-pressed')).toBe('false');

    toggle.click();
    await fixture.whenStable();

    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);

    toggle.click();
    await fixture.whenStable();
    localStorage.clear();
  });
});
