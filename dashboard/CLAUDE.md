# CLAUDE.md

Guidance for working on the CoSky dashboard, the React frontend served by `cosky-rest-api`.

## Commands

```bash
cd dashboard
pnpm install
pnpm dev            # http://localhost:5173
pnpm lint           # eslint --fix
pnpm build          # tsc -b && vite build
pnpm test:unit      # Playwright unit tests (tests/unit)
pnpm test:ui        # Playwright end-to-end tests against the mock API (tests/ui/mock-api.ts)
pnpm test:coverage  # enforces 80% statements/functions/lines, 75% branches
pnpm generate       # regenerate src/generated from a REST API on :8080
```

Run `lint`, `build`, `test:unit` and `test:ui` before committing.

## Environment

- `VITE_API_BASE_URL`: REST API base URL (`.env.development`: the dev cluster; `.env.production`: `/`, same origin).
- `VITE_ENVIRONMENT_NAME`: optional environment name shown in the header; defaults to the API host.

## Stack

- React 19 + TypeScript, Vite with the React Compiler, React Router 7.
- UI: shadcn/ui components (`base-nova` style) on **Base UI** (`@base-ui/react`), styled with **Tailwind CSS 4**.
- Monaco for configuration editing, `@xyflow/react` for the service topology, sonner for toasts, lucide icons.
- API: `@ahoo-wang/fetcher` with clients generated from the OpenAPI spec, CoSec token refresh.

## Layout

| Path | Contents |
|---|---|
| `src/generated/` | Generated API clients and types. Never edit by hand; run `pnpm generate`. |
| `src/services/` | `fetcher.ts` (base URL, CoSec, timeout) and `clients.ts` (client instances). |
| `src/security/`, `src/components/security/` | Token storage, `ProtectedRoute`, change password. |
| `src/contexts/` | Namespaces, current namespace, and the detail drawer (`useDrawer`). |
| `src/components/ui/` | shadcn/ui primitives plus shared controls (`confirm-button`, `multi-select`, `options-select`, `data-table`). |
| `src/components/layout/` | Application shell: `AuthenticatedLayout` composes `Sidebar`, `CommandSearch`, `AccountMenu`, `CurrentNamespaceSelector`; menu entries live in `navigation.ts`. |
| `src/lib/actions.ts` | `runAction` and `failureMessage`: toast outcomes with the server's reason. |
| `src/pages/<area>/` | One folder per route, lazy-loaded from `AppRoutes.tsx`. |

`AppRoutes.tsx` wraps signed-in routes in `NamespacesProvider` → `DrawerProvider` → `CurrentNamespaceProvider` → `ProtectedRoute` → `AuthenticatedLayout`.

## Conventions

- **Imports**: use the `@/` alias, without file extensions; use `import type` for types.
- **Styling**: Tailwind utilities only, no stylesheets per component. Colors come from theme tokens in `src/index.css` (`bg-shell`, `text-shell-muted`, `bg-canvas`, …); add a token there instead of a hard-coded color. Shared visual effects go in `index.css` as `@utility`. Merge class names with `cn()`.
- **Base UI**: compose with the `render` prop (not `asChild`); set `nativeButton={false}` when a `Button` renders a link. Menu items use `onClick`; a `DropdownMenuLabel` must sit inside a `DropdownMenuGroup`. Pass `items` to `Select` so the trigger shows labels.
- **Adding components**: `npx shadcn add <name>`, then restore the license header, drop `"use client"`, and import `cn` from `@/lib/utils`.
- **Operations**: wrap a mutation in `runAction(operation, {success, failure})`; in a form's `onError` use `toast.error(await failureMessage('…', error))`. Don't hand-roll try/catch toasts.
- **Tests**: select elements by role, label, or `data-slot`, not by class name. New pages need an end-to-end test in `tests/ui` and a mock in `mock-api.ts`.
