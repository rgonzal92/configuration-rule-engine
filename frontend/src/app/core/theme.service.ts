import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'cre-theme';
const DARK_CLASS = 'app-dark';
const SWITCHING_CLASS = 'theme-switching';

/**
 * Switches between the light and dark themes. It starts from the visitor's saved choice, or from
 * the operating system setting when there is none, and marks the page root with the class that
 * both PrimeNG and Tailwind use for dark styles.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly dark = signal(this.initialDark());

  constructor() {
    this.apply();
  }

  toggle(): void {
    this.dark.update((dark) => !dark);
    this.apply();

    // A blocked or full storage only means the choice is not remembered.
    try {
      localStorage.setItem(STORAGE_KEY, this.dark() ? 'dark' : 'light');
    } catch {
      // Keep the current page's choice without saving it.
    }
  }

  /**
   * Hundreds of PrimeNG elements transition their colors, which would stall the first frame after
   * a switch and show stale colors until it ends. Transitions are paused while the new colors are
   * computed, then allowed again for later hover and focus changes.
   */
  private apply(): void {
    const root = document.documentElement;

    root.classList.add(SWITCHING_CLASS);
    root.classList.toggle(DARK_CLASS, this.dark());
    // Reading a computed style makes the browser apply the new colors now, while transitions are off.
    getComputedStyle(root).getPropertyValue('color');
    setTimeout(() => root.classList.remove(SWITCHING_CLASS));
  }

  private initialDark(): boolean {
    let saved: string | null = null;
    try {
      saved = localStorage.getItem(STORAGE_KEY);
    } catch {
      // Storage can be blocked; fall back to the system setting.
    }

    if (saved === 'dark' || saved === 'light') {
      return saved === 'dark';
    }

    // Environments without media queries, such as tests, start light.
    return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches;
  }
}
