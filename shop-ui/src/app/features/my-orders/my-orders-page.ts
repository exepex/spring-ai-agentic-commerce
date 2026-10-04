import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommerceApi } from '../../core/commerce-api';
import { CustomerNotification, Order } from '../../core/models';
import { pollWhileActive } from '../../core/polling';
import { Session } from '../../core/session';
import { EmptyState } from '../../shared/empty-state';
import { Icon } from '../../shared/icon';
import { Panel } from '../../shared/panel';
import { StatusBadge } from '../../shared/status-badge';

/** The signed-in customer's own orders, and the messages the shop and its agents sent them. */
@Component({
  selector: 'app-my-orders-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Panel, StatusBadge, EmptyState, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-orders-page.html',
  styleUrl: './my-orders-page.scss',
})
export class MyOrdersPage {
  private readonly api = inject(CommerceApi);
  protected readonly session = inject(Session);

  private readonly allOrders = signal<Order[]>([]);
  private readonly allNotifications = signal<CustomerNotification[]>([]);
  // An answer can still be for the customer signed in before; show only the current customer's.
  protected readonly orders = computed(() =>
    this.allOrders().filter((order) => order.customerEmail === this.session.customer()),
  );
  protected readonly notifications = computed(() =>
    this.allNotifications().filter(
      (notification) => notification.customerEmail === this.session.customer(),
    ),
  );

  constructor() {
    // Load straight away when another customer signs in, not at the next poll.
    effect(() => {
      this.session.customer();
      untracked(() => this.refresh());
    });
    pollWhileActive(() => this.refresh(), { intervalMs: 4000, immediate: false });
  }

  private refresh(): void {
    const customer = this.session.customer();
    this.api.orders(customer).subscribe((orders) => this.allOrders.set(orders));
    this.api
      .customerNotifications(customer)
      .subscribe((notifications) => this.allNotifications.set(notifications));
  }
}
