import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let prefersDark: boolean;

  function service(): ThemeService {
    return TestBed.inject(ThemeService);
  }

  beforeEach(() => {
    prefersDark = false;
    localStorage.clear();
    document.documentElement.classList.remove('app-dark');
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query === '(prefers-color-scheme: dark)' && prefersDark,
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('follows the system setting when nothing was chosen', () => {
    prefersDark = true;

    expect(service().dark()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
  });

  it('prefers a saved choice over the system setting', () => {
    prefersDark = true;
    localStorage.setItem('cre-theme', 'light');

    expect(service().dark()).toBe(false);
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);
  });

  it('toggles the theme and remembers the choice', () => {
    const theme = service();

    theme.toggle();

    expect(theme.dark()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
    expect(localStorage.getItem('cre-theme')).toBe('dark');
  });

  it('switches colors without transitions, then allows them again', async () => {
    const theme = service();
    const root = document.documentElement;

    theme.toggle();

    expect(root.classList).toContain('app-dark');
    expect(root.classList).toContain('theme-switching');

    await new Promise((resolve) => setTimeout(resolve));

    expect(root.classList).not.toContain('theme-switching');
  });

  it('still works when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    const theme = service();
    theme.toggle();

    expect(theme.dark()).toBe(true);
  });
});
