import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon, IconName } from './icon';

/** What a list shows when it has nothing in it: a calm line, not a blank space. */
@Component({
  selector: 'app-empty-state',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-icon [name]="icon()" [size]="22" />
    <p class="title">{{ title() }}</p>
    <p class="muted"><ng-content /></p>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-1);
      padding: var(--space-6) var(--space-4);
      text-align: center;
      color: var(--text-muted);
      border: 1px dashed var(--border);
      border-radius: var(--radius);
    }
    p {
      margin: 0;
      font-size: var(--text-sm);
    }
    .title {
      color: var(--text);
      font-weight: 600;
    }
    .muted:empty {
      display: none;
    }
  `,
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly icon = input<IconName>('check');
}
