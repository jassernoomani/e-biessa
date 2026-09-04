import { ChangeDetectionStrategy, Component, Input, computed, inject } from '@angular/core';
import { ThemeService } from '../core/theme.service';

export type BrandLogoVariant = 'header' | 'footer' | 'auth';

@Component({
  selector: 'app-brand-logo',
  template: `<img [class]="'brand-logo brand-logo-' + variant" [src]="source()" alt="E-biessa">`,
  host: { style: 'display: contents' },
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandLogoComponent {
  private readonly theme = inject(ThemeService);

  @Input({ required: true }) variant!: BrandLogoVariant;

  readonly source = computed(() => this.theme.darkMode()
    ? '/e-biessa-logo-dark.png'
    : '/e-biessa-logo.png');
}
