# CoSky Dashboard

React 19 dashboard for CoSky microservice governance. The UI is built with shadcn/ui on Base UI and Tailwind CSS 4, with Monaco for configuration editing and React Flow for service topology.

## Development

```bash
pnpm install
pnpm dev
```

The page expects the API on its own origin: `pnpm dev` proxies `/v1` to a server on `:8080` (or `$COSKY_API_URL`), such as `cosky-server --dev`.
Set `VITE_ENVIRONMENT_NAME` to show an explicit environment or cluster name in the authenticated header.

## Verification

```bash
pnpm lint
pnpm build
pnpm test:unit
pnpm test:ui
pnpm test:coverage
```

`test:ui` runs the Playwright end-to-end suite against an isolated mocked API. It covers authentication, Dashboard, configuration, service instances, namespaces, users, roles, audit logs, and the mobile navigation layout.

`test:coverage` generates HTML and LCOV reports in `coverage/` and enforces 80% statements, functions, and lines plus 75% branches.

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
