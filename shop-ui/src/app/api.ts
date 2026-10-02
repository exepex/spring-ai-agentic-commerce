import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Every call goes through the same origin; the dev proxy (or nginx in Docker) routes /svc/<service> to it. */
const CATALOG = '/svc/catalog/api';
const ORDERS = '/svc/orders/api';
const PAYMENTS = '/svc/payments/api';
const SHIPPING = '/svc/shipping/api';
const GOVERNANCE = '/svc/governance/api';
const AGENTS = '/svc/agents/api';

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  onHand: number;
  reserved: number;
  available: number;
}

export interface OrderLine {
  productId: string;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: string;
  customerEmail: string;
  status:
    | 'PLACED'
    | 'PAYMENT_PENDING'
    | 'CONFIRMED'
    | 'PAYMENT_FAILED'
    | 'CANCELLED'
    | 'SHIPPED'
    | 'DELIVERED'
    | 'DELIVERY_FAILED'
    | 'LOST';
  total: number;
  currency: string;
  createdAt: string;
  cancelledAt: string | null;
  cancellationReason: string | null;
  paymentFailure: string | null;
  lines: OrderLine[];
}

export interface Refund {
  id: string;
  amount: number;
  reason: string;
  idempotencyKey: string;
  providerReference: string;
  /** A failed refund returned no money; it is not counted in the payment's refunded amount. */
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED';
  createdAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  amount: number;
  refundedAmount: number;
  refundable: number;
  currency: string;
  status: string;
  provider: string;
  providerReference: string;
  failureMessage: string | null;
  refunds: Refund[];
}

export type CarrierOutcome = 'DELIVERED' | 'DELIVERY_FAILED' | 'LOST';

export interface Shipment {
  id: string;
  orderId: string;
  customerEmail: string;
  trackingNumber: string;
  status: 'PREPARING' | 'SHIPPED' | 'CANCELLED' | CarrierOutcome;
  estimatedDelivery: string;
  shippedAt: string | null;
  deliveredAt: string | null;
  /** Why the carrier did not deliver the parcel. */
  deliveryProblem: string | null;
}

export interface AuditEvent {
  id: string;
  occurredAt: string;
  orderId: string | null;
  actorType: 'AGENT' | 'HUMAN' | 'SYSTEM';
  actor: string;
  action: string;
  outcome: 'SUCCEEDED' | 'FAILED' | 'DENIED' | 'PENDING_APPROVAL' | 'REJECTED';
  summary: string;
  details: string | null;
  traceId: string | null;
}

export interface RefundRequest {
  id: string;
  orderId: string;
  amount: number;
  currency: string;
  reason: string;
  idempotencyKey: string;
  requestedBy: string;
  status: 'PENDING_APPROVAL' | 'EXECUTED' | 'FAILED' | 'REJECTED';
  providerReference: string | null;
  failure: string | null;
  decidedBy: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface ProposedLine {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
}

export interface Proposal {
  id: string;
  customerEmail: string;
  lines: ProposedLine[];
  total: number;
  currency: string;
  status: 'PROPOSED' | 'CONFIRMING' | 'CONFIRMED' | 'FAILED';
  orderId: string | null;
  failure: string | null;
}

export interface CustomerNotification {
  id: string;
  orderId: string | null;
  customerEmail: string;
  message: string;
  sentBy: string;
  createdAt: string;
}

export interface Escalation {
  id: string;
  orderId: string | null;
  raisedBy: string;
  summary: string;
  status: 'OPEN' | 'ASSIGNED' | 'RESOLVED';
  assignedTo: string | null;
  resolvedBy: string | null;
  resolutionNote: string | null;
  createdAt: string;
}

export interface AgentView {
  id: string;
  /** null when the switches cannot be read (the MCP server is down). */
  enabled: boolean | null;
  model: string;
  effort: string;
  tools: string[];
}

export interface AgentsView {
  modelConfigured: boolean;
  slackConfigured: boolean;
  servicenowConfigured: boolean;
  agents: AgentView[];
}

export interface AssistantReply {
  text: string;
  proposals: Proposal[];
}

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  products(): Observable<Product[]> {
    return this.http.get<Product[]>(`${CATALOG}/products`);
  }

  adjustStock(productId: string, delta: number, reason: string): Observable<Product> {
    return this.http.post<Product>(`${CATALOG}/products/${productId}/stock-adjustments`, { delta, reason });
  }

  orders(customerEmail?: string): Observable<Order[]> {
    const query = customerEmail ? `?customerEmail=${encodeURIComponent(customerEmail)}` : '';
    return this.http.get<Order[]>(`${ORDERS}/orders${query}`);
  }

