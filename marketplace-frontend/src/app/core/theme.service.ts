import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

const THEME_STORAGE_KEY = 'e_bieca_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);

  readonly darkMode = signal(this.initialDarkMode());

  constructor() {
    this.applyTheme();
  }

  toggle(): void {
    this.darkMode.update(value => !value);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(THEME_STORAGE_KEY, this.darkMode() ? 'dark' : 'light');
    }
    this.applyTheme();
  }

  private initialDarkMode(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    if (saved) return saved === 'dark';
    return !!window.matchMedia?.('(prefers-color-scheme: dark)').matches;
  }

  private applyTheme(): void {
    this.document.documentElement.dataset['theme'] = this.darkMode() ? 'dark' : 'light';
  }
}
