import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Inline the single artwork source so its annotations inherit the active theme. */
@Component({
  selector: 'app-brake-assembly',
  templateUrl: '../../../public/brake-assembly.svg',
  styles: [
    ':host { display: block; width: 100%; aspect-ratio: 560 / 410; } svg { display: block; width: 100%; height: auto; }',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrakeAssemblyComponent {}