  order(orderId: string): Observable<Order> {
    return this.http.get<Order>(`${ORDERS}/orders/${orderId}`);
  }

  payment(orderId: string): Observable<Payment> {
    return this.http.get<Payment>(`${PAYMENTS}/payments/${orderId}`);
  }

  paymentOutage(): Observable<boolean> {
    return this.http.get<{ active: boolean }>(`${PAYMENTS}/admin/simulated-outage`).pipe(map((outage) => outage.active));
  }

  setPaymentOutage(active: boolean): Observable<boolean> {
    return this.http
      .put<{ active: boolean }>(`${PAYMENTS}/admin/simulated-outage`, { active })
      .pipe(map((outage) => outage.active));
  }

  shipment(orderId: string): Observable<Shipment> {
    return this.http.get<Shipment>(`${SHIPPING}/shipments/${orderId}`);
  }

  shipments(statuses: Shipment['status'][]): Observable<Shipment[]> {
    const query = new URLSearchParams(statuses.map((status) => ['status', status]));
    return this.http.get<Shipment[]>(`${SHIPPING}/shipments?${query}`);
  }

  /** Ships the order from the warehouse; refused once it is cancelled or its stock is short. */
  shipOrder(orderId: string): Observable<Order> {
    return this.http.post<Order>(`${ORDERS}/orders/${orderId}/dispatch`, {});
  }

  /** Plays the carrier: reports what happened to a shipped parcel. */
  reportFromCarrier(orderId: string, outcome: CarrierOutcome, deliveryProblem: string): Observable<Shipment> {
    return this.http.post<Shipment>(`${SHIPPING}/shipments/${orderId}/carrier-reports`, { outcome, deliveryProblem });
  }

  timeline(orderId: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${GOVERNANCE}/orders/${orderId}/timeline`);
  }

  recentActivity(): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${GOVERNANCE}/audit-events`);
  }

  refundRequests(filter: { status?: string; orderId?: string } = {}): Observable<RefundRequest[]> {
    const query = new URLSearchParams(Object.entries(filter).filter(([, value]) => !!value) as [string, string][]);
    return this.http.get<RefundRequest[]>(`${GOVERNANCE}/refund-requests?${query}`);
  }

  decideRefund(requestId: string, decision: 'approve' | 'reject' | 'retry', by: string, note: string): Observable<RefundRequest> {
    return this.http.post<RefundRequest>(`${GOVERNANCE}/refund-requests/${requestId}/${decision}`, { by, note });
  }

  proposal(proposalId: string): Observable<Proposal> {
    return this.http.get<Proposal>(`${GOVERNANCE}/order-proposals/${proposalId}`);
  }

  confirmProposal(proposalId: string, paymentMethod: string): Observable<Proposal> {
    return this.http.post<Proposal>(`${GOVERNANCE}/order-proposals/${proposalId}/confirm`, { paymentMethod });
  }

  notifications(orderId?: string): Observable<CustomerNotification[]> {
    return this.http.get<CustomerNotification[]>(`${GOVERNANCE}/notifications${orderId ? `?orderId=${orderId}` : ''}`);
  }

  escalations(status?: string): Observable<Escalation[]> {
    return this.http.get<Escalation[]>(`${GOVERNANCE}/escalations${status ? `?status=${status}` : ''}`);
  }

  /** Acts on an escalation: take it, hand it back to the queue, or resolve it. */
  actOnEscalation(escalationId: string, action: 'assign' | 'hand-back' | 'resolve', by: string, note: string): Observable<Escalation> {
    return this.http.post<Escalation>(`${GOVERNANCE}/escalations/${escalationId}/${action}`, { by, note });
  }

  agents(): Observable<AgentsView> {
    return this.http.get<AgentsView>(`${AGENTS}/agents`);
  }

  setAgentEnabled(agentId: string, enabled: boolean, by: string): Observable<AgentsView> {
    return this.http.put<AgentsView>(`${AGENTS}/agents/${agentId}`, { enabled, by });
  }

  chat(conversationId: string, customerEmail: string, message: string): Observable<AssistantReply> {
    return this.http
      .post<{ text: string; proposals: string[] }>(`${AGENTS}/assistant/chat`, { conversationId, customerEmail, message })
      .pipe(map((reply) => ({ text: reply.text, proposals: reply.proposals.map((json) => JSON.parse(json) as Proposal) })));
  }
}

/** Where the Jaeger UI is, for links from the audit trail to the distributed trace. */
export const JAEGER_URL = 'http://localhost:16686';
