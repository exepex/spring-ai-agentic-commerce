import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { CommerceApi, refusalReason } from '../../core/commerce-api';
import {
  AgentOverview,
  AuditEvent,
  CarrierOutcome,
  Product,
  RefundDecision,
  RefundRequest,
  Shipment,
  SupportCase,
} from '../../core/models';
import { Session } from '../../core/session';

export interface WriteOffOutcome {
  succeeded: boolean;
  message: string;
}

/**
 * What the operations console shows and does, shared by its tabs: the work waiting for people, the agents and their
 * kill switches, and the demo controls. Provided by the operations page, so it lives as long as the page.
 */
@Injectable()
export class OperationsStore {
  private readonly api = inject(CommerceApi);
  private readonly session = inject(Session);

  private readonly refunds = signal<RefundRequest[]>([]);
  readonly pendingRefunds = computed(() =>
    this.refunds().filter((refund) => refund.status === 'PENDING_APPROVAL'),
  );
  readonly failedRefunds = computed(() =>
    this.refunds().filter((refund) => refund.status === 'FAILED'),
  );
  /** Unresolved cases first, then the most recently resolved ones. */
  readonly cases = signal<SupportCase[]>([]);
  readonly openCases = computed(() =>
    this.cases().filter((supportCase) => supportCase.status !== 'RESOLVED'),
  );
  readonly resolvedCases = computed(() =>
    this.cases().filter((supportCase) => supportCase.status === 'RESOLVED'),
  );
  /** Parcels still to ship, and parcels on their way that the carrier has not reported on. */
  readonly shipments = signal<Shipment[]>([]);
  /** null when the agent service cannot be reached. */
  readonly agents = signal<AgentOverview | null>(null);
  readonly products = signal<Product[]>([]);
  readonly activity = signal<AuditEvent[]>([]);
  readonly paymentOutage = signal(false);

  /** What the last write-off did. */
  readonly writeOffOutcome = signal<WriteOffOutcome | null>(null);

  /** The item whose action is on its way to the server. */
  readonly busyItemId = signal<string | null>(null);
  /** Why the last action on an item was refused, by item id, in the server's words. */
  private readonly refusals = signal<Record<string, string>>({});

  refresh(): void {
    // Open work is fetched by status, so it never drops out behind the 100 most recent records.
    forkJoin({
      pending: this.api.refundRequests({ status: 'PENDING_APPROVAL' }),
      failed: this.api.refundRequests({ status: 'FAILED' }),
      open: this.api.cases({ open: true }),
      recent: this.api.cases(),
      activity: this.api.recentActivity(),
    }).subscribe(({ pending, failed, open, recent, activity }) => {
      this.refunds.set([...pending, ...failed]);
      this.cases.set([
        ...open,
        ...recent.filter((supportCase) => supportCase.status === 'RESOLVED'),
      ]);
      this.activity.set(activity);
    });
    this.api
      .shipments(['PREPARING', 'SHIPPED'])
      .subscribe((shipments) => this.shipments.set(shipments));
    this.api.agents().subscribe({
      next: (agents) => this.agents.set(agents),
      error: () => this.agents.set(null),
    });
    this.api.paymentOutage().subscribe((active) => this.paymentOutage.set(active));
    this.api.products().subscribe((products) => this.products.set(products));
  }

  refusalFor(itemId: string): string | undefined {
    return this.refusals()[itemId] || undefined;
  }

  isBusy(itemId: string): boolean {
    return this.busyItemId() === itemId;
  }

  decideRefund(refund: RefundRequest, decision: RefundDecision, note: string): void {
    this.act(refund.id, this.api.decideRefund(refund.id, decision, this.session.operator(), note));
  }

  ship(shipment: Shipment): void {
    this.act(shipment.id, this.api.dispatchOrder(shipment.orderId));
  }

  reportCarrierOutcome(shipment: Shipment, outcome: CarrierOutcome, problem: string): void {
    this.act(shipment.id, this.api.reportCarrierOutcome(shipment.orderId, outcome, problem));
  }

  setAgentEnabled(agentId: string, enabled: boolean): void {
    this.api.setAgentEnabled(agentId, enabled, this.session.operator()).subscribe({
      next: (agents) => this.agents.set(agents),
      error: () => this.refresh(),
    });
  }

  setPaymentOutage(active: boolean): void {
    this.api.setPaymentOutage(active).subscribe({
      next: (outage) => this.paymentOutage.set(outage),
      error: () => this.refresh(),
    });
  }

  /** Writes stock off; with fewer units left than are reserved, the catalog publishes a stock-out and a case opens. */
  writeOff(productId: string, quantity: number, reason: string): void {
    this.api.adjustStock(productId, -quantity, reason).subscribe({
      next: (product) => {
        this.writeOffOutcome.set({ succeeded: true, message: describeStock(product) });
        this.refresh();
      },
      error: (failure) =>
        this.writeOffOutcome.set({
          succeeded: false,
          message: refusalReason(failure, 'The write-off failed.'),
        }),
    });
  }

  /** Runs one item's action: the item is busy meanwhile, and a refusal is kept to show next to it. */
  private act(itemId: string, request: Observable<unknown>): void {
    this.busyItemId.set(itemId);
    request.subscribe({
      next: () => this.settle(itemId, ''),
      error: (failure) => this.settle(itemId, refusalReason(failure, 'The request failed.')),
    });
  }

  private settle(itemId: string, refusal: string): void {
    this.busyItemId.set(null);
    this.refusals.update((refusals) => ({ ...refusals, [itemId]: refusal }));
    this.refresh();
  }
}

function describeStock(product: Product): string {
  return product.reserved > product.onHand
    ? `${product.name}: ${product.onHand} on hand for ${product.reserved} reserved. ` +
        'Stock-out published: a case goes to ServiceNow for the incident agent.'
    : `${product.name}: ${product.onHand} on hand, ${product.reserved} reserved. No order is affected.`;
}
