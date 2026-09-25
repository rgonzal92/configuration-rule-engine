import { provideHttpClient } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { AppPreset } from './core/app-preset';
import { ThemeService } from './core/theme.service';

export const appConfig: ApplicationConfig = {
  // HttpClient echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header on writes by default.
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    providePrimeNG({
      theme: {
        preset: AppPreset,
        options: {
          darkModeSelector: '.app-dark',
          cssLayer: { name: 'primeng', order: 'theme, base, primeng, components, utilities' },
        },
      },
      ripple: true,
      license: typeof PRIMEUI_LICENSE_KEY === 'undefined' ? undefined : PRIMEUI_LICENSE_KEY,
    }),
    // Applies the saved or system theme before the first render.
    provideAppInitializer(() => void inject(ThemeService)),
  ],
};
