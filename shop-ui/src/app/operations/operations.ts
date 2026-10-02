import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AgentsView, Api, AuditEvent, Escalation, Product, RefundRequest } from '../api';
import { refreshWhileOpen } from '../polling';
import { OPERATOR } from '../session';
import { Timeline } from '../timeline';

/** The operations team's console: approvals, escalations, the agents' kill switches, and demo controls. */
@Component({
  selector: 'app-operations',
  imports: [FormsModule, CurrencyPipe, DatePipe, RouterLink, Timeline],
  templateUrl: './operations.html',
  styleUrl: './operations.scss',
})
export class Operations {
  private readonly api = inject(Api);
  protected readonly operator = OPERATOR;

  protected readonly refunds = signal<RefundRequest[]>([]);
  protected readonly pendingRefunds = computed(() => this.refunds().filter((refund) => refund.status === 'PENDING_APPROVAL'));
  protected readonly escalations = signal<Escalation[]>([]);
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
    this.api.decideRefund(refund.id, decision, OPERATOR, this.noteFor(refund.id)).subscribe({
      next: () => this.done(),
      error: () => this.done(),
    });
  }

  protected failedRefundFor(escalation: Escalation): RefundRequest | undefined {
    return this.refunds().find((refund) => refund.orderId === escalation.orderId && refund.status === 'FAILED');
  }

  protected resolve(escalation: Escalation): void {
    this.busy.set(escalation.id);
    this.api.resolveEscalation(escalation.id, OPERATOR, this.noteFor(escalation.id) || 'Handled').subscribe({
      next: () => this.done(),
      error: () => this.done(),
    });
  }

  protected toggleAgent(agentId: string, enabled: boolean): void {
    this.api.setAgentEnabled(agentId, enabled).subscribe((agents) => this.agents.set(agents));
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

  private done(): void {
    this.busy.set(null);
    this.refresh();
  }

  private refresh(): void {
    // Open work is fetched by status, so it never drops out behind the 100 most recent records.
    forkJoin({
      pending: this.api.refundRequests({ status: 'PENDING_APPROVAL' }),
      failed: this.api.refundRequests({ status: 'FAILED' }),
      open: this.api.escalations('OPEN'),
      recent: this.api.escalations(),
      activity: this.api.recentActivity(),
    }).subscribe(({ pending, failed, open, recent, activity }) => {
      this.refunds.set([...pending, ...failed]);
      this.escalations.set([...open, ...recent.filter((escalation) => escalation.status !== 'OPEN')]);
      this.activity.set(activity);
    });
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
