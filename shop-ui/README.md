# shop-ui

The demo's Angular app: the shop with the shopping assistant, the orders with their audit timeline, and the
operations console (approvals, failed refunds, cases, the agents' kill switches and demo controls).

```bash
npm ci
npm start   # http://localhost:4200, proxying /svc/<service> to the services on localhost (proxy.conf.json)
```

In Docker, nginx serves the built app and routes `/svc/<service>/` to each service (`nginx.conf`).
