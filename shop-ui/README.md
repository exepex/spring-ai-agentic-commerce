# shop-ui

The demo's Angular app: the shop with the shopping assistant, the customer's orders and messages, every order with its
audit timeline, and the operations console (refund approvals, failed refunds, cases, shipping, the agents' kill
switches, activity and demo controls).

```
src/app/
  core/       the API client, the models, the session, polling, and every label the UI shows
  shared/     building blocks every page uses: panel, status badge, icon, empty state, case card, audit timeline
  features/
    shop/         the catalog and the assistant chat, with the proposal a customer confirms
    my-orders/    the signed-in customer's orders and messages
    orders/       all orders, and one order end to end
    operations/   the console: a store shared by its tabs, one component per tab
```

Design tokens (colours for light and dark, spacing, type) and the shared primitives (buttons, fields, badges, cards,
callouts, tables) live in `src/styles.scss`; components hold only their own layout.

```bash
npm ci
npm start   # http://localhost:4200, proxying /svc/<service> to the services on localhost (proxy.conf.json)
```

In Docker, nginx serves the built app and routes `/svc/<service>/` to each service (`nginx.conf`).
