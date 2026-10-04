import { ActorType, AuditEvent, CaseType } from './models';

/** How a status reads at a glance: the colour its badge gets. */
export type StatusTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral';

interface StatusPresentation {
  label: string;
  tone: StatusTone;
}

/**
 * Every status the services send, in the words a customer or an operator would use. The enums are shared across
 * orders, payments, refunds, shipments, cases, proposals and audit outcomes, so one table covers them all.
 */
const STATUSES: Record<string, StatusPresentation> = {
  PLACED: { label: 'Placed', tone: 'info' },
  PAYMENT_PENDING: { label: 'Awaiting payment', tone: 'warning' },
  CONFIRMED: { label: 'Paid', tone: 'success' },
  PAYMENT_FAILED: { label: 'Payment failed', tone: 'danger' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
  PREPARING: { label: 'Preparing', tone: 'info' },
  SHIPPED: { label: 'Shipped', tone: 'info' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  DELIVERY_FAILED: { label: 'Delivery failed', tone: 'danger' },
  LOST: { label: 'Lost', tone: 'danger' },
  SUCCEEDED: { label: 'Succeeded', tone: 'success' },
  DECLINED: { label: 'Declined', tone: 'danger' },
  PENDING: { label: 'Pending', tone: 'warning' },
  FAILED: { label: 'Failed', tone: 'danger' },
  DENIED: { label: 'Denied', tone: 'danger' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
  PENDING_APPROVAL: { label: 'Needs approval', tone: 'warning' },
  EXECUTED: { label: 'Refunded', tone: 'success' },
  WITH_AGENT: { label: 'With agent', tone: 'info' },
  WITH_TEAM: { label: 'With team', tone: 'warning' },
  RESOLVED: { label: 'Resolved', tone: 'success' },
  PROPOSED: { label: 'Ready to confirm', tone: 'warning' },
  CONFIRMING: { label: 'Placing order', tone: 'info' },
};

export function statusLabel(status: string): string {
  return STATUSES[status]?.label ?? humanize(status);
}

export function statusTone(status: string): StatusTone {
  return STATUSES[status]?.tone ?? 'neutral';
}

const CASE_TYPES: Record<CaseType, string> = {
  STOCK_OUT: 'Stock-out',
  DELIVERY_FAILED: 'Failed delivery',
  PARCEL_LOST: 'Lost parcel',
  REFUND_FAILED: 'Failed refund',
  HANDOFF: 'Hand-off',
  SERVICE_DESK: 'Service desk',
};

export function caseTypeLabel(type: CaseType): string {
  return CASE_TYPES[type] ?? humanize(type);
}

/** How a step the shop's own services took is attributed. */
const AUTOMATIC = 'Automatic';

/** Outside systems the back office knows by name. */
const SYSTEM_NAMES: Record<string, string> = { servicenow: 'ServiceNow' };

/**
 * Who did something, for the back office: an agent by its name ("Incident agent"), a person by their email, ServiceNow
 * by name, and a step one of the shop's own services took as "Automatic". Which internal service it was matters to
 * engineers, not to the people working the case, so it stays in the step's technical details.
 */
export function actorName(actor: string, type?: ActorType): string {
  if (actor.includes('@')) {
    return actor;
  }
  if (SYSTEM_NAMES[actor]) {
    return SYSTEM_NAMES[actor];
  }
  return type === 'SYSTEM' || isAutomatic(actor) ? AUTOMATIC : humanize(actor);
}

/** Whether an actor is one of the shop's own services, rather than an agent, a person or ServiceNow. */
export function isAutomatic(actor: string): boolean {
  return actor.endsWith('-service');
}

/** What each audit action and agent tool means, in words; anything not listed reads as its name. */
const ACTIONS: Record<string, string> = {
  ORDER_CONFIRMED: 'Order paid',
  ORDER_SHIPPED: 'Order shipped',
  ORDER_CANCELLED: 'Order cancelled',
  SHIPMENT_DELIVERED: 'Parcel delivered',
  SHIPMENT_DELIVERY_FAILED: 'Delivery failed',
  SHIPMENT_LOST: 'Parcel lost',
  STOCK_OUT: 'Stock-out',
  REFUND_FAILED: 'Refund failed',
  refund_failed: 'Refund failed',
  raise_case: 'Case opened',
  open_incident: 'Incident opened',
  follow_incident: 'Incident updated',
  switch_on_agent: 'Agent switched on',
  switch_off_agent: 'Agent switched off',
  confirm_order: 'Order confirmed by the customer',
  approve_refund: 'Refund approved',
  reject_refund: 'Refund rejected',
  issue_refund: 'Refund issued',
  cancel_order: 'Order cancelled',
  notify_customer: 'Customer notified',
  propose_order: 'Order proposed',
  escalate_to_human: 'Handed to a person',
  assign_to_team: 'Assigned to a team',
  resolve_incident: 'Incident resolved',
  add_work_note: 'Work note added',
  track_shipment: 'Parcel checked',
  get_order: 'Order looked up',
  find_customer_orders: "Customer's orders looked up",
  search_products: 'Products searched',
  get_incident: 'Incident read',
  list_teams: 'Teams listed',
  conversations_add_message: 'Posted to Slack',
};

/**
 * An audit action or tool name as words: "ORDER_SHIPPED" reads "Order shipped", "raise_case" reads "Case opened". A
 * step that did not succeed names what was attempted instead ("Cancel order", then Denied), so it never reads as done.
 */
export function actionLabel(action: string, outcome?: AuditEvent['outcome']): string {
  const name = action.slice(action.indexOf(':') + 1);
  const attempted = outcome !== undefined && outcome !== 'SUCCEEDED';
  return (attempted ? undefined : ACTIONS[name]) ?? humanize(name);
}

/** The MCP server a tool lives on, from its prefix; tools without one are the shop's own. */
export function toolServer(tool: string): string {
  const separator = tool.indexOf(':');
  return separator < 0
    ? 'Commerce'
    : (TOOL_SERVERS[tool.slice(0, separator)] ?? humanize(tool.slice(0, separator)));
}

const TOOL_SERVERS: Record<string, string> = { servicenow: 'ServiceNow', slack: 'Slack' };

/** SOME_ENUM, some_name or some-name as "Some enum", "Some name". */
export function humanize(value: string): string {
  const words = value.replace(/[_-]+/g, ' ').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** The first eight characters of an id: enough to tell orders apart on screen. */
export function shortId(id: string): string {
  return id.slice(0, 8);
}

/** Two letters for an avatar, from an email's local part. */
export function initials(email: string): string {
  const name = email.split('@')[0];
  return name.slice(0, 2).toUpperCase();
}

/** "Opened automatically" or "Opened by Incident agent": how a record says who made it. */
export function byline(verb: string, actor: string): string {
  return isAutomatic(actor) ? `${verb} automatically` : `${verb} by ${actorName(actor)}`;
}
