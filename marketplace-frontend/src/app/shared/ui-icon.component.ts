import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

type IconName =
  | 'search'
  | 'microphone'
  | 'sun'
  | 'moon'
  | 'heart'
  | 'close'
  | 'camera'
  | 'arrow-left'
  | 'pin'
  | 'shield'
  | 'truck'
  | 'tag'
  | 'fuel'
  | 'crosshair'
  | 'check';

/** One stroke vocabulary for shared marketplace controls. */
@Component({
  selector: 'app-ui-icon',
  template: `<svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path [attr.d]="paths[name]" />
  </svg>`,
  styles: [
    ':host { display: inline-flex; width: 20px; height: 20px; flex-shrink: 0; vertical-align: middle; } svg { width: 100%; height: 100%; }',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiIconComponent {
  @Input({ required: true }) name!: IconName;
  readonly paths: Record<IconName, string> = {
    'arrow-left': 'M19 12H5M12 5l-7 7 7 7',
    search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
    microphone: 'M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0ZM5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8',
    sun: 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5',
    moon: 'M20.5 14.3A9 9 0 0 1 9.7 3.5 9 9 0 1 0 20.5 14.3Z',
    heart:
      'M20.8 4.8a5.5 5.5 0 0 0-7.8 0L12 6l-1-1.2a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.4a5.5 5.5 0 0 0 0-7.8Z',
    close: 'M6 6l12 12M6 18 18 6',
    camera: 'M3 6h4l2-3h6l2 3h4v14H3ZM16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
    pin: 'M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12ZM14.5 9a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0',
    shield: 'M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6ZM8.5 12l2.5 2.5 4.5-5',
    truck:
      'M2 6h11v10H2ZM13 9h4l4 4v3h-8M7.5 18.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0M18.5 18.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0',
    tag: 'M3 12V3h9l9 9-9 9ZM8 8h.01',
    fuel: 'M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M4 10h10M14 8l3 3v6a1.5 1.5 0 0 0 3 0V8l-3-3',
    crosshair: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M12 3v4M12 17v4M3 12h4M17 12h4',
    check: 'M5 12.5l4.5 4.5L19 7',
  };
}
