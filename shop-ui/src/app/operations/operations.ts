import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AgentsView, Api, AuditEvent, CarrierOutcome, Product, RefundRequest, Shipment, SupportCase } from '../api';
import { refreshWhileOpen } from '../polling';
import { OPERATORS, Session } from '../session';
import { Timeline } from '../timeline';

/** The operations team's console: approvals, failed refunds, cases, shipping, the agents' kill switches, and demo controls. */
@Component({
  selector: 'app-operations',
  imports: [FormsModule, CurrencyPipe, DatePipe, RouterLink, Timeline],
  templateUrl: './operations.html',
  styleUrl: './operations.scss',
})
export class Operations {
  private readonly api = inject(Api);
  protected readonly session = inject(Session);
  protected readonly operators = OPERATORS;
  /** Why the last action on an item was refused, by item id, as the server said it. */
  protected readonly refusals = signal<Record<string, string>>({});

  protected readonly refunds = signal<RefundRequest[]>([]);
  protected readonly pendingRefunds = computed(() => this.refunds().filter((refund) => refund.status === 'PENDING_APPROVAL'));
  protected readonly failedRefunds = computed(() => this.refunds().filter((refund) => refund.status === 'FAILED'));
  /** Open cases first, then the most recently resolved ones. */
  protected readonly cases = signal<SupportCase[]>([]);
  /** Parcels still to ship, and parcels on their way that the carrier has not reported on. */
  protected readonly shipments = signal<Shipment[]>([]);
  protected readonly agents = signal<AgentsView | null>(null);
  protected readonly products = signal<Product[]>([]);
  protected readonly activity = signal<AuditEvent[]>([]);
  protected readonly paymentOutage = signal(false);
  protected readonly notes = signal<Record<string, string>>({});
  protected readonly busy = signal<string | null>(null);

  protected readonly writeOffProduct = signal('');
  protected readonly writeOffQuantity = signal(1);
  protected readonly writeOffReason = signal('damaged in warehouse');
  protected readonly writeOffResult = signal<string | null>(null);

  constructor() {
    refreshWhileOpen(() => this.refresh());
  }

  protected noteFor(id: string): string {
    return this.notes()[id] ?? '';
  }

  protected setNote(id: string, note: string): void {
    this.notes.update((notes) => ({ ...notes, [id]: note }));
  }

  protected decide(refund: RefundRequest, decision: 'approve' | 'reject' | 'retry'): void {
    this.busy.set(refund.id);
    this.api.decideRefund(refund.id, decision, this.session.operator(), this.noteFor(refund.id)).subscribe({
      next: () => this.done(refund.id),
      error: (failure) => this.done(refund.id, failure),
    });
  }

  protected ship(shipment: Shipment): void {
    this.busy.set(shipment.id);
    this.api.shipOrder(shipment.orderId).subscribe({
      next: () => this.done(shipment.id),
      error: (failure) => this.done(shipment.id, failure),
    });
  }

  protected report(shipment: Shipment, outcome: CarrierOutcome): void {
    this.busy.set(shipment.id);
    this.api.reportFromCarrier(shipment.orderId, outcome, this.noteFor(shipment.id)).subscribe({
      next: () => this.done(shipment.id),
      error: (failure) => this.done(shipment.id, failure),
    });
  }

  protected refusalFor(id: string): string | undefined {
    return this.refusals()[id];
  }

  protected toggleAgent(agentId: string, enabled: boolean): void {
    this.api.setAgentEnabled(agentId, enabled, this.session.operator()).subscribe({
      next: (agents) => this.agents.set(agents),
      error: () => this.refresh(),
    });
  }

  protected toggleOutage(active: boolean): void {
    this.api.setPaymentOutage(active).subscribe((outage) => this.paymentOutage.set(outage));
  }

  protected writeOff(): void {
    const product = this.products().find((candidate) => candidate.id === this.writeOffProduct());
    if (!product) {
      return;
    }
    this.api.adjustStock(product.id, -this.writeOffQuantity(), this.writeOffReason()).subscribe({
      next: (updated) =>
        this.writeOffResult.set(
          updated.reserved > updated.onHand
            ? `${updated.name}: ${updated.onHand} on hand for ${updated.reserved} reserved. Stock-out published: a case goes to ServiceNow for the incident agent.`
            : `${updated.name}: ${updated.onHand} on hand, ${updated.reserved} reserved. No order is affected.`,
        ),
      error: (failure) => this.writeOffResult.set(failure.error?.detail ?? 'The write-off failed'),
    });
  }

  private done(id: string, failure?: { error?: { detail?: string } }): void {
    this.busy.set(null);
    this.refusals.update((refusals) => ({ ...refusals, [id]: failure?.error?.detail ?? '' }));
    this.refresh();
  }

  private refresh(): void {
    // Open work is fetched by status, so it never drops out behind the 100 most recent records.
    forkJoin({
      pending: this.api.refundRequests({ status: 'PENDING_APPROVAL' }),
      failed: this.api.refundRequests({ status: 'FAILED' }),
      open: this.api.cases({ open: true }),
      recent: this.api.cases(),
      activity: this.api.recentActivity(),
    }).subscribe(({ pending, failed, open, recent, activity }) => {
      this.refunds.set([...pending, ...failed]);
      this.cases.set([...open, ...recent.filter((supportCase) => supportCase.status === 'RESOLVED')]);
      this.activity.set(activity);
    });
    this.api.shipments(['PREPARING', 'SHIPPED']).subscribe((shipments) => this.shipments.set(shipments));
    this.api.agents().subscribe({ next: (agents) => this.agents.set(agents), error: () => this.agents.set(null) });
    this.api.paymentOutage().subscribe((outage) => this.paymentOutage.set(outage));
    this.api.products().subscribe((products) => {
      this.products.set(products);
      if (!this.writeOffProduct() && products.length) {
        this.writeOffProduct.set(products[0].id);
      }
    });
  }
}
