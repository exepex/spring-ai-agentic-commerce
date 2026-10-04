import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommerceApi } from '../../core/commerce-api';
import { shortId } from '../../core/labels';
import { Order } from '../../core/models';
import { pollWhileActive } from '../../core/polling';
import { EmptyState } from '../../shared/empty-state';
import { StatusBadge } from '../../shared/status-badge';

/** Every customer's most recent orders, for the back office. */
@Component({
  selector: 'app-orders-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, StatusBadge, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="page">
      <header class="page-header">
        <div>
          <h1>All orders</h1>
          <p class="lead">
            The most recent orders from every customer. Open one for its payment, parcel, cases and
            the full audit trail.
          </p>
        </div>
      </header>

      <section class="card table-card">
        @if (orders().length) {
          <table class="table stack-on-phone">
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Placed</th>
                <th scope="col">Customer</th>
                <th scope="col">Items</th>
                <th scope="col" class="numeric">Total</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              @for (order of orders(); track order.id) {
                <tr class="clickable" (click)="open(order)">
                  <td data-label="Order">
                    <a
                      class="mono"
                      [routerLink]="['/orders', order.id]"
                      (click)="$event.stopPropagation()"
                      >{{ shortId(order.id) }}</a
                    >
                  </td>
                  <td data-label="Placed" class="nowrap">
                    {{ order.createdAt | date: 'MMM d, HH:mm' }}
                  </td>
                  <td data-label="Customer">{{ order.customerEmail }}</td>
                  <td data-label="Items">
                    <span class="items">
                      @for (line of order.lines; track line.productId) {
                        <span>{{ line.quantity }} × {{ line.productName }}</span>
                      }
                    </span>
                  </td>
                  <td data-label="Total" class="numeric amount">
                    {{ order.total | currency: order.currency }}
                  </td>
                  <td data-label="Status"><app-status-badge [status]="order.status" /></td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <app-empty-state title="No orders yet" icon="package">
            Orders appear here as soon as a customer confirms one.
          </app-empty-state>
        }
      </section>
    </main>
  `,
  styles: `
    .table-card {
      padding: var(--space-2);
    }
    .items {
      display: grid;
    }
    .numeric {
      text-align: right;
    }
    .nowrap {
      white-space: nowrap;
    }
    td {
      overflow-wrap: anywhere;
    }
  `,
})
export class OrdersPage {
  private readonly api = inject(CommerceApi);
  private readonly router = inject(Router);
  protected readonly orders = signal<Order[]>([]);
  protected readonly shortId = shortId;

  constructor() {
    pollWhileActive(() => this.api.orders().subscribe((orders) => this.orders.set(orders)));
  }

  protected open(order: Order): void {
    this.router.navigate(['/orders', order.id]);
  }
}
