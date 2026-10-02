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
  status: 'PLACED' | 'CONFIRMED' | 'PAYMENT_FAILED' | 'CANCELLED';
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

export interface Shipment {
  trackingNumber: string;
  status: string;
  estimatedDelivery: string;
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
  status: 'PROPOSED' | 'CONFIRMED' | 'FAILED';
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
  status: 'OPEN' | 'RESOLVED';
  resolvedBy: string | null;
  resolutionNote: string | null;
  createdAt: string;
}

export interface AgentView {
  id: string;
  enabled: boolean;
  model: string;
  effort: string;
  tools: string[];
}

export interface AgentsView {
  modelConfigured: boolean;
  slackConfigured: boolean;
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

  confirmProposal(proposalId: string, paymentMethod: string): Observable<Proposal> {
    return this.http.post<Proposal>(`${GOVERNANCE}/order-proposals/${proposalId}/confirm`, { paymentMethod });
  }

  notifications(orderId?: string): Observable<CustomerNotification[]> {
    return this.http.get<CustomerNotification[]>(`${GOVERNANCE}/notifications${orderId ? `?orderId=${orderId}` : ''}`);
  }

  escalations(): Observable<Escalation[]> {
    return this.http.get<Escalation[]>(`${GOVERNANCE}/escalations`);
  }

  resolveEscalation(escalationId: string, by: string, note: string): Observable<Escalation> {
    return this.http.post<Escalation>(`${GOVERNANCE}/escalations/${escalationId}/resolve`, { by, note });
  }

  agents(): Observable<AgentsView> {
    return this.http.get<AgentsView>(`${AGENTS}/agents`);
  }

  setAgentEnabled(agentId: string, enabled: boolean): Observable<AgentsView> {
    return this.http.put<AgentsView>(`${AGENTS}/agents/${agentId}`, { enabled });
  }

  chat(conversationId: string, customerEmail: string, message: string): Observable<AssistantReply> {
    return this.http
      .post<{ text: string; proposals: string[] }>(`${AGENTS}/assistant/chat`, { conversationId, customerEmail, message })
      .pipe(map((reply) => ({ text: reply.text, proposals: reply.proposals.map((json) => JSON.parse(json) as Proposal) })));
  }
}

/** Where the Jaeger UI is, for links from the audit trail to the distributed trace. */
export const JAEGER_URL = 'http://localhost:16686';
