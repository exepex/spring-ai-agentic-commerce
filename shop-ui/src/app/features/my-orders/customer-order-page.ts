import { CurrencyPipe, DatePipe, formatDate } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  LOCALE_ID,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, of } from 'rxjs';
import { CommerceApi } from '../../core/commerce-api';
import { CustomerNotification, Order, Payment, RefundRequest, Shipment } from '../../core/models';
import { pollWhileActive } from '../../core/polling';
import { Session } from '../../core/session';
import { EmptyState } from '../../shared/empty-state';
import { Icon } from '../../shared/icon';
import { OrderProgress } from '../../shared/order-progress';
import { Panel } from '../../shared/panel';
import { StatusBadge } from '../../shared/status-badge';

type Tone = 'info' | 'success' | 'warning' | 'danger';

/** One sentence that tells the customer where their order is and what happens next. */
interface OrderUpdate {
  tone: Tone;
  text: string;
}

/**
 * The customer's view of one of their orders: where it is, what they paid and got back, the parcel, and the messages
 * about it. Nothing here names agents, people or the shop's services: the customer cares what happens, not who did it.
 */
@Component({
  selector: 'app-customer-order-page',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    EmptyState,
    Icon,
    OrderProgress,
    Panel,
    StatusBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-order-page.html',
  styleUrl: './customer-order-page.scss',
})
export class CustomerOrderPage {
  /** From the route. */
  readonly orderId = input.required<string>();

  private readonly api = inject(CommerceApi);
  private readonly session = inject(Session);
  private readonly locale = inject(LOCALE_ID);

  private readonly loadedOrder = signal<Order | null>(null);
  protected readonly notFound = signal(false);
  protected readonly payment = signal<Payment | null>(null);
  protected readonly shipment = signal<Shipment | null>(null);
  private readonly refundRequests = signal<RefundRequest[]>([]);
  protected readonly messages = signal<CustomerNotification[]>([]);

  /** Customers see only their own orders. */
  protected readonly order = computed(() => {
    const order = this.loadedOrder();
    return order?.customerEmail === this.session.customer() ? order : null;
  });
  protected readonly belongsToSomeoneElse = computed(
    () => this.loadedOrder() !== null && this.order() === null,
  );
  protected readonly paid = computed(() => this.payment()?.status === 'SUCCEEDED');
  /** Refunds asked for but not yet back on the card: waiting for approval, or being retried. */
  protected readonly refundsOnTheirWay = computed(() =>
    this.refundRequests().filter(
      (refund) => refund.status === 'PENDING_APPROVAL' || refund.status === 'FAILED',
    ),
  );
  protected readonly update = computed(() => {
    const order = this.order();
    return order ? this.describe(order) : null;
  });

  constructor() {
    effect(() => {
      this.orderId();
      untracked(() => this.clear());
      untracked(() => this.refresh());
    });
    pollWhileActive(() => this.refresh(), { intervalMs: 4000, immediate: false });
  }

  private describe(order: Order): OrderUpdate {
    const shipment = this.shipment();
    switch (order.status) {
      case 'PLACED':
      case 'PAYMENT_PENDING':
        return { tone: 'info', text: "We're confirming your payment." };
      case 'CONFIRMED':
        return { tone: 'success', text: "Paid. We're getting your order ready to ship." };
      case 'PAYMENT_FAILED':
        return {
          tone: 'danger',
          text: `Your payment didn't go through${order.paymentFailure ? `: ${order.paymentFailure}` : ''}. You haven't been charged.`,
        };
      case 'CANCELLED':
        return {
          tone: 'warning',
          text: `This order was cancelled${order.cancellationReason ? `: ${order.cancellationReason}` : ''}.`,
        };
      case 'SHIPPED':
        return {
          tone: 'info',
          text: shipment
            ? `On its way. Expected ${this.day(shipment.estimatedDelivery)}.`
            : 'On its way.',
        };
      case 'DELIVERED':
        return {
          tone: 'success',
          text: shipment?.deliveredAt
            ? `Delivered ${this.day(shipment.deliveredAt)}.`
            : 'Delivered.',
        };
      case 'DELIVERY_FAILED':
        return {
          tone: 'danger',
          text: "The carrier couldn't deliver your parcel. We're on it and will let you know what happens next.",
        };
      case 'LOST':
        return {
          tone: 'danger',
          text: "The carrier lost your parcel. We're on it and will let you know what happens next.",
        };
    }
  }

  private day(timestamp: string): string {
    return formatDate(timestamp, 'EEEE, MMM d', this.locale);
  }

  private refresh(): void {
    const id = this.orderId();
    this.api.order(id).subscribe({
      next: (order) => {
        this.loadedOrder.set(order);
        this.notFound.set(false);
      },
      error: () => this.notFound.set(this.loadedOrder() === null),
    });
    orNull(this.api.payment(id)).subscribe((payment) => this.payment.set(payment));
    orNull(this.api.shipment(id)).subscribe((shipment) => this.shipment.set(shipment));
    this.api
      .refundRequests({ orderId: id })
      .subscribe((refunds) => this.refundRequests.set(refunds));
    this.api.orderNotifications(id).subscribe((messages) => this.messages.set(messages));
  }

  private clear(): void {
    this.loadedOrder.set(null);
    this.notFound.set(false);
    this.payment.set(null);
    this.shipment.set(null);
    this.refundRequests.set([]);
    this.messages.set([]);
  }
}

/** An order has no payment or shipment until it gets that far: a missing one reads as none. */
function orNull<T>(request: Observable<T>): Observable<T | null> {
  return request.pipe(catchError(() => of(null)));
}
