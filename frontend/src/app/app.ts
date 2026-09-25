import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Ripple } from 'primeng/ripple';
import { ThemeService } from './core/theme.service';

/**
 * Provides the shared application shell, primary navigation, theme toggle, and links to the source
 * code and the author profile.
 */
@Component({
  imports: [ButtonDirective, Ripple, RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {
  protected readonly theme = inject(ThemeService);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  /**
   * Moves focus to the main content in place. The page's base URL would otherwise turn the
   * "#main" link into a navigation to the home page.
   */
  protected skipToMain(event: MouseEvent): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }
}
