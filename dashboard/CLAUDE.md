# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with the CoSky dashboard frontend.

## Commands

```bash
cd dashboard

# Install dependencies
pnpm install

# Run dev server (http://localhost:5173)
pnpm dev

# Build for production
pnpm build

# Lint and auto-fix
pnpm lint

# Generate API clients from OpenAPI spec
pnpm generate
```

## Environment Variables

- `VITE_API_BASE_URL` - Backend API base URL (default: `http://cosky.dev.svc.cluster.local/`)

## Architecture

### Tech Stack
- **Framework**: React 19 with TypeScript
- **Build**: Vite with React Compiler (babel-plugin-react-compiler)
- **UI**: Ant Design 6.x
- **Routing**: React Router v7
- **Topology**: @xyflow/react for service topology visualization
- **Config Editor**: Monaco Editor
- **API Client**: @ahoo-wang/fetcher with auto-generated clients

### API Layer (`src/generated/`)
Auto-generated API clients from the REST API OpenAPI spec:
- `ConfigApiClient` - Configuration CRUD and version management
- `ServiceApiClient` - Service registry and discovery
- `NamespaceApiClient` - Namespace management
- `UserApiClient`, `RoleApiClient` - RBAC user/role management
- `AuditLogApiClient` - Audit log queries
- `StatApiClient` - Service statistics
- `AuthenticateApiClient` - Authentication

### Client Setup (`src/services/`)
- `fetcher.ts` - Fetcher configuration with CoSec security, token refresh, and 2-minute timeout
- `clients.ts` - API client instances and React hooks created via `createExecuteApiHooks`

### State Management (`src/contexts/`)
- `NamespacesProvider/NamespacesContext` - Manages available namespaces list
- `CurrentNamespaceProvider/CurrentNamespaceContext` - Current selected namespace
- `DrawerProvider/DrawerContext` - Ant Design drawer for detail panels

### Pages (`src/pages/`)
Lazy-loaded routes: `dashboard`, `config`, `service`, `namespace`, `user`, `role`, `audit`

### Routing (`AppRoutes.tsx`)
Protected routes wrapped in `NamespacesProvider` → `DrawerProvider` → `CurrentNamespaceProvider` → `ProtectedRoute` → `AuthenticatedLayout`

### Security
- Token storage via `src/security/tokenStorage.ts`
- `SecurityProvider` from `@ahoo-wang/fetcher-react` handles auth state
- `ProtectedRoute` checks authentication before rendering

## Code Style

ESLint configuration in `eslint.config.js`:
- TypeScript recommended rules
- React Hooks rules
- React Refresh rules
- React Compiler rules
- `typescript-eslint/consistent-type-imports` with `prefer: "type-imports"`
