import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ServiceUrls } from './endpoints';
import {
  AgentOverview,
  AssistantReply,
  AuditEvent,
  CarrierOutcome,
  CustomerNotification,
  Order,
  Payment,
  Product,
  Proposal,
  RefundDecision,
  RefundRequest,
  Shipment,
  SupportCase,
} from './models';

/** Which refund requests to list: by status, by order, or (with neither) the most recent. */
export interface RefundRequestFilter {
  status?: RefundRequest['status'];
  orderId?: string;
}

/** Which cases to list: the unresolved ones, an order's, or (with neither) the most recent. */
export interface CaseFilter {
  open?: boolean;
  orderId?: string;
}

/** The one client for every service the UI talks to. */
@Injectable({ providedIn: 'root' })
export class CommerceApi {
  private readonly http = inject(HttpClient);

  products(): Observable<Product[]> {
    return this.http.get<Product[]>(`${ServiceUrls.catalog}/products`);
  }

  /** Adds stock (a positive delta) or writes it off (a negative one). */
  adjustStock(productId: string, delta: number, reason: string): Observable<Product> {
    return this.http.post<Product>(
      `${ServiceUrls.catalog}/products/${productId}/stock-adjustments`,
      {
        delta,
        reason,
      },
    );
  }

  /** One customer's orders with {@code customerEmail}, otherwise every customer's most recent ones. */
  orders(customerEmail?: string): Observable<Order[]> {
    const params = customerEmail ? new HttpParams().set('customerEmail', customerEmail) : undefined;
    return this.http.get<Order[]>(`${ServiceUrls.orders}/orders`, { params });
  }

  order(orderId: string): Observable<Order> {
    return this.http.get<Order>(`${ServiceUrls.orders}/orders/${orderId}`);
  }

  /** Ships the order from the warehouse; refused once it is cancelled or its stock is short. */
  dispatchOrder(orderId: string): Observable<Order> {
    return this.http.post<Order>(`${ServiceUrls.orders}/orders/${orderId}/dispatch`, {});
  }

  payment(orderId: string): Observable<Payment> {
    return this.http.get<Payment>(`${ServiceUrls.payments}/payments/${orderId}`);
  }

  paymentOutage(): Observable<boolean> {
    return this.http
      .get<{ active: boolean }>(`${ServiceUrls.payments}/admin/simulated-outage`)
      .pipe(map((outage) => outage.active));
  }

  setPaymentOutage(active: boolean): Observable<boolean> {
    return this.http
      .put<{ active: boolean }>(`${ServiceUrls.payments}/admin/simulated-outage`, { active })
      .pipe(map((outage) => outage.active));
  }

  shipment(orderId: string): Observable<Shipment> {
    return this.http.get<Shipment>(`${ServiceUrls.shipping}/shipments/${orderId}`);
  }

  shipments(statuses: Shipment['status'][]): Observable<Shipment[]> {
    const params = statuses.reduce(
      (query, status) => query.append('status', status),
      new HttpParams(),
    );
    return this.http.get<Shipment[]>(`${ServiceUrls.shipping}/shipments`, { params });
  }

  /** Plays the carrier: reports what happened to a shipped parcel. */
  reportCarrierOutcome(
    orderId: string,
    outcome: CarrierOutcome,
    deliveryProblem: string,
  ): Observable<Shipment> {
    return this.http.post<Shipment>(
      `${ServiceUrls.shipping}/shipments/${orderId}/carrier-reports`,
      {
        outcome,
        deliveryProblem,
      },
    );
  }

  orderTimeline(orderId: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${ServiceUrls.governance}/orders/${orderId}/timeline`);
  }

  recentActivity(): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${ServiceUrls.governance}/audit-events`);
  }

  refundRequests(filter: RefundRequestFilter = {}): Observable<RefundRequest[]> {
    let params = new HttpParams();
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.orderId) {
      params = params.set('orderId', filter.orderId);
    }
    return this.http.get<RefundRequest[]>(`${ServiceUrls.governance}/refund-requests`, { params });
  }

  decideRefund(
    requestId: string,
    decision: RefundDecision,
    decidedBy: string,
    note: string,
  ): Observable<RefundRequest> {
    return this.http.post<RefundRequest>(
      `${ServiceUrls.governance}/refund-requests/${requestId}/${decision}`,
      { by: decidedBy, note },
    );
  }

  proposal(proposalId: string): Observable<Proposal> {
    return this.http.get<Proposal>(`${ServiceUrls.governance}/order-proposals/${proposalId}`);
  }

  /** The customer's own confirmation: the only way an assistant's proposal becomes an order. */
  confirmProposal(proposalId: string, paymentMethod: string): Observable<Proposal> {
    return this.http.post<Proposal>(
      `${ServiceUrls.governance}/order-proposals/${proposalId}/confirm`,
      { paymentMethod },
    );
  }

  /** One customer's most recent messages. */
  customerNotifications(customerEmail: string): Observable<CustomerNotification[]> {
    const params = new HttpParams().set('customerEmail', customerEmail);
    return this.http.get<CustomerNotification[]>(`${ServiceUrls.governance}/notifications`, {
      params,
    });
  }

  /** The messages sent about one order. */
  orderNotifications(orderId: string): Observable<CustomerNotification[]> {
    const params = new HttpParams().set('orderId', orderId);
    return this.http.get<CustomerNotification[]>(`${ServiceUrls.governance}/notifications`, {
      params,
    });
  }

  cases(filter: CaseFilter = {}): Observable<SupportCase[]> {
    let params = new HttpParams();
    if (filter.open) {
      params = params.set('open', true);
    }
    if (filter.orderId) {
      params = params.set('orderId', filter.orderId);
    }
    return this.http.get<SupportCase[]>(`${ServiceUrls.governance}/cases`, { params });
  }

  agents(): Observable<AgentOverview> {
    return this.http.get<AgentOverview>(`${ServiceUrls.agents}/agents`);
  }

  /** Flips an agent's kill switch, on the record of the person who did it. */
  setAgentEnabled(agentId: string, enabled: boolean, changedBy: string): Observable<AgentOverview> {
    return this.http.put<AgentOverview>(`${ServiceUrls.agents}/agents/${agentId}`, {
      enabled,
      by: changedBy,
    });
  }

  chat(conversationId: string, customerEmail: string, message: string): Observable<AssistantReply> {
    return this.http
      .post<{ text: string; proposals: string[] }>(`${ServiceUrls.agents}/assistant/chat`, {
        conversationId,
        customerEmail,
        message,
      })
      .pipe(
        map((reply) => ({
          text: reply.text,
          proposals: reply.proposals.map((json) => JSON.parse(json) as Proposal),
        })),
      );
  }
}

/** Whether a request failed because what it asked for does not exist, rather than because a service is unavailable. */
export function isNotFound(failure: unknown): boolean {
  return (failure as { status?: unknown } | null)?.status === 404;
}

/** The reason a service gave for refusing a request, from its problem details answer. */
export function refusalReason(failure: unknown, fallback: string): string {
  const detail = (failure as { error?: { detail?: unknown } } | null)?.error?.detail;
  return typeof detail === 'string' && detail ? detail : fallback;
}
