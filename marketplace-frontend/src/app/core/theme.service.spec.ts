import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset['theme'];
  });

  it('restores and applies a saved theme', () => {
    localStorage.setItem('e_bieca_theme', 'dark');
    const service = TestBed.inject(ThemeService);

    expect(service.darkMode()).toBe(true);
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('toggles and persists the selected theme', () => {
    localStorage.setItem('e_bieca_theme', 'light');
    const service = TestBed.inject(ThemeService);

    service.toggle();

    expect(service.darkMode()).toBe(true);
    expect(localStorage.getItem('e_bieca_theme')).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });
});
