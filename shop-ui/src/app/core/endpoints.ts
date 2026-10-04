/** Every service is reached through the same origin: nginx in Docker, or the dev proxy, routes /svc/<service> to it. */
export const ServiceUrls = {
  catalog: '/svc/catalog/api',
  orders: '/svc/orders/api',
  payments: '/svc/payments/api',
  shipping: '/svc/shipping/api',
  governance: '/svc/governance/api',
  agents: '/svc/agents/api',
} as const;

/** The Jaeger UI, for links from the audit trail to each step's distributed trace. */
export const JAEGER_URL = 'http://localhost:16686';
