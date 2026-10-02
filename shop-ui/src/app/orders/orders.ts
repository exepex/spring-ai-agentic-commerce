import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api, Order } from '../api';
import { refreshWhileOpen } from '../polling';

@Component({
  selector: 'app-orders',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <main class="page">
      <h1>Orders</h1>
      <section class="card">
        <table>
          <thead>
            <tr><th>Placed</th><th>Customer</th><th>Items</th><th>Total</th><th>Status</th></tr>
          </thead>
          <tbody>
            @for (order of orders(); track order.id) {
              <tr class="clickable" (click)="open(order)">
                <td>{{ order.createdAt | date: 'MMM d, HH:mm:ss' }}</td>
                <td>{{ order.customerEmail }}</td>
                <td>
                  @for (line of order.lines; track line.productId) {
                    <div>{{ line.quantity }} × {{ line.productName }}</div>
                  }
                </td>
                <td>{{ order.total | currency: order.currency }}</td>
                <td><span class="chip {{ order.status }}">{{ order.status }}</span></td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="empty">No orders yet.</td></tr>
            }
          </tbody>
        </table>
      </section>
    </main>
  `,
})
export class Orders {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly orders = signal<Order[]>([]);

  constructor() {
    refreshWhileOpen(() => this.api.orders().subscribe((orders) => this.orders.set(orders)));
  }

  protected open(order: Order): void {
    this.router.navigate(['/orders', order.id]);
  }
}
