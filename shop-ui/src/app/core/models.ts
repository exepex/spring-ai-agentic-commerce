/** What the services return, as the UI reads it. Each type mirrors one service's JSON contract. */

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

export type OrderStatus =
  | 'PLACED'
  | 'PAYMENT_PENDING'
  | 'CONFIRMED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'DELIVERY_FAILED'
  | 'LOST';

export interface Order {
  id: string;
  customerEmail: string;
  status: OrderStatus;
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
  status: 'SUCCEEDED' | 'DECLINED';
  provider: string;
  providerReference: string;
  failureMessage: string | null;
  refunds: Refund[];
}

/** What the carrier can report about a shipped parcel. */
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

export type ActorType = 'AGENT' | 'HUMAN' | 'SYSTEM';

/** One step in the audit trail: who or what acted, how it turned out, and why. */
export interface AuditEvent {
  id: string;
  occurredAt: string;
  orderId: string | null;
  actorType: ActorType;
  actor: string;
  action: string;
  outcome: 'SUCCEEDED' | 'FAILED' | 'DENIED' | 'PENDING_APPROVAL' | 'REJECTED';
  summary: string;
  details: string | null;
  traceId: string | null;
}

export type RefundDecision = 'approve' | 'reject' | 'retry';

/** A refund an agent asked for; above the approval limit it waits for a person. */
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

/** An order the assistant put together; only the customer's own confirmation places it. */
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

/** A message the shop or an agent sent to a customer. */
export interface CustomerNotification {
  id: string;
  orderId: string | null;
  customerEmail: string;
  message: string;
  sentBy: string;
  createdAt: string;
}

export type CaseType =
  'STOCK_OUT' | 'DELIVERY_FAILED' | 'PARCEL_LOST' | 'REFUND_FAILED' | 'HANDOFF' | 'SERVICE_DESK';

/** A problem the shop opened a case for, worked as a ServiceNow incident. */
export interface SupportCase {
  id: string;
  orderId: string | null;
  type: CaseType;
  /** PENDING until its incident is in ServiceNow; then with the incident agent, with a team, or resolved. */
  status: 'PENDING' | 'WITH_AGENT' | 'WITH_TEAM' | 'RESOLVED';
  title: string;
  description: string;
  raisedBy: string;
  incidentNumber: string | null;
  incidentUrl: string | null;
  /** The ServiceNow group that has the incident, once it is read back. */
  assignmentGroup: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AgentStatus {
  id: string;
  /** null when the kill switches cannot be read (the MCP server is down). */
  enabled: boolean | null;
  model: string;
  effort: string;
  tools: string[];
}

/** The agents and the outside systems they connect to. */
export interface AgentOverview {
  modelConfigured: boolean;
  slackConfigured: boolean;
  servicenowConfigured: boolean;
  agents: AgentStatus[];
}

export interface AssistantReply {
  text: string;
  proposals: Proposal[];
}
