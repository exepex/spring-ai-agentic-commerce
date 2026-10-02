import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Api, AuditEvent, CustomerNotification, Order, Payment, RefundRequest, Shipment, SupportCase } from '../api';
import { refreshWhileOpen } from '../polling';
import { Timeline } from '../timeline';

@Component({
  selector: 'app-order-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, Timeline],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss',
})
export class OrderDetail {
  readonly orderId = input.required<string>();

  private readonly api = inject(Api);
  protected readonly order = signal<Order | null>(null);
  protected readonly payment = signal<Payment | null>(null);
  protected readonly shipment = signal<Shipment | null>(null);
  protected readonly refunds = signal<RefundRequest[]>([]);
  protected readonly notifications = signal<CustomerNotification[]>([]);
  protected readonly cases = signal<SupportCase[]>([]);
  protected readonly timeline = signal<AuditEvent[]>([]);

  constructor() {
    effect(() => {
      this.orderId();
      untracked(() => this.refresh());
    });
    refreshWhileOpen(() => this.refresh(), 3000, false);
  }

  private refresh(): void {
    const id = this.orderId();
    this.api.order(id).subscribe((order) => this.order.set(order));
    this.api.payment(id).pipe(catchError(() => of(null))).subscribe((payment) => this.payment.set(payment));
    this.api.shipment(id).pipe(catchError(() => of(null))).subscribe((shipment) => this.shipment.set(shipment));
    this.api.refundRequests({ orderId: id }).subscribe((refunds) => this.refunds.set(refunds));
    this.api.notifications(id).subscribe((notifications) => this.notifications.set(notifications));
    this.api.cases({ orderId: id }).subscribe((cases) => this.cases.set(cases));
    this.api.timeline(id).subscribe((events) => this.timeline.set(events));
  }
}
