# CLAUDE.md

Guidance for working on the CoSky dashboard, the React frontend the server hosts (design D11). It only uses the public REST API.

## Commands

```bash
cd dashboard
pnpm install
pnpm dev            # http://localhost:5173, with /v1 proxied to a server on :8080 (or $COSKY_API_URL)
pnpm lint           # eslint --fix
pnpm build          # tsc -b && vite build
pnpm test:unit      # Playwright unit tests (tests/unit)
pnpm test:ui        # Playwright end-to-end tests of the production build against the mock API (tests/ui/mock-api.ts)
pnpm test:ui:real   # The hosted page against a real server: see playwright.real.config.ts
pnpm test:coverage  # enforces 80% statements/functions/lines, 75% branches
pnpm generate       # regenerate src/generated from ../api/build/openapi/cosky.yaml (./gradlew :api:generateOpenApi)
```

Run `lint`, `build`, `test:unit` and `test:ui` before committing.

## Environment

- `VITE_ENVIRONMENT_NAME`: optional environment name shown in the header; defaults to the host of the page.

The API always shares the page's origin, since the server has no CORS (design D34). `pnpm build` leaves `dist/`, which the server build bundles. Routes live in the URL fragment, so that no server needs a fallback route.

## Stack

- React 19 + TypeScript, Vite with the React Compiler, React Router 7.
- UI: shadcn/ui components (`base-nova` style) on **Base UI** (`@base-ui/react`), styled with **Tailwind CSS 4**.
- Monaco for configuration editing, `@xyflow/react` for the service topology, sonner for toasts, lucide icons.
- API: `@ahoo-wang/fetcher` with clients generated from the OpenAPI spec; Server-Sent Events for live refresh.

## Layout

| Path | Contents |
|---|---|
| `src/generated/` | Generated API clients and types. Never edit by hand; run `pnpm generate`. |
| `src/services/` | `fetcher.ts` (token, expiry, timeout), `clients.ts` (client instances), `resources.ts` (typed operations and complete models) and `watch.ts` (following changes). Pages use `resources.ts`: proto3 JSON leaves empty fields out, while the generated types call everything required. |
| `src/security/`, `src/components/security/` | The token of this tab (`session.ts`), sign-in with OIDC and PKCE (`oidc.ts`), `ProtectedRoute`. |
| `src/contexts/` | Who is signed in (`useMe`), namespaces, current namespace, and the detail drawer (`useDrawer`). |
| `src/hooks/useWatchRefresh.ts` | Reads a page again when its objects change. Use one stream per page: browsers allow few connections per server over HTTP/1.1. |
| `src/components/ui/` | shadcn/ui primitives plus shared controls (`confirm-button`, `multi-select`, `options-select`, `data-table`). |
| `src/components/layout/` | Application shell: `AuthenticatedLayout` composes `Sidebar`, `CommandSearch`, `AccountMenu`, `CurrentNamespaceSelector`; menu entries live in `navigation.ts`. |
| `src/lib/actions.ts` | `runAction` and `failureMessage`: toast outcomes with the server's reason. |
| `src/pages/<area>/` | One folder per route, lazy-loaded from `AppRoutes.tsx`. |

`AppRoutes.tsx` wraps signed-in routes in `ProtectedRoute` → `NamespacesProvider` → `DrawerProvider` → `CurrentNamespaceProvider` → `AuthenticatedLayout`.

## Conventions

- **Imports**: use the `@/` alias, without file extensions; use `import type` for types.
- **Styling**: Tailwind utilities only, no stylesheets per component. Colors come from theme tokens in `src/index.css` (`bg-shell`, `text-shell-muted`, `bg-canvas`, …); add a token there instead of a hard-coded color. Shared visual effects go in `index.css` as `@utility`. Merge class names with `cn()`.
- **Base UI**: compose with the `render` prop (not `asChild`); set `nativeButton={false}` when a `Button` renders a link. Menu items use `onClick`; a `DropdownMenuLabel` must sit inside a `DropdownMenuGroup`. Pass `items` to `Select` so the trigger shows labels.
- **Adding components**: `npx shadcn add <name>`, then restore the license header, drop `"use client"`, and import `cn` from `@/lib/utils`.
- **Operations**: wrap a mutation in `runAction(operation, {success, failure})`. Only an operation whose result you need uses try/catch, with `toast.error(await failureMessage('…', error))`.
- **Edits**: send the revision the edit started from (`If-Match`), and create with none (`If-None-Match: *`), so that nobody overwrites a change they did not see. `resources.ts` does this when given the revision.
- **Tests**: select elements by role, label, or `data-slot`, not by class name. New pages need an end-to-end test in `tests/ui` and a mock in `mock-api.ts`.
