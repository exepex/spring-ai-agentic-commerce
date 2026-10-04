import { ActorType, CaseType } from './models';

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

const ACTOR_TYPES: Record<ActorType, string> = {
  AGENT: 'Agent',
  HUMAN: 'Person',
  SYSTEM: 'Service',
};

export function actorTypeLabel(type: ActorType): string {
  return ACTOR_TYPES[type];
}

/** An agent or service id as a name: "incident-agent" reads "Incident agent". People keep their email. */
export function actorName(actor: string): string {
  return actor.includes('@') ? actor : humanize(actor);
}

/** An audit action or tool name as words: "ORDER_SHIPPED" and "servicenow:assign_to_team" read as sentences. */
export function actionLabel(action: string): string {
  return humanize(action.slice(action.indexOf(':') + 1));
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
