// A load test of the shop's hot paths through nginx, as the browser and the shop's MCP server use them:
// browsing the catalog, placing orders (checkout: reserve stock, charge, announce), and reading orders back.
//
//   docker run --rm -i --network host -e BASE_URL=http://localhost:8080/svc \
//     -e INTERNAL_API_TOKEN=dev-internal-api-token grafana/k6 run - < load-tests/shop.js
//
// Run it against the demo with simulated payments (no Stripe key): it places thousands of orders.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/svc';
const TOKEN = __ENV.INTERNAL_API_TOKEN || 'dev-internal-api-token';
const PRODUCTS = ['8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0003', '8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004'];
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    browse: {
      executor: 'constant-arrival-rate',
      exec: 'browse',
      rate: Number(__ENV.BROWSE_RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
    checkout: {
      executor: 'constant-arrival-rate',
      exec: 'checkout',
      rate: Number(__ENV.CHECKOUT_RATE || 50),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    // An answer can be a success and still wrong (an order left PAYMENT_PENDING): the checks must pass too, and the
    // checkout's own, which the far more numerous browsing checks would otherwise hide.
    'checks': ['rate>0.99'],
    'checks{check:order confirmed}': ['rate>0.99'],
    // When every VU is busy, k6 stops starting iterations: the offered load was not sustained. None may be dropped,
    // whatever the rate and duration (a dropped iteration runs no checks, so no other threshold would notice).
    'dropped_iterations': ['count<1'],
    'http_req_duration{name:products}': ['p(95)<300'],
    'http_req_duration{name:place order}': ['p(95)<1000'],
  },
};

// Enough stock for the whole run, so every order can be placed.
export function setup() {
  for (const productId of PRODUCTS) {
    const added = http.post(
      `${BASE_URL}/catalog/api/products/${productId}/stock-adjustments`,
      JSON.stringify({ delta: 1000000, reason: 'load test' }),
      { headers: JSON_HEADERS },
    );
    check(added, { 'stock added': (response) => response.status === 200 });
  }
}

export function browse() {
  const products = http.get(`${BASE_URL}/catalog/api/products`, { tags: { name: 'products' } });
  check(products, { 'products listed': (response) => response.status === 200 });
}

export function checkout() {
  const customer = `load-${__VU % 500}@example.com`;
  const placed = http.post(
    `${BASE_URL}/orders/api/orders`,
    JSON.stringify({
      customerEmail: customer,
      lines: [{ productId: PRODUCTS[__ITER % PRODUCTS.length], quantity: 1 }],
    }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${TOKEN}` }, tags: { name: 'place order' } },
  );
  check(placed, {
    'order confirmed': (response) => response.status === 201 && response.json('status') === 'CONFIRMED',
  });
  if (placed.status === 201) {
    const order = http.get(`${BASE_URL}/orders/api/orders/${placed.json('id')}`, { tags: { name: 'order' } });
    check(order, { 'order read back': (response) => response.status === 200 });
  }
  const orders = http.get(`${BASE_URL}/orders/api/orders?customerEmail=${encodeURIComponent(customer)}`, {
    tags: { name: "customer's orders" },
  });
  check(orders, { "customer's orders listed": (response) => response.status === 200 });
}
