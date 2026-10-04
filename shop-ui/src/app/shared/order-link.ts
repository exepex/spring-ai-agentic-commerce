import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { shortId } from '../core/labels';

/** A link to an order's page, named by the start of its id. */
@Component({
  selector: 'app-order-link',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<a [routerLink]="['/orders', orderId()]" [attr.aria-label]="'Order ' + orderId()"
    >Order <span class="mono">{{ label() }}</span></a
  >`,
  styles: `
    a {
      white-space: nowrap;
      font-size: var(--text-sm);
      font-weight: 500;
    }
  `,
})
export class OrderLink {
  readonly orderId = input.required<string>();
  protected readonly label = computed(() => shortId(this.orderId()));
}
