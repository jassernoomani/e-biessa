import { TestBed } from '@angular/core/testing';
import { ThemeService } from '../core/theme.service';
import { BrandLogoComponent } from './brand-logo.component';

describe('BrandLogoComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem('e_bieca_theme', 'light');
    await TestBed.configureTestingModule({ imports: [BrandLogoComponent] }).compileComponents();
  });

  it('renders the requested logo size with the light asset', () => {
    const fixture = TestBed.createComponent(BrandLogoComponent);
    fixture.componentRef.setInput('variant', 'header');
    fixture.detectChanges();

    const image = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(image.classList.contains('brand-logo-header')).toBe(true);
    expect(image.getAttribute('src')).toBe('/e-biessa-logo.png');
    expect(image.alt).toBe('E-biessa');
  });

  it('switches assets reactively without changing its layout class', () => {
    const fixture = TestBed.createComponent(BrandLogoComponent);
    fixture.componentRef.setInput('variant', 'auth');
    fixture.detectChanges();

    TestBed.inject(ThemeService).toggle();
    fixture.detectChanges();

    const image = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(image.classList.contains('brand-logo-auth')).toBe(true);
    expect(image.getAttribute('src')).toBe('/e-biessa-logo-dark.png');
  });
});
