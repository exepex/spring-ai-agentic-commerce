import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus } from '../../core/models';
import { Icon } from '../../shared/icon';

type StepState = 'done' | 'current' | 'failed' | 'upcoming';

interface ProgressStep {
  label: string;
  state: StepState;
}

const STEPS = ['Placed', 'Paid', 'Shipped', 'Delivered'];

/** For each status: the step the order is at (every earlier one is done), and why it stopped there, if it did. */
const POSITIONS: Record<Exclude<OrderStatus, 'CANCELLED'>, { step: number; failure?: string }> = {
  PLACED: { step: 1 },
  PAYMENT_PENDING: { step: 1 },
  PAYMENT_FAILED: { step: 1, failure: 'Payment failed' },
  CONFIRMED: { step: 2 },
  SHIPPED: { step: 3 },
  DELIVERED: { step: 4 },
  DELIVERY_FAILED: { step: 3, failure: 'Delivery failed' },
  LOST: { step: 3, failure: 'Lost in transit' },
};

/** Where an order is on its way from placed to delivered, and where it stopped if it went wrong. */
@Component({
  selector: 'app-order-progress',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="progress" aria-label="Order progress">
      @for (step of steps(); track $index) {
        <li
          [attr.data-state]="step.state"
          [attr.aria-current]="step.state === 'current' ? 'step' : null"
        >
          <span class="marker">
            @switch (step.state) {
              @case ('done') {
                <app-icon name="check" [size]="14" />
              }
              @case ('failed') {
                <app-icon name="x" [size]="14" />
              }
              @default {
                {{ $index + 1 }}
              }
            }
          </span>
          <span class="label">{{ step.label }}</span>
        </li>
      }
    </ol>
  `,
  styleUrl: './order-progress.scss',
})
export class OrderProgress {
  readonly status = input.required<OrderStatus>();
  /** Whether the order was paid; tells how far a cancelled order got. */
  readonly paid = input(false);
  /** Whether the order left the warehouse; tells how far a cancelled order got. */
  readonly shipped = input(false);

  protected readonly steps = computed<ProgressStep[]>(() => {
    const status = this.status();
    const { step, failure } =
      status === 'CANCELLED'
        ? { step: 1 + Number(this.paid()) + Number(this.shipped()), failure: 'Cancelled' }
        : POSITIONS[status];
    return STEPS.map((label, index) => ({
      label: index === step && failure ? failure : label,
      state: index < step ? 'done' : index > step ? 'upcoming' : failure ? 'failed' : 'current',
    }));
  });
}
