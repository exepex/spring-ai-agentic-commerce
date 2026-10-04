import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Stroke paths on a 24 by 24 grid, drawn in the current text colour. */
const ICON_PATHS = {
  activity: ['M22 12h-4l-3 9L9 3l-3 9H2'],
  alert: [
    'M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
    'M12 9v4',
    'M12 17h.01',
  ],
  'arrow-left': ['M19 12H5', 'M12 19l-7-7 7-7'],
  bag: ['M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z', 'M3 6h18', 'M16 10a4 4 0 0 1-8 0'],
  bot: [
    'M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z',
    'M12 8V4H8',
    'M2 14h2',
    'M20 14h2',
    'M9 13v2',
    'M15 13v2',
  ],
  bottle: [
    'M10 2h4',
    'M10 2v3.5L8.5 8A3 3 0 0 0 8 9.7V20a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2V9.7a3 3 0 0 0-.5-1.7L14 5.5V2',
    'M8 13h8',
  ],
  card: ['M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z', 'M2 10h20'],
  check: ['M20 6 9 17l-5-5'],
  'chevron-right': ['M9 18l6-6-6-6'],
  clock: ['M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z', 'M12 6v6l4 2'],
  external: [
    'M15 3h6v6',
    'M10 14 21 3',
    'M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6',
  ],
  headlamp: [
    'M12 9a4 4 0 1 0 0 8 4 4 0 0 0 0-8z',
    'M2 13h3',
    'M19 13h3',
    'M12 2v3',
    'M5 6l2 2',
    'M19 6l-2 2',
  ],
  jacket: ['M8 3 4 6v15h5v-8', 'M16 3l4 3v15h-5v-8', 'M8 3l4 4 4-4', 'M12 7v14'],
  lifebuoy: [
    'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z',
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z',
    'M4.9 4.9l4.3 4.3',
    'M14.8 14.8l4.3 4.3',
    'M14.8 9.2l4.3-4.3',
    'M4.9 19.1l4.3-4.3',
  ],
  mail: [
    'M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
    'M22 6l-10 7L2 6',
  ],
  mountain: ['M8 3l4 8 5-5 5 15H2L8 3z'],
  package: [
    'M21 16V8a2 2 0 0 0-1-1.7l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.7l7 4a2 2 0 0 0 2 0l7-4a2 2 0 0 0 1-1.7z',
    'M3.3 7 12 12l8.7-5',
    'M12 22V12',
  ],
  receipt: [
    'M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1z',
    'M16 8H8',
    'M16 12H8',
    'M13 16H8',
  ],
  refund: ['M3 12a9 9 0 1 0 3-6.7L3 8', 'M3 3v5h5'],
  send: ['M22 2 11 13', 'M22 2 15 22 11 13 2 9z'],
  server: [
    'M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z',
    'M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2z',
    'M6 6h.01',
    'M6 18h.01',
  ],
  shield: ['M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z', 'M9 12l2 2 4-4'],
  shoe: [
    'M2 16v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-1a3 3 0 0 0-3-3h-2l-4-4-2-5H6L4 9l-2 1z',
    'M2 16h20',
    'M10 9l2-1',
    'M12 11l2-1',
  ],
  sliders: [
    'M4 21v-7',
    'M4 10V3',
    'M12 21v-9',
    'M12 8V3',
    'M20 21v-5',
    'M20 12V3',
    'M1 14h6',
    'M9 8h6',
    'M17 16h6',
  ],
  sparkles: ['M12 3l1.9 5.8L20 11l-6.1 2.2L12 19l-1.9-5.8L4 11l6.1-2.2z', 'M19 3v4', 'M21 5h-4'],
  truck: [
    'M1 3h15v13H1z',
    'M16 8h4l3 3v5h-7z',
    'M5.5 16a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
    'M18.5 16a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
  ],
  user: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z'],
  x: ['M18 6 6 18', 'M6 6l12 12'],
} as const;

export type IconName = keyof typeof ICON_PATHS;

/** A line icon from the app's own set; decorative, so screen readers skip it. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'icon' },
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="size()"
      [attr.height]="size()"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      @for (path of paths(); track $index) {
        <path [attr.d]="path" />
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(18);
  protected readonly paths = computed(() => ICON_PATHS[this.name()]);
}
