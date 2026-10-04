import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { statusLabel, statusTone } from '../core/labels';

/** A status in plain words, coloured by how it reads: done, in progress, needs attention, or failed. */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [attr.data-tone]="tone()">{{ text() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<string>();
  /** Words to show instead of the status's usual label. */
  readonly label = input<string>();
  protected readonly text = computed(() => this.label() ?? statusLabel(this.status()));
  protected readonly tone = computed(() => statusTone(this.status()));
}
