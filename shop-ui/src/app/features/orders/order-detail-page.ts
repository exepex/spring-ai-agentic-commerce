import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, of } from 'rxjs';
import { CommerceApi, isNotFound } from '../../core/commerce-api';
import { shortId } from '../../core/labels';
import {
  AuditEvent,
  CustomerNotification,
  Order,
  Payment,
  RefundRequest,
  Shipment,
  SupportCase,
} from '../../core/models';
import { pollWhileActive } from '../../core/polling';
import { AuditTimeline } from '../../shared/audit-timeline';
import { EmptyState } from '../../shared/empty-state';
import { Icon } from '../../shared/icon';
import { CaseCard } from '../../shared/case-card';
import { ActorNamePipe, BylinePipe } from '../../shared/label-pipes';
import { Panel } from '../../shared/panel';
import { StatusBadge } from '../../shared/status-badge';
import { OrderProgress } from '../../shared/order-progress';

/** One order end to end: what was bought, the payment and refunds, the parcel, its cases, and the audit trail. */
@Component({
  selector: 'app-order-detail-page',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    AuditTimeline,
    EmptyState,
    Icon,
    Panel,
    StatusBadge,
    OrderProgress,
    ActorNamePipe,
    BylinePipe,
    CaseCard,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-detail-page.html',
  styleUrl: './order-detail-page.scss',
})
export class OrderDetailPage {
  /** From the route. */
  readonly orderId = input.required<string>();

  private readonly api = inject(CommerceApi);
  protected readonly order = signal<Order | null>(null);
  protected readonly notFound = signal(false);
  /** The order could not be loaded because a service is down; polling keeps trying. */
  protected readonly unavailable = signal(false);
  protected readonly payment = signal<Payment | null>(null);
  protected readonly shipment = signal<Shipment | null>(null);
  protected readonly refundRequests = signal<RefundRequest[]>([]);
  protected readonly notifications = signal<CustomerNotification[]>([]);
  protected readonly cases = signal<SupportCase[]>([]);
  protected readonly timeline = signal<AuditEvent[]>([]);

  protected readonly shortId = computed(() => shortId(this.orderId()));
  protected readonly paid = computed(() => this.payment()?.status === 'SUCCEEDED');
  /** Refunds still in flight or that failed; the ones that went through are in the refunded total. */
  protected readonly openRefunds = computed(
    () => this.payment()?.refunds.filter((refund) => refund.status !== 'SUCCEEDED') ?? [],
  );

  constructor() {
    // Load straight away when the route moves to another order, not at the next poll.
    effect(() => {
      this.orderId();
      untracked(() => this.clear());
      untracked(() => this.refresh());
    });
    pollWhileActive(() => this.refresh(), { immediate: false });
  }

  private refresh(): void {
    const id = this.orderId();
    this.api.order(id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.notFound.set(false);
        this.unavailable.set(false);
      },
      error: (failure) => {
        // An order already on screen stays there through a brief outage; the next poll refreshes it.
        if (this.order() === null) {
          this.notFound.set(isNotFound(failure));
          this.unavailable.set(!isNotFound(failure));
        }
      },
    });
    orNull(this.api.payment(id)).subscribe((payment) => this.payment.set(payment));
    orNull(this.api.shipment(id)).subscribe((shipment) => this.shipment.set(shipment));
    this.api
      .refundRequests({ orderId: id })
      .subscribe((refunds) => this.refundRequests.set(refunds));
    this.api.orderNotifications(id).subscribe((messages) => this.notifications.set(messages));
    this.api.cases({ orderId: id }).subscribe((cases) => this.cases.set(cases));
    this.api.orderTimeline(id).subscribe((events) => this.timeline.set(events));
  }

  /** Forgets the previous order, so its facts never show under another order's id. */
  private clear(): void {
    this.order.set(null);
    this.notFound.set(false);
    this.unavailable.set(false);
    this.payment.set(null);
    this.shipment.set(null);
    this.refundRequests.set([]);
    this.notifications.set([]);
    this.cases.set([]);
    this.timeline.set([]);
  }
}

/** An order has no payment or shipment until it gets that far: a missing one reads as none. */
function orNull<T>(request: Observable<T>): Observable<T | null> {
  return request.pipe(catchError(() => of(null)));
}
