# CoSky Dashboard

React 19 dashboard for CoSky microservice governance. The UI is built with Radix UI and shadcn/ui, with Monaco for configuration editing and React Flow for service topology.

## Development

```bash
pnpm install
pnpm dev
```

Set `VITE_API_BASE_URL` to override the CoSky REST API base URL.

## Verification

```bash
pnpm lint
pnpm build
pnpm test:ui
```

`test:ui` runs the Playwright end-to-end suite against an isolated mocked API. It covers authentication, Dashboard, configuration, service instances, namespaces, users, roles, audit logs, and the mobile navigation layout.

Run the production-contract suite against an isolated CoSky REST API and Redis instance with:

```bash
COSKY_REAL_E2E=1 \
COSKY_REAL_API_URL=http://127.0.0.1:18080/ \
COSKY_REAL_USERNAME=cosky \
COSKY_REAL_PASSWORD='<isolated test password>' \
pnpm test:ui:real
```

The real suite uses unique resource names, exercises every mutation through the UI, and removes the resources it creates.

## API Client Generation

With the REST API running on port 8080:

```bash
pnpm generate
```
