import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon, IconName } from './icon';

/**
 * A card with a heading: the building block of every page. Content goes in the body; anything marked
 * {@code panelActions} goes to the right of the heading.
 */
@Component({
  selector: 'app-panel',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <header class="panel-header">
        @if (icon(); as icon) {
          <span class="panel-icon"><app-icon [name]="icon" /></span>
        }
        <div class="panel-title">
          <h2>{{ heading() }}</h2>
          @if (description()) {
            <p class="muted">{{ description() }}</p>
          }
        </div>
        <div class="panel-actions"><ng-content select="[panelActions]" /></div>
      </header>
      <ng-content />
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .panel-header {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      margin-bottom: var(--space-4);
    }
    .panel-icon {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      flex: none;
      border-radius: var(--radius-sm);
      background: var(--brand-soft);
      color: var(--brand);
    }
    .panel-title {
      flex: 1;
      min-width: 0;
    }
    h2 {
      margin: 0;
    }
    p {
      margin: 2px 0 0;
      font-size: var(--text-sm);
    }
    .panel-actions:empty {
      display: none;
    }
  `,
})
export class Panel {
  readonly heading = input.required<string>();
  readonly description = input<string>();
  readonly icon = input<IconName>();
}
