import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AgentsView, Api, AuditEvent, CarrierOutcome, Escalation, Product, RefundRequest, Shipment } from '../api';
import { refreshWhileOpen } from '../polling';
import { OPERATORS, Session } from '../session';
import { Timeline } from '../timeline';

/** The operations team's console: approvals, escalations, shipping, the agents' kill switches, and demo controls. */
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
  protected readonly escalations = signal<Escalation[]>([]);
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

  /** {@code card} is the card the button sits on: it is busy meanwhile and shows a refusal. */
  protected decide(refund: RefundRequest, decision: 'approve' | 'reject' | 'retry', card: string = refund.id): void {
    this.busy.set(card);
    this.api.decideRefund(refund.id, decision, this.session.operator(), this.noteFor(refund.id)).subscribe({
      next: () => this.done(card),
      error: (failure) => this.done(card, failure),
    });
  }

  protected failedRefundFor(escalation: Escalation): RefundRequest | undefined {
    return this.refunds().find((refund) => refund.orderId === escalation.orderId && refund.status === 'FAILED');
  }

  protected act(escalation: Escalation, action: 'assign' | 'hand-back' | 'resolve'): void {
    this.busy.set(escalation.id);
    const note = this.noteFor(escalation.id) || (action === 'resolve' ? 'Handled' : '');
    this.api.actOnEscalation(escalation.id, action, this.session.operator(), note).subscribe({
      next: () => this.done(escalation.id),
      error: (failure) => this.done(escalation.id, failure),
    });
  }

  protected isMine(escalation: Escalation): boolean {
    return escalation.status === 'ASSIGNED' && escalation.assignedTo === this.session.operator();
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
            ? `${updated.name}: ${updated.onHand} on hand for ${updated.reserved} reserved. Stock-out published: the order-exceptions agent takes over.`
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
      open: this.api.escalations('OPEN'),
      assigned: this.api.escalations('ASSIGNED'),
      recent: this.api.escalations(),
      activity: this.api.recentActivity(),
    }).subscribe(({ pending, failed, open, assigned, recent, activity }) => {
      this.refunds.set([...pending, ...failed]);
      this.escalations.set([...open, ...assigned, ...recent.filter((escalation) => escalation.status === 'RESOLVED')]);
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
