# Dashboard Radix UI + shadcn/ui 重写实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `dashboard/` 的 UI 层从 Ant Design 整体迁移到 Radix UI + shadcn/ui(Tailwind CSS v4)，并落地暗色主题、统一 DataTable、⌘K 命令面板等交互增强。

**Architecture:** 原地页级渐进迁移（spec 方案 A)：先建基建（令牌/主题/toast/布局/Sheet 宿主/DataTable/表单），再按 namespace → audit-log → user → role → service → config → dashboard → login 顺序逐页替换，最后卸载 antd。全程保持 `pnpm lint` / `pnpm build` 绿。

**Tech Stack:** React 19 + Vite 8(React Compiler)+ Tailwind CSS v4 + shadcn/ui(new-york/zinc)+ Radix UI + lucide-react + sonner + cmdk + @tanstack/react-table v8 + react-hook-form + zod。保留：react-router 7、Monaco、@xyflow/react、@ahoo-wang/fetcher* 全家桶、`src/generated/`。

**Spec:** `docs/superpowers/specs/2026-08-16-dashboard-radix-shadcn-rewrite-design.md`

## Global Constraints

- **无测试体系**（spec 决策）：每个任务的质量门禁 = `pnpm lint` 无错误 + `pnpm build`(`tsc -b` 严格模式 + Vite 构建）成功 + 任务内列出的 grep/手动走查项。所有命令工作目录为 `dashboard/`。
- 包管理一律 `pnpm`(10.33);Node ^20.19 / >=22.12。
- **禁止改动**:`src/generated/`、`src/services/`(fetcher/clients)、`src/security/`、路由结构、`@ahoo-wang/fetcher*` 依赖。
- 新建源文件必须带 Apache 2.0 license 头（照抄现有文件头，`Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)]`)。
- UI 文案一律英文。
- shadcn 组件生成到 `src/components/ui/`，该目录被 ESLint 忽略；业务定制写在该目录之外。
- React Compiler 全程开启；若 `eslint-plugin-react-compiler` 对 react-hook-form 表单文件报错，在该文件顶部第一行加 `"use no memo"` 指令豁免（不得全局关闭）。
- 提交规范：Conventional Commits，scope 为 `dashboard`，如 `feat(dashboard): ...`。
- CI 契约不变:`dashboard/` 下 `pnpm install / lint / build` 与 `dist/` 产物路径不动。
- 设计令牌唯一来源是 `src/index.css`(`:root` / `.dark` / `@theme`)；组件一律用语义类（`bg-background`、`text-muted-foreground` 等），禁止散落的面板级内联样式。
- 手动走查需要后端：本地 `../gradlew :cosky-rest-api:bootRun` 或默认 dev 环境（`.env.development` 已指向 `http://cosky.dev.svc.cluster.local/`)。

---

## File Structure

新增/重写后的关键文件布局（未列出的文件保持不动）:

```
dashboard/
├── components.json                     # T0 shadcn 配置
├── vite.config.ts                      # T0 加 tailwindcss() + @/ alias
├── tsconfig.app.json                   # T0 加 baseUrl/paths
├── eslint.config.js                    # T0 忽略 src/components/ui
├── src/
│   ├── index.css                       # T0 Tailwind v4 入口 + 全部设计令牌
│   ├── lib/
│   │   ├── utils.ts                    # T0 cn()
│   │   └── commands.ts                 # T14 命令面板事件总线
│   ├── theme/ThemeProvider.tsx         # T0 ThemeProvider + useTheme
│   ├── components/
│   │   ├── ui/                         # T0+ shadcn 生成物
│   │   ├── feedback/                   # Spinner(T0) / ConfirmDialog / Empty / ErrorState(T4)
│   │   ├── layout/                     # T2 重写:AppSidebar/AppHeader/ThemeToggle/Watermark/
│   │   │                               #      AuthenticatedLayout/PageHeader/DataTableWrapper/
│   │   │                               #      CurrentNamespaceSelector
│   │   ├── namespace/NamespaceSelector.tsx   # T2 重写为 Popover+Command combobox
│   │   ├── table/                      # T4 重写:DataTable/DataTableColumnHeader/
│   │   │                               #   DataTablePagination/DataTableViewOptions/columns/SearchFilter
│   │   ├── form/FileDropzone.tsx       # T11
│   │   ├── command/CommandPalette.tsx  # T14
│   │   └── topology/                   # T12 颜色令牌化
│   ├── contexts/
│   │   ├── DrawerContext.tsx           # T3 重写(去 antd DrawerProps)
│   │   └── DrawerProvider.tsx          # T3 重写(Sheet 宿主 + 拖拽调宽)
│   └── pages/                          # T6-T13 逐页重写
└── src/App.tsx / AppRoutes.tsx         # T1/T2/T15 渐进修改
```

---

### Task 0: 基建 —— Tailwind v4 + 别名 + shadcn + 主题令牌 + ThemeProvider

**Files:**
- Modify: `dashboard/package.json`
- Modify: `dashboard/vite.config.ts`
- Modify: `dashboard/tsconfig.app.json`
- Modify: `dashboard/eslint.config.js`
- Create: `dashboard/components.json`
- Create: `dashboard/src/lib/utils.ts`
- Create: `dashboard/src/theme/ThemeProvider.tsx`
- Create: `dashboard/src/components/feedback/Spinner.tsx`
- Overwrite: `dashboard/src/index.css`
- Modify: `dashboard/src/App.tsx`
- Generate: `dashboard/src/components/ui/*`(shadcn CLI)

**Interfaces:**
- Consumes: 无（首个任务）。
- Produces:
  - `cn(...inputs: ClassValue[]): string` — `@/lib/utils`
  - `ThemeProvider({children})`、`useTheme(): {theme: 'light'|'dark'|'system', resolvedTheme: 'light'|'dark', setTheme(t)}` — `@/theme/ThemeProvider`
  - `Spinner({className?})` — `@/components/feedback/Spinner`
  - 语义令牌类：`bg-background` `text-foreground` `bg-card` `text-muted-foreground` `border-border` `bg-primary` `text-primary-foreground` `bg-destructive` `bg-accent` `ring-ring` 及 `sidebar-*` 系列；品牌渐变 `from-brand-from to-brand-to`。

- [ ] **Step 1: 记录基线包体**

```bash
cd dashboard && pnpm install && pnpm build && du -sh dist && ls -la dist/assets | head -20
```

把总大小记入提交信息或临时笔记，供 Task 15 对比。

- [ ] **Step 2: 安装依赖**

```bash
cd dashboard
pnpm add -D tailwindcss@^4 @tailwindcss/vite@^4
pnpm add class-variance-authority clsx tailwind-merge tw-animate-css lucide-react
```

- [ ] **Step 3: 配置 Vite(插件 + 别名）**

将 `dashboard/vite.config.ts` 整体替换为：

```ts
import {defineConfig} from 'vite'
import react, {reactCompilerPreset} from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import {fileURLToPath, URL} from 'node:url'

const exclude = [/src\/generated/, /node_modules/]

export default defineConfig({
  plugins: [
    tailwindcss(),
    react({
      exclude,
    }),
    babel({
      presets: [reactCompilerPreset()],
      exclude,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    include: ['monaco-editor'],
    exclude: ['@monaco-editor/react'],
  },
})
```

- [ ] **Step 4: 配置 TS 路径别名**

`dashboard/tsconfig.app.json` 的 `compilerOptions` 内追加两行（保持其余不变）:

```json
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    },
```

- [ ] **Step 5: ESLint 忽略 shadcn 生成目录**

`dashboard/eslint.config.js` 第 12 行改为：

```js
        ignores: ['src/generated/**', 'src/components/ui/**'],
```

- [ ] **Step 6: 创建 shadcn 配置文件**

创建 `dashboard/components.json`:

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/index.css",
    "baseColor": "zinc",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

- [ ] **Step 7: 创建 `cn()` 工具**

创建 `dashboard/src/lib/utils.ts`（带 Apache 头，下同，不再重复提示）:

```ts
import {clsx, type ClassValue} from "clsx"
import {twMerge} from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs))
}
```

- [ ] **Step 8: 覆写 `src/index.css` 为令牌体系**

用以下内容**整体替换** `dashboard/src/index.css`(zinc 中性色 + 品牌紫 primary,#667eea ≈ `oklch(0.585 0.181 277)`,#764ba2 ≈ `oklch(0.48 0.16 295)`):

```css
@import "tailwindcss";
@import "tw-animate-css";

@custom-variant dark (&:is(.dark *));

:root {
  --radius: 0.5rem;
  --background: oklch(0.985 0 0);
  --foreground: oklch(0.141 0.005 285.823);
  --card: oklch(1 0 0);
  --card-foreground: oklch(0.141 0.005 285.823);
  --popover: oklch(1 0 0);
  --popover-foreground: oklch(0.141 0.005 285.823);
  --primary: oklch(0.585 0.181 277);
  --primary-foreground: oklch(0.985 0 0);
  --secondary: oklch(0.967 0.001 286.375);
  --secondary-foreground: oklch(0.21 0.006 285.885);
  --muted: oklch(0.967 0.001 286.375);
  --muted-foreground: oklch(0.552 0.016 285.938);
  --accent: oklch(0.967 0.001 286.375);
  --accent-foreground: oklch(0.21 0.006 285.885);
  --destructive: oklch(0.577 0.245 27.325);
  --destructive-foreground: oklch(0.985 0 0);
  --border: oklch(0.92 0.004 286.32);
  --input: oklch(0.92 0.004 286.32);
  --ring: oklch(0.585 0.181 277);
  --chart-1: oklch(0.585 0.181 277);
  --chart-2: oklch(0.48 0.16 295);
  --chart-3: oklch(0.398 0.07 227.392);
  --chart-4: oklch(0.828 0.189 84.429);
  --chart-5: oklch(0.769 0.188 70.08);
  --sidebar: oklch(0.985 0 0);
  --sidebar-foreground: oklch(0.141 0.005 285.823);
  --sidebar-primary: oklch(0.585 0.181 277);
  --sidebar-primary-foreground: oklch(0.985 0 0);
  --sidebar-accent: oklch(0.967 0.001 286.375);
  --sidebar-accent-foreground: oklch(0.21 0.006 285.885);
  --sidebar-border: oklch(0.92 0.004 286.32);
  --sidebar-ring: oklch(0.585 0.181 277);
  --brand-from: oklch(0.585 0.181 277);
  --brand-to: oklch(0.48 0.16 295);
}

.dark {
  --background: oklch(0.141 0.005 285.823);
  --foreground: oklch(0.985 0 0);
  --card: oklch(0.21 0.006 285.885);
  --card-foreground: oklch(0.985 0 0);
  --popover: oklch(0.21 0.006 285.885);
  --popover-foreground: oklch(0.985 0 0);
  --primary: oklch(0.708 0.152 277);
  --primary-foreground: oklch(0.141 0.005 285.823);
  --secondary: oklch(0.274 0.006 286.033);
  --secondary-foreground: oklch(0.985 0 0);
  --muted: oklch(0.274 0.006 286.033);
  --muted-foreground: oklch(0.705 0.015 286.067);
  --accent: oklch(0.274 0.006 286.033);
  --accent-foreground: oklch(0.985 0 0);
  --destructive: oklch(0.704 0.191 22.216);
  --destructive-foreground: oklch(0.985 0 0);
  --border: oklch(1 0 0 / 10%);
  --input: oklch(1 0 0 / 15%);
  --ring: oklch(0.708 0.152 277);
  --chart-1: oklch(0.708 0.152 277);
  --chart-2: oklch(0.63 0.14 295);
  --chart-3: oklch(0.769 0.188 70.08);
  --chart-4: oklch(0.627 0.265 303.9);
  --chart-5: oklch(0.645 0.246 16.439);
  --sidebar: oklch(0.21 0.006 285.885);
  --sidebar-foreground: oklch(0.985 0 0);
  --sidebar-primary: oklch(0.708 0.152 277);
  --sidebar-primary-foreground: oklch(0.985 0 0);
  --sidebar-accent: oklch(0.274 0.006 286.033);
  --sidebar-accent-foreground: oklch(0.985 0 0);
  --sidebar-border: oklch(1 0 0 / 10%);
  --sidebar-ring: oklch(0.708 0.152 277);
  --brand-from: oklch(0.708 0.152 277);
  --brand-to: oklch(0.63 0.14 295);
}

@theme inline {
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-destructive-foreground: var(--destructive-foreground);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --color-chart-1: var(--chart-1);
  --color-chart-2: var(--chart-2);
  --color-chart-3: var(--chart-3);
  --color-chart-4: var(--chart-4);
  --color-chart-5: var(--chart-5);
  --color-sidebar: var(--sidebar);
  --color-sidebar-foreground: var(--sidebar-foreground);
  --color-sidebar-primary: var(--sidebar-primary);
  --color-sidebar-primary-foreground: var(--sidebar-primary-foreground);
  --color-sidebar-accent: var(--sidebar-accent);
  --color-sidebar-accent-foreground: var(--sidebar-accent-foreground);
  --color-sidebar-border: var(--sidebar-border);
  --color-sidebar-ring: var(--sidebar-ring);
  --color-brand-from: var(--brand-from);
  --color-brand-to: var(--brand-to);
}

@layer base {
  * {
    @apply border-border outline-ring/50;
  }
  body {
    @apply bg-background text-foreground;
  }
  #root {
    @apply w-full h-screen;
  }
}
```

- [ ] **Step 9: 创建 ThemeProvider**

创建 `dashboard/src/theme/ThemeProvider.tsx`:

```tsx
import {createContext, useContext, useEffect, useState, type ReactNode} from 'react';

export type Theme = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'cosky:theme';

export interface ThemeContextType {
    theme: Theme;
    resolvedTheme: ResolvedTheme;
    setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

function getSystemTheme(): ResolvedTheme {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({children}: { children: ReactNode }) {
    const [theme, setTheme] = useState<Theme>(
        () => (localStorage.getItem(THEME_STORAGE_KEY) as Theme | null) ?? 'system',
    );
    const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(getSystemTheme);

    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const listener = (e: MediaQueryListEvent) => setSystemTheme(e.matches ? 'dark' : 'light');
        media.addEventListener('change', listener);
        return () => media.removeEventListener('change', listener);
    }, []);

    const resolvedTheme: ResolvedTheme = theme === 'system' ? systemTheme : theme;

    useEffect(() => {
        document.documentElement.classList.toggle('dark', resolvedTheme === 'dark');
        localStorage.setItem(THEME_STORAGE_KEY, theme);
    }, [theme, resolvedTheme]);

    return (
        <ThemeContext.Provider value={{theme, resolvedTheme, setTheme}}>
            {children}
        </ThemeContext.Provider>
    );
}

export function useTheme(): ThemeContextType {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within ThemeProvider');
    }
    return context;
}
```

- [ ] **Step 10: 创建 Spinner**

创建 `dashboard/src/components/feedback/Spinner.tsx`:

```tsx
import {Loader2} from 'lucide-react';
import {cn} from '@/lib/utils';

export function Spinner({className}: { className?: string }) {
    return <Loader2 className={cn('h-8 w-8 animate-spin text-muted-foreground', className)}/>;
}
```

- [ ] **Step 11: 挂载 ThemeProvider（纯增量，不动 antd)**

`dashboard/src/App.tsx`：在 import 区加 `import {ThemeProvider} from './theme/ThemeProvider.tsx';`，并把 `App()` 的返回结构最外层包一层：

```tsx
    return (
        <ThemeProvider>
            <ConfigProvider
                ...原样保留...
            >
                <AntdApp>
                    <ErrorBoundary>
                        <BrowserRouter>
                            <AppRoutes/>
                        </BrowserRouter>
                    </ErrorBoundary>
                </AntdApp>
            </ConfigProvider>
        </ThemeProvider>
    );
```

- [ ] **Step 12: 生成首批 shadcn 组件**

```bash
cd dashboard
pnpm dlx shadcn@latest add --yes button input skeleton separator card badge
```

验证 `src/components/ui/` 下出现 `button.tsx input.tsx skeleton.tsx separator.tsx card.tsx badge.tsx`。

- [ ] **Step 13: 门禁 + 提交**

```bash
cd dashboard
pnpm lint
pnpm build
grep -rn "antd" src/App.tsx   # 应仍能看到 antd(本任务不删)
```

预期：lint 无错误、build 成功；`pnpm dev` 打开后页面仍是 antd 外观（preflight 可能造成细节差异，可接受）。

```bash
git add -A dashboard && git commit -m "feat(dashboard): scaffold Tailwind v4 + shadcn/ui infrastructure with theme tokens"
```

---

### Task 1: toast —— sonner 全量替换 `message`

**Files:**
- Modify: `dashboard/package.json`（加 sonner)
- Modify: `dashboard/src/App.tsx`（挂 Toaster)
- Modify: 所有含 `App.useApp()` 的文件（执行 Step 1 的 grep 得到清单；探索期已知约 12 个：`pages/login/LoginPage.tsx`、`pages/config/*`、`pages/service/*`、`pages/namespace/*`、`pages/user/*`、`pages/role/*`、`pages/audit/*`、`components/security/ChangePwd.tsx` 等）

**Interfaces:**
- Consumes: `ThemeProvider`/`useTheme`(Task 0)。
- Produces: `toast.success(msg)` / `toast.error(msg)`(`sonner`）为全局唯一反馈 API;`AppToaster`（挂在 App 内，随主题）。

- [ ] **Step 1: 列出所有替换点**

```bash
cd dashboard
grep -rln "App.useApp\|useApp()" src
```

- [ ] **Step 2: 安装并挂载 Toaster**

```bash
pnpm add sonner
```

`dashboard/src/App.tsx` 修改：加 imports 与 `ThemedToaster` 组件，并把它放进 `ThemeProvider` 内层（`ConfigProvider` 外）:

```tsx
import {Toaster} from 'sonner';
import {ThemeProvider, useTheme} from './theme/ThemeProvider.tsx';

function ThemedToaster() {
    const {resolvedTheme} = useTheme();
    return <Toaster richColors closeButton position="top-center" theme={resolvedTheme}/>;
}
```

`App()` 返回结构变为：

```tsx
    return (
        <ThemeProvider>
            <ThemedToaster/>
            <ConfigProvider ...原样... >
                ...
            </ConfigProvider>
        </ThemeProvider>
    );
```

- [ ] **Step 3: 逐文件机械替换**

对 Step 1 清单中的每个文件：

1. 删除 `const {message} = App.useApp()`（或 `App.useApp()` 的解构行）;
2. 从 antd import 中移除 `App`（若该文件不再使用）;
3. 添加 `import {toast} from 'sonner';`;
4. `message.success(` → `toast.success(`;`message.error(` → `toast.error(`；其余 `message.xxx(` 同理映射（`warning` → `toast.warning`)。

- [ ] **Step 4: 门禁 + 提交**

```bash
cd dashboard
! grep -rn "useApp" src          # 预期无输出
pnpm lint && pnpm build
```

```bash
git add -A dashboard && git commit -m "refactor(dashboard): replace antd message with sonner toast"
```

---

### Task 2: 布局 —— Sidebar + Header + ThemeToggle + Watermark + 命名空间选择器

**Files:**
- Create: `dashboard/src/components/layout/AppSidebar.tsx`
- Create: `dashboard/src/components/layout/AppHeader.tsx`
- Create: `dashboard/src/components/layout/ThemeToggle.tsx`
- Create: `dashboard/src/components/layout/Watermark.tsx`
- Overwrite: `dashboard/src/components/layout/AuthenticatedLayout.tsx`
- Overwrite: `dashboard/src/components/layout/PageHeader.tsx`
- Overwrite: `dashboard/src/components/layout/DataTableWrapper.tsx`
- Overwrite: `dashboard/src/components/layout/CurrentNamespaceSelector.tsx`
- Overwrite: `dashboard/src/components/namespace/NamespaceSelector.tsx`
- Modify: `dashboard/src/AppRoutes.tsx`(fallback 换 Spinner)
- Modify: `dashboard/src/components/security/ProtectedRoute.tsx`(Skeleton → Spinner)
- Generate: `sidebar dropdown-menu popover command badge collapsible`(shadcn CLI，自动连带 sheet/tooltip/dialog/cmdk 等依赖组件）

**Interfaces:**
- Consumes: `cn`、`Spinner`、`useTheme`(Task 0);`useLayoutCollapsed()`(既有，返回 `[boolean, (v:boolean)=>void]`);`useSecurityContext()`(fetcher-react，提供 `currentUser.sub`、`signOut`);`useDrawer()`(Task 3 前仍是 antd 实现，接口不变）;`useNamespacesContext()` → `{namespaces: string[], loading: boolean, refresh()}`;`useCurrentNamespaceContext()` → `{currentNamespace: string, setCurrent(ns), reset()}`。
- Produces:
  - `NamespaceSelector({value?, onChange?, placeholder?, className?, disabled?})` — combobox，后续编辑器复用
  - `PageHeader({title: string, actions?: ReactNode})` — **移除 `spaceProps`**
  - `Watermark({content: string, children})`
  - `ThemeToggle()`、`AppSidebar()`、`AppHeader()`

- [ ] **Step 1: 生成 shadcn 组件**

```bash
cd dashboard
pnpm dlx shadcn@latest add --yes sidebar dropdown-menu popover command badge collapsible
```

- [ ] **Step 2: 重写 NamespaceSelector 为可搜索 combobox**

整体替换 `dashboard/src/components/namespace/NamespaceSelector.tsx`:

```tsx
import {useState} from 'react';
import {Check, ChevronsUpDown} from 'lucide-react';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList} from '@/components/ui/command';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';

export interface NamespaceSelectorProps {
    value?: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

export function NamespaceSelector({value, onChange, placeholder = 'Select Namespace', className, disabled}: NamespaceSelectorProps) {
    const {namespaces, loading} = useNamespacesContext();
    const [open, setOpen] = useState(false);
    return (
        <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button variant="outline" role="combobox" aria-expanded={open} disabled={disabled || loading}
                        className={cn('justify-between font-normal', className)}>
                    <span className="truncate">{value ?? placeholder}</span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50"/>
                </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
                <Command>
                    <CommandInput placeholder="Search namespace..."/>
                    <CommandList>
                        <CommandEmpty>No namespace found.</CommandEmpty>
                        <CommandGroup>
                            {namespaces.map((ns) => (
                                <CommandItem key={ns} value={ns}
                                             onSelect={(current) => {
                                                 onChange?.(current);
                                                 setOpen(false);
                                             }}>
                                    <Check className={cn('mr-2 h-4 w-4', value === ns ? 'opacity-100' : 'opacity-0')}/>
                                    {ns}
                                </CommandItem>
                            ))}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    );
}
```

- [ ] **Step 3: 重写 CurrentNamespaceSelector**

整体替换 `dashboard/src/components/layout/CurrentNamespaceSelector.tsx`:

```tsx
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {NamespaceSelector} from '@/components/namespace/NamespaceSelector';

export function CurrentNamespaceSelector() {
    const {currentNamespace, setCurrent} = useCurrentNamespaceContext();
    return (
        <NamespaceSelector
            value={currentNamespace}
            onChange={setCurrent}
            className="w-[220px]"
        />
    );
}
```

- [ ] **Step 4: 创建 AppSidebar**

创建 `dashboard/src/components/layout/AppSidebar.tsx`（图标映射：Dashboard→LayoutDashboard、Configuration→FileText、Service→Server、Namespace→Network、Security→ShieldCheck、User→Users、Role→KeyRound、Audit Log→ScrollText):

```tsx
import {Link, NavLink, useLocation} from 'react-router-dom';
import {FileText, KeyRound, LayoutDashboard, Network, ScrollText, Server, ShieldCheck, Users, Github} from 'lucide-react';
import {
    Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent,
    SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem,
    SidebarMenuSub, SidebarMenuSubButton, SidebarMenuSubItem, useSidebar,
} from '@/components/ui/sidebar';
import {Collapsible, CollapsibleContent, CollapsibleTrigger} from '@/components/ui/collapsible';
import CoskyLogo from '@/assets/cosky-logo-constellation.svg';

const topItems = [
    {path: '/home', label: 'Dashboard', icon: LayoutDashboard},
    {path: '/config', label: 'Configuration', icon: FileText},
    {path: '/service', label: 'Service', icon: Server},
    {path: '/namespace', label: 'Namespace', icon: Network},
];

const securityItems = [
    {path: '/user', label: 'User', icon: Users},
    {path: '/role', label: 'Role', icon: KeyRound},
    {path: '/audit-log', label: 'Audit Log', icon: ScrollText},
];

export function AppSidebar() {
    const location = useLocation();
    const {state} = useSidebar();
    const collapsed = state === 'collapsed';
    return (
        <Sidebar collapsible="icon">
            <SidebarHeader className="border-b">
                <Link to="/" className="flex h-12 items-center gap-2 px-2">
                    <img src={CoskyLogo} alt="CoSky" className="h-8 w-auto"/>
                    {!collapsed && (
                        <span className="bg-gradient-to-r from-brand-from to-brand-to bg-clip-text text-xl font-semibold tracking-wide text-transparent">
                            CoSky
                        </span>
                    )}
                </Link>
            </SidebarHeader>
            <SidebarContent>
                <SidebarGroup>
                    <SidebarGroupContent>
                        <SidebarMenu>
                            {topItems.map((item) => (
                                <SidebarMenuItem key={item.path}>
                                    <SidebarMenuButton asChild isActive={location.pathname === item.path} tooltip={item.label}>
                                        <NavLink to={item.path}>
                                            <item.icon/>
                                            <span>{item.label}</span>
                                        </NavLink>
                                    </SidebarMenuButton>
                                </SidebarMenuItem>
                            ))}
                            <Collapsible defaultOpen className="group/collapsible">
                                <SidebarMenuItem>
                                    <CollapsibleTrigger asChild>
                                        <SidebarMenuButton tooltip="Security">
                                            <ShieldCheck/>
                                            <span>Security</span>
                                        </SidebarMenuButton>
                                    </CollapsibleTrigger>
                                    <CollapsibleContent>
                                        <SidebarMenuSub>
                                            {securityItems.map((item) => (
                                                <SidebarMenuSubItem key={item.path}>
                                                    <SidebarMenuSubButton asChild isActive={location.pathname === item.path}>
                                                        <NavLink to={item.path}>
                                                            <item.icon/>
                                                            <span>{item.label}</span>
                                                        </NavLink>
                                                    </SidebarMenuSubButton>
                                                </SidebarMenuSubItem>
                                            ))}
                                        </SidebarMenuSub>
                                    </CollapsibleContent>
                                </SidebarMenuItem>
                            </Collapsible>
                        </SidebarMenu>
                    </SidebarGroupContent>
                </SidebarGroup>
            </SidebarContent>
            <SidebarFooter className="border-t">
                <SidebarMenu>
                    <SidebarMenuItem>
                        <SidebarMenuButton asChild tooltip="GitHub">
                            <a href="https://github.com/Ahoo-Wang/CoSky" target="_blank" rel="noopener noreferrer">
                                <Github/>
                                <span>GitHub</span>
                            </a>
                        </SidebarMenuButton>
                    </SidebarMenuItem>
                </SidebarMenu>
            </SidebarFooter>
        </Sidebar>
    );
}
```

- [ ] **Step 5: 创建 ThemeToggle**

创建 `dashboard/src/components/layout/ThemeToggle.tsx`:

```tsx
import {Monitor, Moon, Sun} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@/components/ui/dropdown-menu';
import {useTheme, type Theme} from '@/theme/ThemeProvider';

const options: { value: Theme; label: string; icon: typeof Sun }[] = [
    {value: 'light', label: 'Light', icon: Sun},
    {value: 'dark', label: 'Dark', icon: Moon},
    {value: 'system', label: 'System', icon: Monitor},
];

export function ThemeToggle() {
    const {theme, setTheme} = useTheme();
    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" aria-label="Toggle theme">
                    <Sun className="h-5 w-5 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0"/>
                    <Moon className="absolute h-5 w-5 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100"/>
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
                {options.map((opt) => (
                    <DropdownMenuItem key={opt.value} onClick={() => setTheme(opt.value)}>
                        <opt.icon className="mr-2 h-4 w-4"/>
                        {opt.label}
                        {theme === opt.value && <span className="ml-auto text-primary">●</span>}
                    </DropdownMenuItem>
                ))}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
```

- [ ] **Step 6: 创建 Watermark**

创建 `dashboard/src/components/layout/Watermark.tsx`:

```tsx
import type {ReactNode} from 'react';
import {useTheme} from '@/theme/ThemeProvider';

export function Watermark({content, children}: { content: string; children: ReactNode }) {
    const {resolvedTheme} = useTheme();
    const fill = resolvedTheme === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.06)';
    const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='240' height='160'><text x='20' y='80' transform='rotate(-20 120 80)' fill='${fill}' font-size='16'>${content}</text></svg>`;
    const backgroundImage = `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
    return (
        <div className="relative flex-1">
            {children}
            <div aria-hidden className="pointer-events-none absolute inset-0 z-10" style={{backgroundImage, backgroundRepeat: 'repeat'}}/>
        </div>
    );
}
```

- [ ] **Step 7: 创建 AppHeader**

创建 `dashboard/src/components/layout/AppHeader.tsx`:

```tsx
import {LogOut, User as UserIcon} from 'lucide-react';
import {useSecurityContext} from '@ahoo-wang/fetcher-react';
import {SidebarTrigger} from '@/components/ui/sidebar';
import {Separator} from '@/components/ui/separator';
import {Button} from '@/components/ui/button';
import {
    DropdownMenu, DropdownMenuContent, DropdownMenuItem,
    DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {CurrentNamespaceSelector} from './CurrentNamespaceSelector';
import {ThemeToggle} from './ThemeToggle';
import {ChangePwd} from '@/components/security/ChangePwd';
import {useDrawer} from '@/contexts/DrawerContext';

export function AppHeader() {
    const {currentUser, signOut} = useSecurityContext();
    const {openDrawer, closeDrawer} = useDrawer();
    const handleChangePwd = () => {
        openDrawer(
            <ChangePwd onSubmit={closeDrawer} onCancel={closeDrawer}/>,
            {title: 'Change Password', defaultSize: '20vw'},
        );
    };
    return (
        <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b bg-background px-4">
            <div className="flex items-center gap-2">
                <SidebarTrigger/>
                <Separator orientation="vertical" className="h-6"/>
                <CurrentNamespaceSelector/>
            </div>
            <div className="flex items-center gap-2">
                <ThemeToggle/>
                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="gap-2">
                            <UserIcon className="h-4 w-4"/>
                            {currentUser.sub}
                        </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={handleChangePwd}>Change Password</DropdownMenuItem>
                        <DropdownMenuSeparator/>
                        <DropdownMenuItem variant="destructive" onClick={signOut}>
                            <LogOut className="mr-2 h-4 w-4"/>
                            Sign out
                        </DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
            </div>
        </header>
    );
}
```

- [ ] **Step 8: 重写 AuthenticatedLayout**

整体替换 `dashboard/src/components/layout/AuthenticatedLayout.tsx`:

```tsx
import {Outlet} from 'react-router-dom';
import {useSecurityContext} from '@ahoo-wang/fetcher-react';
import {SidebarInset, SidebarProvider} from '@/components/ui/sidebar';
import {AppSidebar} from './AppSidebar';
import {AppHeader} from './AppHeader';
import {Watermark} from './Watermark';
import {ErrorBoundary} from '@/components/error/ErrorBoundary';
import {useLayoutCollapsed} from '@/hooks/useLayoutCollapsed';

export function AuthenticatedLayout() {
    const [collapsed, setCollapsed] = useLayoutCollapsed();
    const {currentUser} = useSecurityContext();
    return (
        <SidebarProvider
            open={!collapsed}
            onOpenChange={(open) => setCollapsed(!open)}
        >
            <AppSidebar/>
            <SidebarInset>
                <AppHeader/>
                <main className="flex flex-1 flex-col p-6">
                    <Watermark content={currentUser.sub}>
                        <div className="flex flex-1 flex-col rounded-xl border bg-card p-6 shadow-sm">
                            <ErrorBoundary>
                                <Outlet/>
                            </ErrorBoundary>
                        </div>
                    </Watermark>
                </main>
                <footer className="py-4 text-center text-sm text-muted-foreground">
                    <a href="https://github.com/Ahoo-Wang/CoSky" target="_blank" rel="noopener noreferrer"
                       title="High-performance, low-cost microservice governance platform. Service Discovery and Configuration Service."
                       className="font-medium text-primary hover:underline">
                        CoSky
                    </a>
                    {' © 2021-present'}
                </footer>
            </SidebarInset>
        </SidebarProvider>
    );
}
```

- [ ] **Step 9: 重写 PageHeader / DataTableWrapper**

整体替换 `dashboard/src/components/layout/PageHeader.tsx`:

```tsx
import type {ReactNode} from 'react';

export function PageHeader({title, actions}: { title: string; actions?: ReactNode }) {
    return (
        <div className="mb-6 flex items-center justify-between">
            <h2 className="text-2xl font-semibold tracking-tight">{title}</h2>
            {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
    );
}
```

整体替换 `dashboard/src/components/layout/DataTableWrapper.tsx`:

```tsx
import type {ReactNode} from 'react';

export function DataTableWrapper({children}: { children: ReactNode }) {
    return (
        <div className="overflow-hidden rounded-lg border bg-card">
            {children}
        </div>
    );
}
```

替换后执行 `grep -rn "spaceProps" src` —— 预期无输出；若有调用点，改为 `PageHeader` 新 props（直接传 actions)。

- [ ] **Step 10: 替换路由级加载态**

`dashboard/src/AppRoutes.tsx`:
- 删除 `import {Skeleton} from "antd";`
- 加 `import {Spinner} from "./components/feedback/Spinner.tsx";`
- `fallback={<Skeleton/>}` 改为：

```tsx
            <Suspense fallback={
                <div className="flex h-[60vh] items-center justify-center">
                    <Spinner/>
                </div>
            }>
```

`dashboard/src/components/security/ProtectedRoute.tsx`：将其中的 antd `Skeleton` fallback 替换为同款 `<div className="flex h-[60vh] items-center justify-center"><Spinner/></div>`（保留其守卫逻辑不变）。

- [ ] **Step 11: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "Layout\|Menu\|Dropdown\|Watermark" src/components/layout --include="*.tsx" | grep "antd"   # 布局目录无 antd 引用
```

手动走查（`pnpm dev`)：侧栏折叠/展开并刷新后保持；导航高亮正确；Security 子菜单展开；命名空间切换器可搜索、可切换；主题三档切换生效且刷新后保持；用户菜单可打开（Change Password 抽屉仍走 antd Drawer，正常）;GitHub 页脚链接正确。

```bash
git add -A dashboard && git commit -m "feat(dashboard): rebuild app shell with shadcn sidebar, theme toggle and watermark"
```

---

### Task 3: Sheet 宿主 —— DrawerProvider 实现替换（API 不变）

**Files:**
- Overwrite: `dashboard/src/contexts/DrawerContext.tsx`
- Overwrite: `dashboard/src/contexts/DrawerProvider.tsx`

**Interfaces:**
- Consumes: shadcn `sheet`(Task 2 已随 sidebar 生成）;`cn`。
- Produces（签名对调用方完全兼容，8 个调用点零改动）:
  - `DrawerOptions = { title?: ReactNode; defaultSize?: string }`(`defaultSize` 支持 `'60vw'`/`'20vw'`)
  - `useDrawer(): { openDrawer: (content: ReactNode, options?: DrawerOptions) => void; closeDrawer: () => void }`

- [ ] **Step 1: 重写 DrawerContext**

整体替换 `dashboard/src/contexts/DrawerContext.tsx`（保留 Apache 头）:

```tsx
import {createContext, useContext, type ReactNode} from 'react';

export interface DrawerOptions {
    title?: ReactNode;
    defaultSize?: string;
}

export interface DrawerContextType {
    openDrawer: (content: ReactNode, options?: DrawerOptions) => void;
    closeDrawer: () => void;
}

export const DrawerContext = createContext<DrawerContextType | undefined>(undefined);

export const useDrawer = () => {
    const context = useContext(DrawerContext);
    if (!context) {
        throw new Error('useDrawer must be used within DrawerProvider');
    }
    return context;
};
```

- [ ] **Step 2: 重写 DrawerProvider 为 Sheet 宿主（含拖拽调宽）**

整体替换 `dashboard/src/contexts/DrawerProvider.tsx`（保留 Apache 头）:

```tsx
import {useState, type PointerEvent as ReactPointerEvent, type ReactNode} from 'react';
import {Sheet, SheetContent, SheetHeader, SheetTitle} from '@/components/ui/sheet';
import {DrawerContext, type DrawerOptions} from './DrawerContext';

function parseSize(size?: string): number {
    if (size?.endsWith('vw')) {
        return (window.innerWidth * parseFloat(size)) / 100;
    }
    return window.innerWidth * 0.6;
}

export function DrawerProvider({children}: { children: ReactNode }) {
    const [open, setOpen] = useState(false);
    const [content, setContent] = useState<ReactNode>(null);
    const [options, setOptions] = useState<DrawerOptions>({});
    const [width, setWidth] = useState<number>(window.innerWidth * 0.6);

    const openDrawer = (drawerContent: ReactNode, drawerOptions: DrawerOptions = {}) => {
        setContent(drawerContent);
        setOptions(drawerOptions);
        setWidth(parseSize(drawerOptions.defaultSize));
        setOpen(true);
    };

    const closeDrawer = () => {
        setOpen(false);
        setTimeout(() => {
            setContent(null);
            setOptions({});
        }, 200);
    };

    const startResize = (e: ReactPointerEvent) => {
        e.preventDefault();
        const startX = e.clientX;
        const startWidth = width;
        const onMove = (ev: PointerEvent) => {
            const next = startWidth + (startX - ev.clientX);
            setWidth(Math.min(Math.max(next, 360), window.innerWidth * 0.95));
        };
        const onUp = () => {
            window.removeEventListener('pointermove', onMove);
            window.removeEventListener('pointerup', onUp);
        };
        window.addEventListener('pointermove', onMove);
        window.addEventListener('pointerup', onUp);
    };

    return (
        <DrawerContext.Provider value={{openDrawer, closeDrawer}}>
            {children}
            <Sheet open={open} onOpenChange={(next) => { if (!next) closeDrawer(); }}>
                <SheetContent
                    side="right"
                    className="flex w-full flex-col p-0 sm:max-w-none"
                    style={{width, maxWidth: '95vw'}}
                >
                    <div
                        aria-hidden
                        onPointerDown={startResize}
                        className="absolute left-0 top-0 z-20 h-full w-1 cursor-ew-resize hover:bg-primary/30 active:bg-primary/50"
                    />
                    <SheetHeader className="border-b px-6 py-4">
                        <SheetTitle>{options.title}</SheetTitle>
                    </SheetHeader>
                    <div className="flex-1 overflow-y-auto px-6 py-4">
                        {content}
                    </div>
                </SheetContent>
            </Sheet>
        </DrawerContext.Provider>
    );
}
```

- [ ] **Step 3: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd" src/contexts    # 预期无输出
```

手动走查：Config 页点 Add Config → 右侧 Sheet 打开、标题正确、左缘可拖拽调宽、Esc/遮罩可关闭；Change Password(20vw）初始宽度明显更窄；编辑器提交/取消后关闭。

```bash
git add -A dashboard && git commit -m "refactor(dashboard): swap global drawer host to shadcn Sheet with drag resize"
```

---

### Task 4: DataTable 基建 + 反馈组件

**Files:**
- Modify: `dashboard/package.json`(`@tanstack/react-table`)
- Create: `dashboard/src/components/table/DataTable.tsx`
- Create: `dashboard/src/components/table/DataTableColumnHeader.tsx`
- Create: `dashboard/src/components/table/DataTablePagination.tsx`
- Create: `dashboard/src/components/table/DataTableViewOptions.tsx`
- Create: `dashboard/src/components/table/columns.tsx`
- Overwrite: `dashboard/src/components/table/SearchFilter.tsx`
- Modify: `dashboard/src/components/table/index.tsx`（重导出）
- Create: `dashboard/src/components/feedback/ConfirmDialog.tsx`
- Create: `dashboard/src/components/feedback/Empty.tsx`
- Create: `dashboard/src/components/feedback/ErrorState.tsx`
- Generate: `table alert-dialog`(shadcn CLI)
- Untouched: `dashboard/src/components/table/index.tsx`(遗留 antd 助手仍被未迁移页面引用，T15 删除）

**Interfaces:**
- Consumes: shadcn `table/alert-dialog/dropdown-menu/popover/button/input/skeleton`;`cn`;`Spinner`。
- Produces:
  - `DataTable<TData>(props: DataTableProps<TData>)`:`{columns: ColumnDef<TData, any>[]; data: TData[]; loading?: boolean; error?: unknown; onRetry?: () => void; getRowId?: (row: TData) => string; renderExpanded?: (row: TData) => ReactNode; toolbar?: ReactNode; pagination?: DataTablePagination; showViewOptions?: boolean}`
  - `DataTablePagination = { mode: 'client'; pageSize?: number } | { mode: 'server'; pageIndex: number; pageSize: number; total: number; onPaginationChange: (pageIndex: number, pageSize: number) => void }`（默认 `{mode:'client', pageSize:10}`)
  - `createSearchColumn<T>({title, accessorKey, placeholder?, enableSorting?}): ColumnDef<T, any>` — 列头含排序 + 搜索过滤（`filterFn: 'includesString'`)
  - `createActionColumn<T>({items: ActionItem<T>[]}): ColumnDef<T, any>`;`ActionItem<T> = {key, label, icon?, danger?, confirm?, disabled?(record), onClick(record)}`
  - `ConfirmDialog({open, onOpenChange, title, description?, confirmText?, danger?, onConfirm})`
  - `Empty({title?, description?, action?})`、`ErrorState({error?, onRetry?})`

- [ ] **Step 1: 安装与生成**

```bash
cd dashboard
pnpm add @tanstack/react-table
pnpm dlx shadcn@latest add --yes table alert-dialog
```

- [ ] **Step 2: 创建 ConfirmDialog**

创建 `dashboard/src/components/feedback/ConfirmDialog.tsx`:

```tsx
import {
    AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
    AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {buttonVariants} from '@/components/ui/button';

export interface ConfirmDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    title: string;
    description?: string;
    confirmText?: string;
    danger?: boolean;
    onConfirm: () => void;
}

export function ConfirmDialog({open, onOpenChange, title, description, confirmText = 'Confirm', danger, onConfirm}: ConfirmDialogProps) {
    return (
        <AlertDialog open={open} onOpenChange={onOpenChange}>
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>{title}</AlertDialogTitle>
                    {description && <AlertDialogDescription>{description}</AlertDialogDescription>}
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction
                        className={danger ? buttonVariants({variant: 'destructive'}) : undefined}
                        onClick={onConfirm}
                    >
                        {confirmText}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
}
```

- [ ] **Step 3: 创建 Empty / ErrorState**

创建 `dashboard/src/components/feedback/Empty.tsx`:

```tsx
import type {ReactNode} from 'react';
import {Inbox} from 'lucide-react';

export function Empty({title = 'No data', description, action}: { title?: string; description?: string; action?: ReactNode }) {
    return (
        <div className="flex flex-col items-center justify-center gap-2 py-12 text-center">
            <Inbox className="h-10 w-10 text-muted-foreground/50"/>
            <p className="text-sm font-medium text-muted-foreground">{title}</p>
            {description && <p className="text-xs text-muted-foreground/70">{description}</p>}
            {action}
        </div>
    );
}
```

创建 `dashboard/src/components/feedback/ErrorState.tsx`:

```tsx
import {AlertCircle} from 'lucide-react';
import {Button} from '@/components/ui/button';

export function ErrorState({error, onRetry}: { error?: unknown; onRetry?: () => void }) {
    const message = error instanceof Error ? error.message : 'Failed to load data';
    return (
        <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
            <AlertCircle className="h-10 w-10 text-destructive/70"/>
            <p className="text-sm text-muted-foreground">{message}</p>
            {onRetry && <Button variant="outline" size="sm" onClick={onRetry}>Retry</Button>}
        </div>
    );
}
```

- [ ] **Step 4: 重写 SearchFilter(保留 useSearchFilter!)**

整体替换 `dashboard/src/components/table/SearchFilter.tsx`。**必须原样保留文件末尾的 `useSearchFilter` hook**（含其 `antd/es/table/interface` 类型 import)——遗留 `table/index.tsx` 与未迁移页面的 antd `filterDropdown` 在共存期仍依赖它；只重写 `SearchFilter` 组件的 UI(antd → shadcn),props 保持可选、形状不变：

```tsx
import {Input} from '@/components/ui/input';
import {Button} from '@/components/ui/button';
import type {FilterDropdownProps} from 'antd/es/table/interface';

export interface SearchFilterProps {
    placeholder?: string;
    value?: string;
    onChange?: (value: string) => void;
    onSearch?: () => void;
    onReset?: () => void;
}

export function SearchFilter({placeholder = 'Search...', value, onChange, onSearch, onReset}: SearchFilterProps) {
    return (
        <div className="flex w-56 flex-col gap-2">
            <Input
                placeholder={placeholder}
                value={value ?? ''}
                onChange={(e) => onChange?.(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') onSearch?.(); }}
            />
            <div className="flex justify-end gap-2">
                <Button variant="ghost" size="sm" onClick={() => onReset?.()}>Reset</Button>
                <Button size="sm" onClick={() => onSearch?.()}>Search</Button>
            </div>
        </div>
    );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSearchFilter(dropdownProps: FilterDropdownProps) {
    const {setSelectedKeys, selectedKeys, confirm, clearFilters} = dropdownProps;
    return {
        value: (selectedKeys[0] as string) ?? '',
        onChange: (val: string) => setSelectedKeys(val ? [val] : []),
        onSearch: () => confirm(),
        onReset: () => clearFilters?.(),
    };
}
```

- [ ] **Step 5: 创建 DataTableColumnHeader（排序 + 列内搜索）**

创建 `dashboard/src/components/table/DataTableColumnHeader.tsx`:

```tsx
import {useState} from 'react';
import type {Column} from '@tanstack/react-table';
import {ArrowDown, ArrowUp, ArrowUpDown, Search} from 'lucide-react';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';
import {SearchFilter} from './SearchFilter';

export interface DataTableColumnHeaderProps<TData> {
    column: Column<TData, unknown>;
    title: string;
    placeholder?: string;
    sortable?: boolean;
    searchable?: boolean;
}

export function DataTableColumnHeader<TData>({column, title, placeholder, sortable = true, searchable = true}: DataTableColumnHeaderProps<TData>) {
    const [open, setOpen] = useState(false);
    const filterValue = (column.getFilterValue() as string) ?? '';
    const sorted = column.getIsSorted();
    return (
        <div className="flex items-center gap-1">
            <span>{title}</span>
            {sortable && (
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => column.toggleSorting(sorted === 'asc')}>
                    {sorted === 'asc' ? <ArrowUp className="h-3.5 w-3.5"/> : sorted === 'desc' ? <ArrowDown className="h-3.5 w-3.5"/> : <ArrowUpDown className="h-3.5 w-3.5 opacity-50"/>}
                </Button>
            )}
            {searchable && (
                <Popover open={open} onOpenChange={setOpen}>
                    <PopoverTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-6 w-6">
                            <Search className={cn('h-3.5 w-3.5', filterValue ? 'text-primary' : 'opacity-50')}/>
                        </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-64" align="start">
                        <SearchFilter
                            placeholder={placeholder ?? `Search ${title}`}
                            value={filterValue}
                            onChange={(v) => column.setFilterValue(v || undefined)}
                            onSearch={() => setOpen(false)}
                            onReset={() => column.setFilterValue(undefined)}
                        />
                    </PopoverContent>
                </Popover>
            )}
        </div>
    );
}
```

- [ ] **Step 6: 创建 columns 助手（平移 createSearchColumn/createActionColumn)**

创建 `dashboard/src/components/table/columns.tsx`:

```tsx
/* eslint-disable @typescript-eslint/no-explicit-any */
import {useState, type ReactNode} from 'react';
import type {ColumnDef} from '@tanstack/react-table';
import {Button} from '@/components/ui/button';
import {ConfirmDialog} from '@/components/feedback/ConfirmDialog';
import {DataTableColumnHeader} from './DataTableColumnHeader';

export interface SearchColumnProps<T> {
    title: string;
    accessorKey: Extract<keyof T, string>;
    placeholder?: string;
    enableSorting?: boolean;
}

export function createSearchColumn<T>(props: SearchColumnProps<T>): ColumnDef<T, any> {
    return {
        accessorKey: props.accessorKey,
        enableSorting: props.enableSorting ?? true,
        filterFn: 'includesString',
        header: ({column}) => (
            <DataTableColumnHeader
                column={column}
                title={props.title}
                placeholder={props.placeholder}
                sortable={props.enableSorting ?? true}
            />
        ),
    };
}

export interface ActionItem<T = unknown> {
    key: string;
    label: string;
    icon?: ReactNode;
    danger?: boolean;
    confirm?: string;
    disabled?: (record: T) => boolean;
    onClick: (record: T) => void;
}

function ActionCell<T>({items, record}: { items: ActionItem<T>[]; record: T }) {
    const [pending, setPending] = useState<ActionItem<T> | null>(null);
    return (
        <div className="flex items-center gap-1">
            {items.map((item) => (
                <Button
                    key={item.key}
                    variant="link"
                    size="sm"
                    disabled={item.disabled?.(record)}
                    className={item.danger ? 'text-destructive hover:text-destructive' : undefined}
                    onClick={() => {
                        if (item.confirm) {
                            setPending(item);
                        } else {
                            item.onClick(record);
                        }
                    }}
                >
                    {item.icon}
                    {item.label}
                </Button>
            ))}
            <ConfirmDialog
                open={pending !== null}
                onOpenChange={(open) => { if (!open) setPending(null); }}
                title={pending?.confirm ?? ''}
                danger={pending?.danger}
                onConfirm={() => {
                    pending?.onClick(record);
                    setPending(null);
                }}
            />
        </div>
    );
}

export function createActionColumn<T>({items}: { items: ActionItem<T>[] }): ColumnDef<T, any> {
    return {
        id: 'actions',
        enableSorting: false,
        enableHiding: false,
        header: () => <span>Action</span>,
        cell: ({row}) => <ActionCell items={items} record={row.original}/>,
    };
}
```

- [ ] **Step 7: 创建 DataTablePagination / DataTableViewOptions**

创建 `dashboard/src/components/table/DataTablePagination.tsx`:

```tsx
import type {Table} from '@tanstack/react-table';
import {ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import type {DataTablePagination as PaginationProp} from './DataTable';

interface Props<TData> {
    table: Table<TData>;
    pagination: Extract<PaginationProp, { mode: 'server' }>;
}

export function DataTablePagination<TData>({table, pagination}: Props<TData>) {
    const {pageIndex, pageSize, total, onPaginationChange} = pagination;
    const pageCount = Math.max(Math.ceil(total / pageSize), 1);
    return (
        <div className="flex items-center justify-between px-2 py-3">
            <span className="text-sm text-muted-foreground">Total {total} items</span>
            <div className="flex items-center gap-4">
                <div className="flex items-center gap-2">
                    <span className="text-sm text-muted-foreground">Rows per page</span>
                    <Select value={String(pageSize)} onValueChange={(v) => onPaginationChange(0, Number(v))}>
                        <SelectTrigger className="h-8 w-[70px]"><SelectValue/></SelectTrigger>
                        <SelectContent>
                            {[10, 20, 50, 100].map((size) => (
                                <SelectItem key={size} value={String(size)}>{size}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
                <span className="text-sm text-muted-foreground">Page {pageIndex + 1} of {pageCount}</span>
                <div className="flex items-center gap-1">
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex <= 0} onClick={() => onPaginationChange(0, pageSize)}>
                        <ChevronsLeft className="h-4 w-4"/>
                    </Button>
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex <= 0} onClick={() => onPaginationChange(pageIndex - 1, pageSize)}>
                        <ChevronLeft className="h-4 w-4"/>
                    </Button>
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex >= pageCount - 1} onClick={() => onPaginationChange(pageIndex + 1, pageSize)}>
                        <ChevronRight className="h-4 w-4"/>
                    </Button>
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex >= pageCount - 1} onClick={() => onPaginationChange(pageCount - 1, pageSize)}>
                        <ChevronsRight className="h-4 w-4"/>
                    </Button>
                </div>
            </div>
        </div>
    );
}
```

创建 `dashboard/src/components/table/DataTableViewOptions.tsx`:

```tsx
import type {Table} from '@tanstack/react-table';
import {Columns3} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {
    DropdownMenu, DropdownMenuCheckboxItem, DropdownMenuContent,
    DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

export function DataTableViewOptions<TData>({table}: { table: Table<TData> }) {
    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2">
                    <Columns3 className="h-4 w-4"/>
                    Columns
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
                <DropdownMenuLabel>Toggle columns</DropdownMenuLabel>
                <DropdownMenuSeparator/>
                {table.getAllColumns()
                    .filter((column) => column.getCanHide())
                    .map((column) => (
                        <DropdownMenuCheckboxItem
                            key={column.id}
                            checked={column.getIsVisible()}
                            onCheckedChange={(checked) => column.toggleVisibility(checked)}
                        >
                            {column.id}
                        </DropdownMenuCheckboxItem>
                    ))}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
```

- [ ] **Step 8: 创建 DataTable 主体**

创建 `dashboard/src/components/table/DataTable.tsx`:

```tsx
/* eslint-disable @typescript-eslint/no-explicit-any */
import {Fragment, useState, type ReactNode} from 'react';
import {
    flexRender, getCoreRowModel, getExpandedRowModel, getFilteredRowModel,
    getPaginationRowModel, getSortedRowModel, useReactTable,
    type ColumnDef, type PaginationState, type SortingState,
} from '@tanstack/react-table';
import {ChevronRight} from 'lucide-react';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Skeleton} from '@/components/ui/skeleton';
import {Button} from '@/components/ui/button';
import {cn} from '@/lib/utils';
import {Empty} from '@/components/feedback/Empty';
import {ErrorState} from '@/components/feedback/ErrorState';
import {DataTablePagination} from './DataTablePagination';
import {DataTableViewOptions} from './DataTableViewOptions';

export type DataTablePagination =
    | { mode: 'client'; pageSize?: number }
    | { mode: 'server'; pageIndex: number; pageSize: number; total: number; onPaginationChange: (pageIndex: number, pageSize: number) => void };

export interface DataTableProps<TData> {
    columns: ColumnDef<TData, any>[];
    data: TData[];
    loading?: boolean;
    error?: unknown;
    onRetry?: () => void;
    getRowId?: (row: TData) => string;
    renderExpanded?: (row: TData) => ReactNode;
    toolbar?: ReactNode;
    pagination?: DataTablePagination;
    showViewOptions?: boolean;
}

export function DataTable<TData>({
    columns, data, loading, error, onRetry, getRowId, renderExpanded, toolbar,
    pagination = {mode: 'client', pageSize: 10},
    showViewOptions = true,
}: DataTableProps<TData>) {
    const [sorting, setSorting] = useState<SortingState>([]);
    const [clientPagination, setClientPagination] = useState<PaginationState>({
        pageIndex: 0,
        pageSize: pagination.mode === 'client' ? (pagination.pageSize ?? 10) : 10,
    });
    const isServer = pagination.mode === 'server';
    const serverPagination: PaginationState = isServer
        ? {pageIndex: pagination.pageIndex, pageSize: pagination.pageSize}
        : clientPagination;

    const allColumns: ColumnDef<TData, any>[] = renderExpanded
        ? [{
            id: 'expander',
            enableSorting: false,
            enableHiding: false,
            header: () => null,
            cell: ({row}) => (
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => row.toggleExpanded()}>
                    <ChevronRight className={cn('h-4 w-4 transition-transform', row.getIsExpanded() && 'rotate-90')}/>
                </Button>
            ),
        }, ...columns]
        : columns;

    const table = useReactTable({
        data,
        columns: allColumns,
        getRowId,
        state: {sorting, pagination: serverPagination},
        onSortingChange: setSorting,
        onPaginationChange: isServer
            ? (updater) => {
                const next = typeof updater === 'function' ? updater(serverPagination) : updater;
                pagination.onPaginationChange(next.pageIndex, next.pageSize);
            }
            : setClientPagination,
        manualPagination: isServer,
        pageCount: isServer ? Math.ceil(pagination.total / pagination.pageSize) : undefined,
        getCoreRowModel: getCoreRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
        getPaginationRowModel: isServer ? undefined : getPaginationRowModel(),
        getExpandedRowModel: renderExpanded ? getExpandedRowModel() : undefined,
    });

    const columnCount = table.getAllColumns().length;
    const rows = table.getRowModel().rows;

    let body: ReactNode;
    if (loading && data.length === 0) {
        body = Array.from({length: 5}).map((_, i) => (
            <TableRow key={`skeleton-${i}`}>
                {allColumns.map((_, j) => (
                    <TableCell key={j}><Skeleton className="h-5 w-full"/></TableCell>
                ))}
            </TableRow>
        ));
    } else if (error) {
        body = (
            <TableRow>
                <TableCell colSpan={columnCount}><ErrorState error={error} onRetry={onRetry}/></TableCell>
            </TableRow>
        );
    } else if (rows.length === 0) {
        body = (
            <TableRow>
                <TableCell colSpan={columnCount}><Empty/></TableCell>
            </TableRow>
        );
    } else {
        body = rows.map((row) => (
            <Fragment key={row.id}>
                <TableRow data-state={row.getIsExpanded() ? 'expanded' : undefined}>
                    {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id}>
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </TableCell>
                    ))}
                </TableRow>
                {row.getIsExpanded() && renderExpanded && (
                    <TableRow>
                        <TableCell colSpan={columnCount} className="bg-muted/40 p-4">
                            {renderExpanded(row.original)}
                        </TableCell>
                    </TableRow>
                )}
            </Fragment>
        ));
    }

    return (
        <div className="flex flex-col">
            {(toolbar || showViewOptions) && (
                <div className="flex items-center justify-between gap-2 border-b px-2 py-2">
                    <div className="flex items-center gap-2">{toolbar}</div>
                    {showViewOptions && <DataTableViewOptions table={table}/>}
                </div>
            )}
            <Table>
                <TableHeader>
                    {table.getHeaderGroups().map((headerGroup) => (
                        <TableRow key={headerGroup.id}>
                            {headerGroup.headers.map((header) => (
                                <TableHead key={header.id}>
                                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                                </TableHead>
                            ))}
                        </TableRow>
                    ))}
                </TableHeader>
                <TableBody className={loading && data.length > 0 ? 'opacity-60' : undefined}>
                    {body}
                </TableBody>
            </Table>
            {isServer && (
                <DataTablePagination table={table} pagination={pagination}/>
            )}
            {!isServer && rows.length > (pagination.mode === 'client' ? (pagination.pageSize ?? 10) : 10) && (
                <div className="flex items-center justify-end gap-2 border-t px-2 py-3">
                    <Button variant="outline" size="sm" disabled={!table.getCanPreviousPage()} onClick={() => table.previousPage()}>Previous</Button>
                    <span className="text-sm text-muted-foreground">
                        Page {table.getState().pagination.pageIndex + 1} of {table.getPageCount()}
                    </span>
                    <Button variant="outline" size="sm" disabled={!table.getCanNextPage()} onClick={() => table.nextPage()}>Next</Button>
                </div>
            )}
        </div>
    );
}
```

- [ ] **Step 9: 保持遗留 barrel 不动**

**不要**修改 `dashboard/src/components/table/index.tsx`——其 antd 版 `createSearchColumn`/`createActionColumn`/`useSearchFilter` 仍被尚未迁移的 `ConfigPage`/`ServicePage` 引用，动了会让 `tsc -b` 立刻红。新组件不设 barrel；页面一律深路径导入：

```tsx
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn, createSearchColumn} from '@/components/table/columns';
```

旧 `SearchFilter.tsx` 已被 Step 4 覆写，但新旧 props 形状一致（`value/onChange/onSearch/onReset`)，旧 antd `filterDropdown` 包装器在共存期继续可用。遗留 `index.tsx` 由 T15 在 grep 确认无引用后删除。

- [ ] **Step 10: 门禁 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd" src/components/table src/components/feedback   # 预期无输出
```

```bash
git add -A dashboard && git commit -m "feat(dashboard): add TanStack-based DataTable with search columns, actions and feedback states"
```

---

### Task 5: 表单基建 —— react-hook-form + zod + shadcn Form

**Files:**
- Modify: `dashboard/package.json`
- Generate: `form label select switch textarea`(shadcn CLI)

**Interfaces:**
- Produces（后续页面任务使用的标准表单模式）:
  - `useForm<Values>({resolver: zodResolver(schema), defaultValues})` + `<Form {...form}>` + `<FormField control={form.control} name="..." render={({field}) => <FormItem><FormLabel/><FormControl/><FormMessage/></FormItem>}/>`
  - 动态列表:`useFieldArray({control: form.control, name: 'items'})`
  - React Compiler 豁免：若 lint 报 react-compiler 错误，文件首行加 `"use no memo"`。

- [ ] **Step 1: 安装与生成**

```bash
cd dashboard
pnpm add react-hook-form zod @hookform/resolvers
pnpm dlx shadcn@latest add --yes form label select switch textarea
```

- [ ] **Step 2: 门禁 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
git add -A dashboard && git commit -m "feat(dashboard): add react-hook-form + zod form infrastructure"
```

---

### Task 6: 页面迁移 1/8 —— NamespacePage（含 AddNamespaceForm)

**Files:**
- Overwrite: `dashboard/src/pages/namespace/NamespacePage.tsx`
- Overwrite: `dashboard/src/pages/namespace/AddNamespaceForm.tsx`
- Untouched: `dashboard/src/pages/namespace/namespaces.ts`(`isSystemNamespace` 逻辑不变）

**Interfaces:**
- Consumes: `DataTable`、`createActionColumn`(Task 4);`PageHeader`、`DataTableWrapper`(Task 2);`toast`(Task 1);`namespaceApiClient.removeNamespace`、`useNamespacesContext()`、`useCurrentNamespaceContext()`;Task 5 表单模式。
- Produces: 无新共享接口（页面任务）。

- [ ] **Step 1: 重写 AddNamespaceForm**

先读现文件确认 API 调用（`namespaceApiClient.addNamespace` 或类似，以现文件为准）。整体替换为 RHF + zod 内联表单（组件签名 `({onSuccess}: {onSuccess: () => void})` 保持不变）:

```tsx
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {toast} from 'sonner';
import {Plus} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Form, FormControl, FormField, FormItem, FormMessage} from '@/components/ui/form';
import {namespaceApiClient} from '@/services/clients';

const schema = z.object({
    namespace: z.string().min(1, 'Namespace is required')
        .regex(/^[a-zA-Z][a-zA-Z0-9_.-]*$/, 'Must start with a letter and contain only letters, digits, _, ., -'),
});
type Values = z.infer<typeof schema>;

export function AddNamespaceForm({onSuccess}: { onSuccess: () => void }) {
    const form = useForm<Values>({
        resolver: zodResolver(schema),
        defaultValues: {namespace: ''},
    });

    const onSubmit = async (values: Values) => {
        try {
            await namespaceApiClient.addNamespace(values.namespace);
            toast.success('Namespace added successfully');
            form.reset();
            onSuccess();
        } catch {
            toast.error('Failed to add namespace');
        }
    };

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex items-start gap-2">
                <FormField
                    control={form.control}
                    name="namespace"
                    render={({field}) => (
                        <FormItem className="w-64">
                            <FormControl>
                                <Input placeholder="New namespace" {...field}/>
                            </FormControl>
                            <FormMessage/>
                        </FormItem>
                    )}
                />
                <Button type="submit" disabled={form.formState.isSubmitting}>
                    <Plus className="mr-1 h-4 w-4"/>
                    Add
                </Button>
            </form>
        </Form>
    );
}
```

- [ ] **Step 2: 重写 NamespacePage**

整体替换 `dashboard/src/pages/namespace/NamespacePage.tsx`:

```tsx
import {Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {isSystemNamespace} from './namespaces';
import {namespaceApiClient} from '@/services/clients';
import {AddNamespaceForm} from './AddNamespaceForm';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn} from '@/components/table/columns';

export function NamespacePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {namespaces, loading, refresh} = useNamespacesContext();

    const handleDelete = async (namespace: string) => {
        try {
            await namespaceApiClient.removeNamespace(namespace);
            toast.success('Namespace deleted successfully');
            refresh();
        } catch {
            toast.error('Failed to delete namespace');
        }
    };

    const isDisabled = (namespace: string) =>
        isSystemNamespace(namespace) || currentNamespace === namespace;

    const data = namespaces.map((ns) => ({name: ns}));

    const columns = [
        {
            accessorKey: 'name',
            header: () => <span>Namespace</span>,
        },
        createActionColumn<{ name: string }>({
            items: [
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this namespace?',
                    disabled: (record) => isDisabled(record.name),
                    onClick: (record) => void handleDelete(record.name),
                },
            ],
        }),
    ];

    return (
        <div>
            <PageHeader title="Namespace" actions={<AddNamespaceForm onSuccess={refresh}/>}/>
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={data}
                    loading={loading}
                    getRowId={(row) => row.name}
                />
            </DataTableWrapper>
        </div>
    );
}
```

- [ ] **Step 3: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design" src/pages/namespace   # 预期无输出
```

手动走查：列表加载/骨架屏；新增命名空间（含非法字符校验提示）；删除二次确认；系统命名空间与当前命名空间的 Delete 置灰；列显隐菜单可用。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate namespace page to shadcn DataTable and RHF form"
```

---

### Task 7: 页面迁移 2/8 —— AuditLogPage（服务端分页样板）

**Files:**
- Overwrite: `dashboard/src/pages/audit/AuditLogPage.tsx`

**Interfaces:**
- Consumes: `DataTable` server 分页模式（Task 4);`createSearchColumn`;既有 `useQuery({query, execute})` + `setQuery` 模式；`auditLogApiClient`（以现文件为准）。
- Produces: 无。

- [ ] **Step 1: 重写 AuditLogPage**

先读现文件，以其 antd 列定义（title/dataIndex）与 `useQuery` 的 query 形状为事实来源。转换规则：

1. 保留 `useQuery({query: {namespace, pageIndex, pageSize}, execute: ...})` 结构与 `setQuery` 用法；`pageIndex/pageSize` 作为 query 的一部分（沿用现状）。
2. antd 列 → TanStack 列：文本搜索列用 `createSearchColumn({title, accessorKey})`；纯展示列用 `{accessorKey, header: () => <span>Title</span>}`；时间列沿用 dayjs 格式化（`cell: ({row}) => dayjs(row.original.xxx).format('YYYY-MM-DD HH:mm:ss')`，以现文件格式串为准）。
3. 表格挂载：

```tsx
<DataTable
    columns={columns}
    data={data?.list ?? []}              // 字段名以 generated 类型为准
    loading={loading}
    error={error}
    onRetry={/* 重新执行 useQuery 的函数，以现文件为准 */}
    pagination={{
        mode: 'server',
        pageIndex: query.pageIndex,
        pageSize: query.pageSize,
        total: data?.total ?? 0,
        onPaginationChange: (pageIndex, pageSize) =>
            setQuery({...query, pageIndex, pageSize}),
    }}
/>
```

4. 删除所有 antd/`@ant-design/icons` import;`message` 已在此前任务移除。

- [ ] **Step 2: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design" src/pages/audit
```

手动走查：翻页/跳页/每页条数切换触发服务端请求且总数正确；列搜索与排序（若现状支持）;loading 骨架。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate audit log page with server-side pagination"
```

---

### Task 8: 页面迁移 3/8 —— UserPage（含 AddUserEditor、角色多选）

**Files:**
- Overwrite: `dashboard/src/pages/user/UserPage.tsx`
- Overwrite: `dashboard/src/pages/user/AddUserEditor.tsx`
- Create: `dashboard/src/pages/user/RoleMultiSelect.tsx`

**Interfaces:**
- Consumes: 全部基建；`roleApiClient`/`userApiClient`（以现文件为准）;`useDrawer()`。
- Produces: `RoleMultiSelect({value: string[], onChange: (v: string[]) => void, options: string[], disabled?})`（页内复用，不对外）。

- [ ] **Step 1: 创建 RoleMultiSelect(antd `Select mode="multiple"` 替代）**

创建 `dashboard/src/pages/user/RoleMultiSelect.tsx`:

```tsx
import {useState} from 'react';
import {Check, ChevronsUpDown, X} from 'lucide-react';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList} from '@/components/ui/command';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';

export interface RoleMultiSelectProps {
    value: string[];
    onChange: (value: string[]) => void;
    options: string[];
    disabled?: boolean;
    placeholder?: string;
}

export function RoleMultiSelect({value, onChange, options, disabled, placeholder = 'Select roles'}: RoleMultiSelectProps) {
    const [open, setOpen] = useState(false);
    const toggle = (role: string) => {
        onChange(value.includes(role) ? value.filter((r) => r !== role) : [...value, role]);
    };
    return (
        <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button variant="outline" role="combobox" disabled={disabled}
                        className="h-auto min-h-9 w-full justify-between font-normal">
                    <span className="flex flex-wrap gap-1">
                        {value.length === 0 && <span className="text-muted-foreground">{placeholder}</span>}
                        {value.map((role) => (
                            <Badge key={role} variant="secondary" className="gap-1">
                                {role}
                                <X
                                    className="h-3 w-3 cursor-pointer"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        toggle(role);
                                    }}
                                />
                            </Badge>
                        ))}
                    </span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50"/>
                </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
                <Command>
                    <CommandInput placeholder="Search role..."/>
                    <CommandList>
                        <CommandEmpty>No role found.</CommandEmpty>
                        <CommandGroup>
                            {options.map((role) => (
                                <CommandItem key={role} value={role} onSelect={() => toggle(role)}>
                                    <Check className={cn('mr-2 h-4 w-4', value.includes(role) ? 'opacity-100' : 'opacity-0')}/>
                                    {role}
                                </CommandItem>
                            ))}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    );
}
```

- [ ] **Step 2: 重写 UserPage**

先读现文件，以现有列、API(`userApiClient` 的角色绑定/解锁/删除方法）与 `useRoles` hook 为事实来源。转换规则：

1. 用户名列等 → `createSearchColumn`;Action 列 → `createActionColumn`（解锁：`confirm` 文案沿用现状；删除：danger + confirm)。
2. 行内角色多选（antd `Select mode="multiple"`）单元格 → `cell: ({row}) => <RoleMultiSelect value={...} options={roleOptions} onChange={(roles) => void bindRoles(row.original, roles)}/>`,API 调用沿用现文件逻辑，成功 `toast.success` 失败 `toast.error`。
3. 头部 Add User 按钮 → `Button` + `openDrawer(<AddUserEditor .../>, {title: 'Add User'})`（沿用现状调用）。
4. 行内页面头（未用 `PageHeader` 的话）顺手改为 `PageHeader` + `DataTableWrapper`，与其他页对齐。

- [ ] **Step 3: 重写 AddUserEditor**

先读现文件。antd `Form` → Task 5 模式：用户名/密码 zod schema（校验规则沿用现文件的 `rules`)，角色字段用 `FormField` + `RoleMultiSelect`(`render={({field}) => <RoleMultiSelect value={field.value ?? []} onChange={field.onChange} options={roleSelectorOptions}/>}`)。提交逻辑沿用现文件（`userApiClient.addUser` 等），成功 `toast.success` + `onSuccess()`，失败 `toast.error`。若 react-compiler lint 报错，文件首行加 `"use no memo"`。

- [ ] **Step 4: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design" src/pages/user
```

手动走查：角色多选（搜索、勾选、移除徽标）即时保存；解锁/删除确认；Add User 抽屉表单校验与提交。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate user page with role multi-select"
```

---

### Task 9: 页面迁移 4/8 —— RolePage（含 RoleEditor 动态列表）

**Files:**
- Overwrite: `dashboard/src/pages/role/RolePage.tsx`
- Overwrite: `dashboard/src/pages/role/RoleEditor.tsx`
- Overwrite: `dashboard/src/components/namespace/ResourceActionSelector.tsx`（若被 RoleEditor 引用；先读现文件确认其 props)

**Interfaces:**
- Consumes: 全部基建；`roleApiClient`;`NamespaceSelector`(Task 2 combobox)。
- Produces: 无。

- [ ] **Step 1: 重写 RolePage**

先读现文件。列与操作按既有规则转换（`createSearchColumn` + `createActionColumn`,Add/Edit 走 `openDrawer(<RoleEditor .../>)`，删除 danger+confirm)，页面头统一为 `PageHeader` + `DataTableWrapper`。

- [ ] **Step 2: 重写 RoleEditor —— `Form.List` → `useFieldArray`**

先读现文件，记录：`initialValues` 形状（`{roleName, resourceBinds: [{namespace, actions: []}]}` 或类似，以现文件/generated 类型为准）、提交 API、校验规则。

zod schema 骨架（按现文件字段名调整）:

```tsx
const resourceBindSchema = z.object({
    namespace: z.string().min(1, 'Namespace is required'),
    actions: z.array(z.string()).min(1, 'Select at least one action'),
});
const schema = z.object({
    roleName: z.string().min(1, 'Role name is required'),
    resourceBinds: z.array(resourceBindSchema),
});
type Values = z.infer<typeof schema>;
```

动态列表核心：

```tsx
const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: initialValues ?? {roleName: '', resourceBinds: []},
});
const {fields, append, remove} = useFieldArray({control: form.control, name: 'resourceBinds'});
```

每行渲染（`FormField name={`resourceBinds.${index}.namespace`}` → `NamespaceSelector`;`resourceBinds.${index}.actions` → `ResourceActionSelector` 重写版），行尾删除按钮（`Trash2` icon，`variant="ghost" size="icon"`)，底部 `Button variant="outline" onClick={() => append({namespace: '', actions: []})}` 添加行。提交逻辑沿用现文件。

- [ ] **Step 3: 重写 ResourceActionSelector**

先读现文件确认其 props（大概率 `value/onChange/options` 或固定资源动作集）。用 Task 8 `RoleMultiSelect` 同款 Popover+Command 多选实现（options 来源以现文件为准），保持导出名称与 props 兼容。

- [ ] **Step 4: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design" src/pages/role src/components/namespace
```

手动走查：新增/编辑角色；动态增删授权行；行级校验（空命名空间/空动作列表报错）；提交后列表刷新。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate role page with useFieldArray resource binds"
```

---

### Task 10: 页面迁移 5/8 —— ServicePage 族

**Files:**
- Overwrite: `dashboard/src/pages/service/ServicePage.tsx`
- Overwrite: `dashboard/src/pages/service/ServiceInstanceTable.tsx`
- Overwrite: `dashboard/src/pages/service/ServiceInstanceEditor.tsx`
- Overwrite: `dashboard/src/pages/service/AddServiceForm.tsx`

**Interfaces:**
- Consumes: 全部基建；`serviceApiClient`;Monaco(`@monaco-editor/react` 的 `Editor`);`useDrawer()`。
- Produces: RHF + Monaco 的 `Controller` 模式（Task 11 复用）:

```tsx
<FormField control={form.control} name="metadata" render={({field}) => (
    <FormItem>
        <FormLabel>Metadata</FormLabel>
        <FormControl>
            <Editor
                height="200px"
                language="json"
                theme={resolvedTheme === 'dark' ? 'vs-dark' : 'vs'}
                value={field.value}
                onChange={(v) => field.onChange(v ?? '')}
                options={{minimap: {enabled: false}}}
            />
        </FormControl>
        <FormMessage/>
    </FormItem>
)}/>
```

- [ ] **Step 1: 重写 ServicePage**

先读现文件。服务统计列 → `createSearchColumn`（保留现状可搜索列）;Action 列 → `createActionColumn`;`renderExpanded={(row) => <ServiceInstanceTable namespace={currentNamespace} serviceId={row.serviceId}/>}`（props 以现文件为准）接入行展开；Add Service 沿用 `openDrawer(<AddServiceForm .../>)`。

- [ ] **Step 2: 重写 ServiceInstanceTable**

嵌套表格直接用 `DataTable`（关闭 `showViewOptions`);Edit → `openDrawer(<ServiceInstanceEditor .../>)`;Remove → danger+confirm。实例状态等徽标用 `Badge`(`variant` 语义化：在线 `default`、异常 `destructive`，以现文件逻辑为准）。

- [ ] **Step 3: 重写 ServiceInstanceEditor**

antd `Form` + `InputNumber` + `Switch` + Monaco → Task 5 模式：`InputNumber` → `Input type="number"`(zod `z.coerce.number()`);`Switch` → shadcn `Switch`(`checked={field.value} onCheckedChange={field.onChange}`)；元数据 Monaco 用上方 `Controller` 模式，`resolvedTheme` 来自 `useTheme()`。schema 校验规则沿用现文件。

- [ ] **Step 4: 重写 AddServiceForm**

同 Task 5 模式（serviceId 输入 + 提交，逻辑以现文件为准）。

- [ ] **Step 5: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design" src/pages/service
```

手动走查：服务列表搜索/排序；行展开实例表；实例编辑（含 Monaco 元数据、数字与开关字段）/删除；手动注册服务；暗色下 Monaco 主题正确。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate service pages with instance editor"
```

---

### Task 11: 页面迁移 6/8 —— ConfigPage 族（最重页面）

**Files:**
- Overwrite: `dashboard/src/pages/config/ConfigPage.tsx`
- Overwrite: `dashboard/src/pages/config/ConfigVersionTable.tsx`
- Overwrite: `dashboard/src/pages/config/ConfigEditor.tsx`
- Overwrite: `dashboard/src/pages/config/ConfigImporter.tsx`
- Overwrite: `dashboard/src/pages/config/ConfigVersionDiffer.tsx`
- Overwrite: `dashboard/src/components/namespace/ConfigFormatSelector.tsx`、`ImportPolicySelector.tsx`、`SchemaSelector.tsx`（若存在且被引用；先读现文件确认）
- Create: `dashboard/src/components/form/FileDropzone.tsx`
- Create: `dashboard/src/components/feedback/DescriptionList.tsx`

**Interfaces:**
- Consumes: 全部基建；Task 10 的 Monaco `Controller` 模式；`configApiClient`;`file-saver` 的 `saveAs`（不变）。
- Produces:
  - `FileDropzone({accept: string, onFile: (f: File) => void, hint?: string})`
  - `DescriptionList({items: {label: ReactNode; value: ReactNode}[]})`

- [ ] **Step 1: 创建 FileDropzone 与 DescriptionList**

创建 `dashboard/src/components/form/FileDropzone.tsx`:

```tsx
import {useRef, useState} from 'react';
import {UploadCloud} from 'lucide-react';
import {cn} from '@/lib/utils';

export interface FileDropzoneProps {
    accept: string;
    onFile: (file: File) => void;
    hint?: string;
}

export function FileDropzone({accept, onFile, hint = 'Click or drag file to upload'}: FileDropzoneProps) {
    const [dragging, setDragging] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);
    return (
        <div
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
                e.preventDefault();
                setDragging(false);
                const file = e.dataTransfer.files?.[0];
                if (file) onFile(file);
            }}
            className={cn(
                'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-8 text-center transition-colors',
                dragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50',
            )}
        >
            <UploadCloud className="h-8 w-8 text-muted-foreground"/>
            <p className="text-sm text-muted-foreground">{hint}</p>
            <input
                ref={inputRef}
                type="file"
                accept={accept}
                className="hidden"
                onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) onFile(file);
                    e.target.value = '';
                }}
            />
        </div>
    );
}
```

创建 `dashboard/src/components/feedback/DescriptionList.tsx`:

```tsx
import {Fragment, type ReactNode} from 'react';

export function DescriptionList({items}: { items: { label: ReactNode; value: ReactNode }[] }) {
    return (
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            {items.map((item, index) => (
                <Fragment key={index}>
                    <dt className="text-muted-foreground">{item.label}</dt>
                    <dd className="font-medium break-all">{item.value}</dd>
                </Fragment>
            ))}
        </dl>
    );
}
```

- [ ] **Step 2: 重写各选择器**

逐个读 `ConfigFormatSelector`/`ImportPolicySelector`/`SchemaSelector` 现文件，把 antd `Select` 换成 shadcn `Select`:

```tsx
<Select value={value} onValueChange={onChange}>
    <SelectTrigger className="w-full"><SelectValue placeholder={placeholder}/></SelectTrigger>
    <SelectContent>
        {options.map((opt) => <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>)}
    </SelectContent>
</Select>
```

保持各选择器的导出名称与 props 兼容（以现文件为准）。

- [ ] **Step 3: 重写 ConfigPage**

先读现文件。配置列表列 → `createSearchColumn`;Action 列（Edit/Delete/Export 等，以现文件为准）→ `createActionColumn`；行展开 `renderExpanded` → `ConfigVersionTable`；头部 Add/Import/Export 按钮组（`Space.Compact` → `div className="flex gap-2"`);Export 沿用 `saveAs`；删除 danger+confirm。

- [ ] **Step 4: 重写 ConfigVersionTable**

版本历史表（嵌套）→ `DataTable`(`showViewOptions={false}`);Diff/Rollback 操作沿用 `openDrawer(<ConfigVersionDiffer .../>)`;Rollback danger+confirm（文案以现文件为准）。

- [ ] **Step 5: 重写 ConfigEditor**

`Descriptions` → `DescriptionList`;Monaco `Editor` 用 Task 10 的 `Controller` 模式（语言随 `ConfigFormatSelector` 值映射，沿用现文件映射表）；提交/取消逻辑不变。

- [ ] **Step 6: 重写 ConfigImporter**

antd `Dragger` → `FileDropzone accept=".zip"`（选中后存入 RHF 字段或本地 state，以现文件提交流程为准）；导入策略 → 重写后的 `ImportPolicySelector`;FormData 上传逻辑不变。

- [ ] **Step 7: 重写 ConfigVersionDiffer**

Monaco `DiffEditor` 保留，`theme` 接 `useTheme()`;`Descriptions` → `DescriptionList`；回滚按钮接 `ConfirmDialog`。

- [ ] **Step 8: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design" src/pages/config src/components/form
```

手动走查（完整）:config CRUD；版本历史展开；Diff 对比渲染；回滚确认后生效；zip 导入（拖拽 + 点击两种方式）与导出；暗色下 Editor/DiffEditor 主题正确。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate config pages with dropzone importer and monaco diff"
```

---

### Task 12: 页面迁移 7/8 —— DashboardPage（拓扑 + 统计卡 + 全屏）

**Files:**
- Overwrite: `dashboard/src/pages/dashboard/DashboardPage.tsx`
- Modify: `dashboard/src/components/topology/Topology.tsx`、`ServiceNode.tsx`、`topologies.ts`（颜色令牌化）
- Create: `dashboard/src/components/topology/FullscreenButton.tsx`
- Create: `dashboard/src/hooks/useCountUp.ts`
- Delete: DashboardPage 内联 `<style>` 块（迁移时随文件重写移除）
- Remove dependency use: `@ahoo-wang/fetcher-viewer` 的 `Fullscreen`

**Interfaces:**
- Consumes: 全部基建；`statApiClient`;`@xyflow/react`。
- Produces:
  - `useCountUp(target: number, durationMs?: number): number` — `@/hooks/useCountUp`
  - `FullscreenButton({targetRef: RefObject<HTMLElement | null>})` — `@/components/topology/FullscreenButton`

- [ ] **Step 1: 创建 useCountUp**

创建 `dashboard/src/hooks/useCountUp.ts`:

```ts
import {useEffect, useState} from 'react';

export function useCountUp(target: number, durationMs = 800): number {
    const [value, setValue] = useState(0);
    useEffect(() => {
        const start = performance.now();
        let raf = 0;
        const tick = (now: number) => {
            const progress = Math.min((now - start) / durationMs, 1);
            setValue(Math.round(target * (1 - Math.pow(1 - progress, 3))));
            if (progress < 1) {
                raf = requestAnimationFrame(tick);
            }
        };
        raf = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(raf);
    }, [target, durationMs]);
    return value;
}
```

- [ ] **Step 2: 创建 FullscreenButton**

创建 `dashboard/src/components/topology/FullscreenButton.tsx`:

```tsx
import {useEffect, useState, type RefObject} from 'react';
import {Maximize2, Minimize2} from 'lucide-react';
import {Button} from '@/components/ui/button';

export function FullscreenButton({targetRef}: { targetRef: RefObject<HTMLElement | null> }) {
    const [isFullscreen, setIsFullscreen] = useState(false);
    useEffect(() => {
        const onChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
        document.addEventListener('fullscreenchange', onChange);
        return () => document.removeEventListener('fullscreenchange', onChange);
    }, []);
    const toggle = () => {
        if (document.fullscreenElement) {
            void document.exitFullscreen();
        } else {
            void targetRef.current?.requestFullscreen();
        }
    };
    return (
        <Button variant="outline" size="icon" onClick={toggle} aria-label="Toggle fullscreen">
            {isFullscreen ? <Minimize2 className="h-4 w-4"/> : <Maximize2 className="h-4 w-4"/>}
        </Button>
    );
}
```

- [ ] **Step 3: 拓扑颜色令牌化**

读 `components/topology/` 三个文件，把硬编码颜色集中为：

```ts
// topologies.ts 顶部新增
export interface FlowTheme {
    background: string;   // Background 点色
    edge: string;
    nodeBg: string;
    nodeBorder: string;
    nodeText: string;
}

export const flowThemes: Record<'light' | 'dark', FlowTheme> = {
    light: {background: '#e4e4e7', edge: '#a1a1aa', nodeBg: '#ffffff', nodeBorder: '#667eea', nodeText: '#18181b'},
    dark: {background: '#3f3f46', edge: '#52525b', nodeBg: '#27272a', nodeBorder: '#a5b4fc', nodeText: '#f4f4f5'},
};
```

`Topology.tsx`:`const {resolvedTheme} = useTheme(); const flowTheme = flowThemes[resolvedTheme];` 并把 `Background`/`MiniMap`/边/`ServiceNode` 的颜色改为读 `flowTheme`（搜索高亮逻辑不变，高亮色用品牌紫 `#667eea`/暗色 `#a5b4fc`)。

- [ ] **Step 4: 重写 DashboardPage**

先读现文件。规则：
1. `Row/Col/Card` → `div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4"` + shadcn `Card`;4 张统计卡（namespaces/configs/services/instances，以现文件数据源为准）数值用 `useCountUp`;hover 微交互用 Tailwind(`transition-shadow hover:shadow-md`)，删除 110 行内联 `<style>`。
2. 拓扑卡片：shadcn `Card`,header 含搜索输入（沿用现状高亮逻辑）+ `FullscreenButton`(targetRef 指向卡片容器 `useRef<HTMLDivElement>`)；移除 `@ahoo-wang/fetcher-viewer` 的 `Fullscreen` import。
3. `Skeleton` → shadcn `Skeleton` 或直接 `Spinner`。

- [ ] **Step 5: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design\|fetcher-viewer" src/pages/dashboard src/components/topology
```

手动走查：统计卡数字滚动；拓扑搜索高亮；全屏进出正常；暗色下节点/边/背景对比度可读。

```bash
git add -A dashboard && git commit -m "feat(dashboard): migrate home page with themed topology and count-up stat cards"
```

---

### Task 13: 页面迁移 8/8 —— LoginPage（含粒子背景重做）

**Files:**
- Overwrite: `dashboard/src/pages/login/LoginPage.tsx`
- Create: `dashboard/src/pages/login/ParticleBackground.tsx`
- Delete: `dashboard/src/pages/login/LoginPage.css`

**Interfaces:**
- Consumes: 全部基建；现有登录提交逻辑（`useSecurityContext` 的 signIn 流程，以现文件为准）。
- Produces: 无。

- [ ] **Step 1: 创建 ParticleBackground**

创建 `dashboard/src/pages/login/ParticleBackground.tsx`（轻量 canvas 粒子网络，替代 487 行 CSS 动画）:

```tsx
import {useEffect, useRef} from 'react';

interface Particle {
    x: number;
    y: number;
    vx: number;
    vy: number;
}

export function ParticleBackground() {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        let raf = 0;
        const particles: Particle[] = [];
        const resize = () => {
            canvas.width = canvas.offsetWidth * devicePixelRatio;
            canvas.height = canvas.offsetHeight * devicePixelRatio;
        };
        resize();
        window.addEventListener('resize', resize);

        const count = Math.min(80, Math.floor((canvas.offsetWidth * canvas.offsetHeight) / 15000));
        for (let i = 0; i < count; i++) {
            particles.push({
                x: Math.random() * canvas.width,
                y: Math.random() * canvas.height,
                vx: (Math.random() - 0.5) * 0.4 * devicePixelRatio,
                vy: (Math.random() - 0.5) * 0.4 * devicePixelRatio,
            });
        }

        const linkDistance = 140 * devicePixelRatio;
        const render = () => {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            for (const p of particles) {
                p.x += p.vx;
                p.y += p.vy;
                if (p.x < 0 || p.x > canvas.width) p.vx *= -1;
                if (p.y < 0 || p.y > canvas.height) p.vy *= -1;
                ctx.beginPath();
                ctx.arc(p.x, p.y, 1.5 * devicePixelRatio, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255,255,255,0.5)';
                ctx.fill();
            }
            for (let i = 0; i < particles.length; i++) {
                for (let j = i + 1; j < particles.length; j++) {
                    const dx = particles[i].x - particles[j].x;
                    const dy = particles[i].y - particles[j].y;
                    const dist = Math.hypot(dx, dy);
                    if (dist < linkDistance) {
                        ctx.beginPath();
                        ctx.moveTo(particles[i].x, particles[i].y);
                        ctx.lineTo(particles[j].x, particles[j].y);
                        ctx.strokeStyle = `rgba(255,255,255,${0.25 * (1 - dist / linkDistance)})`;
                        ctx.lineWidth = devicePixelRatio * 0.5;
                        ctx.stroke();
                    }
                }
            }
            raf = requestAnimationFrame(render);
        };
        raf = requestAnimationFrame(render);
        return () => {
            cancelAnimationFrame(raf);
            window.removeEventListener('resize', resize);
        };
    }, []);

    return <canvas ref={canvasRef} className="absolute inset-0 h-full w-full" aria-hidden/>;
}
```

- [ ] **Step 2: 重写 LoginPage**

先读现文件，保留其提交处理（调用安全上下文的登录方法、错误提示）。UI 整体替换为：全屏品牌渐变底（`bg-gradient-to-br from-brand-from to-brand-to`)+ `ParticleBackground` + 居中 shadcn `Card`（玻璃质感：`bg-white/10 backdrop-blur-md border-white/20`,dark 下 `bg-black/20`);RHF + zod(username/password 均 `min(1)`）表单，提交按钮 loading 态（`Spinner` + disabled)；删除 `./LoginPage.css` import 与该文件。

- [ ] **Step 3: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
! grep -rn "antd\|@ant-design\|LoginPage.css" src/pages/login
test ! -f src/pages/login/LoginPage.css && echo "css deleted"
```

手动走查：登录成功跳转 /home；错误凭证 toast 报错；粒子动画流畅（CPU 占用低）；亮/暗主题下表单可读。

```bash
git add -A dashboard && git commit -m "feat(dashboard): rebuild login page with canvas particle background"
```

---

### Task 14: ⌘K 命令面板

**Files:**
- Create: `dashboard/src/lib/commands.ts`
- Create: `dashboard/src/components/command/CommandPalette.tsx`
- Modify: `dashboard/src/components/layout/AppHeader.tsx`（加入口按钮）
- Modify: `dashboard/src/components/layout/AuthenticatedLayout.tsx`（挂载 palette)
- Modify: `dashboard/src/pages/config/ConfigPage.tsx`、`service/ServicePage.tsx`、`user/UserPage.tsx`、`role/RolePage.tsx`（订阅 add 命令）

**Interfaces:**
- Consumes: `command`(shadcn,Task 2 已生成）、`dialog`;`useNavigate`;`useNamespacesContext`/`useCurrentNamespaceContext`;`useTheme`;`useDrawer`（不需要，见下）。
- Produces:
  - `emitCommand(cmd: DashboardCommand)` / `useDashboardCommand(cmd, handler)`,`DashboardCommand = 'add-config' | 'add-service' | 'add-user' | 'add-role'` — `@/lib/commands`
  - `CommandPalette()` — 全局挂载一次

- [ ] **Step 1: 创建命令事件总线**

创建 `dashboard/src/lib/commands.ts`:

```ts
import {useEffect} from 'react';

export type DashboardCommand = 'add-config' | 'add-service' | 'add-user' | 'add-role';

const EVENT_NAME = 'cosky:dashboard-command';

export function emitCommand(cmd: DashboardCommand) {
    window.dispatchEvent(new CustomEvent(EVENT_NAME, {detail: cmd}));
}

export function useDashboardCommand(cmd: DashboardCommand, handler: () => void) {
    useEffect(() => {
        const listener = (e: Event) => {
            if ((e as CustomEvent<DashboardCommand>).detail === cmd) {
                handler();
            }
        };
        window.addEventListener(EVENT_NAME, listener);
        return () => window.removeEventListener(EVENT_NAME, listener);
    }, [cmd, handler]);
}
```

- [ ] **Step 2: 创建 CommandPalette**

创建 `dashboard/src/components/command/CommandPalette.tsx`:

```tsx
import {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
    FileText, KeyRound, LayoutDashboard, Moon, Network, Plus,
    ScrollText, Server, Sun, Users,
} from 'lucide-react';
import {
    CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator,
} from '@/components/ui/command';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useTheme} from '@/theme/ThemeProvider';
import {emitCommand, type DashboardCommand} from '@/lib/commands';

const pages = [
    {path: '/home', label: 'Dashboard', icon: LayoutDashboard},
    {path: '/config', label: 'Configuration', icon: FileText},
    {path: '/service', label: 'Service', icon: Server},
    {path: '/namespace', label: 'Namespace', icon: Network},
    {path: '/user', label: 'User', icon: Users},
    {path: '/role', label: 'Role', icon: KeyRound},
    {path: '/audit-log', label: 'Audit Log', icon: ScrollText},
];

const addActions: { cmd: DashboardCommand; label: string; path: string }[] = [
    {cmd: 'add-config', label: 'Add Config', path: '/config'},
    {cmd: 'add-service', label: 'Add Service', path: '/service'},
    {cmd: 'add-user', label: 'Add User', path: '/user'},
    {cmd: 'add-role', label: 'Add Role', path: '/role'},
];

export function CommandPalette() {
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const {namespaces} = useNamespacesContext();
    const {currentNamespace, setCurrent} = useCurrentNamespaceContext();
    const {resolvedTheme, setTheme} = useTheme();

    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
                e.preventDefault();
                setOpen((prev) => !prev);
            }
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, []);

    const runCommand = (fn: () => void) => {
        setOpen(false);
        fn();
    };

    return (
        <CommandDialog open={open} onOpenChange={setOpen}>
            <CommandInput placeholder="Type a command or search..."/>
            <CommandList>
                <CommandEmpty>No results found.</CommandEmpty>
                <CommandGroup heading="Pages">
                    {pages.map((page) => (
                        <CommandItem key={page.path} value={page.label}
                                     onSelect={() => runCommand(() => navigate(page.path))}>
                            <page.icon className="mr-2 h-4 w-4"/>
                            {page.label}
                        </CommandItem>
                    ))}
                </CommandGroup>
                <CommandSeparator/>
                <CommandGroup heading="Namespace">
                    {namespaces.map((ns) => (
                        <CommandItem key={ns} value={`namespace ${ns}`}
                                     onSelect={() => runCommand(() => setCurrent(ns))}>
                            <Network className="mr-2 h-4 w-4"/>
                            {ns}
                            {ns === currentNamespace && <span className="ml-auto text-xs text-primary">current</span>}
                        </CommandItem>
                    ))}
                </CommandGroup>
                <CommandSeparator/>
                <CommandGroup heading="Actions">
                    {addActions.map((action) => (
                        <CommandItem key={action.cmd} value={action.label}
                                     onSelect={() => runCommand(() => {
                                         navigate(action.path);
                                         emitCommand(action.cmd);
                                     })}>
                            <Plus className="mr-2 h-4 w-4"/>
                            {action.label}
                        </CommandItem>
                    ))}
                    <CommandItem value="toggle theme"
                                 onSelect={() => runCommand(() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark'))}>
                        {resolvedTheme === 'dark' ? <Sun className="mr-2 h-4 w-4"/> : <Moon className="mr-2 h-4 w-4"/>}
                        Toggle Theme
                    </CommandItem>
                </CommandGroup>
            </CommandList>
        </CommandDialog>
    );
}
```

注意：`emitCommand` 在 `navigate` 之后同步发出，目标页若尚未挂载会丢事件。处理：`runCommand` 内改为 `navigate(action.path); setTimeout(() => emitCommand(action.cmd), 300);`(lazy 页面挂载窗口）。

- [ ] **Step 3: 挂载与入口**

`AuthenticatedLayout.tsx`：在 `<SidebarProvider>` 内（`SidebarInset` 之后或其中）渲染一次 `<CommandPalette/>` 并 import。

`AppHeader.tsx`：在 `ThemeToggle` 前插入入口按钮：

```tsx
<Button
    variant="outline"
    className="hidden w-48 justify-between text-muted-foreground sm:flex"
    onClick={() => window.dispatchEvent(new KeyboardEvent('keydown', {key: 'k', metaKey: true}))}
>
    <span className="text-sm">Search...</span>
    <kbd className="pointer-events-none rounded border bg-muted px-1.5 text-xs">⌘K</kbd>
</Button>
```

- [ ] **Step 4: 页面订阅 add 命令**

在四个页面的组件体内各加一行（处理函数用各页现有的"打开新增抽屉"函数）:

```tsx
// ConfigPage
useDashboardCommand('add-config', () => openEditor(/* 新增态,以现文件 handleAdd 为准 */));
// ServicePage
useDashboardCommand('add-service', () => /* 现文件打开 AddServiceForm 的函数 */);
// UserPage
useDashboardCommand('add-user', () => /* 现文件打开 AddUserEditor 的函数 */);
// RolePage
useDashboardCommand('add-role', () => /* 现文件打开 RoleEditor 新增态的函数 */);
```

并各自 `import {useDashboardCommand} from '@/lib/commands';`。

- [ ] **Step 5: 门禁 + 走查 + 提交**

```bash
cd dashboard
pnpm lint && pnpm build
```

手动走查：⌘K / Ctrl+K 开关面板；页面跳转；命名空间切换并观察到表格数据刷新；Add Config 从任意页触发后 Config 页抽屉自动打开；Toggle Theme 生效；Esc 关闭。

```bash
git add -A dashboard && git commit -m "feat(dashboard): add command palette with navigation, namespace switch and quick actions"
```

---

### Task 15: 清理 —— 卸载 antd、收尾、bundle 对比

**Files:**
- Modify: `dashboard/package.json`（移除依赖）
- Overwrite: `dashboard/src/App.tsx`（去 ConfigProvider/AntdApp/antd ErrorBoundary)
- Modify: `dashboard/src/components/error/ErrorBoundary.tsx`(fallback UI 换 Tailwind)
- Delete: `dashboard/src/App.css`（无引用死文件）
- Modify: `dashboard/CLAUDE.md`(Tech Stack 段）

**Interfaces:**
- Consumes: 一切前序任务。
- Produces: 最终态；无 antd 残留。

- [ ] **Step 1: 确认无 antd 引用残留**

```bash
cd dashboard
grep -rn "from 'antd'\|from \"antd\"\|antd/es\|@ant-design\|fetcher-viewer" src
```

预期无输出。若有输出，回到对应页面任务补齐迁移后再继续。

- [ ] **Step 2: 卸载依赖**

```bash
cd dashboard
pnpm remove antd @ant-design/icons @ahoo-wang/fetcher-viewer @types/dagre
```

- [ ] **Step 3: 收尾 App.tsx**

整体替换 `dashboard/src/App.tsx`:

```tsx
import {BrowserRouter} from 'react-router-dom';
import './services/fetcher';
import {Toaster} from 'sonner';
import {ThemeProvider, useTheme} from './theme/ThemeProvider.tsx';
import {ErrorBoundary} from './components/error/ErrorBoundary.tsx';
import {AppRoutes} from './AppRoutes.tsx';

function ThemedToaster() {
    const {resolvedTheme} = useTheme();
    return <Toaster richColors closeButton position="top-center" theme={resolvedTheme}/>;
}

function App() {
    return (
        <ThemeProvider>
            <ThemedToaster/>
            <ErrorBoundary>
                <BrowserRouter>
                    <AppRoutes/>
                </BrowserRouter>
            </ErrorBoundary>
        </ThemeProvider>
    );
}

export default App;
```

`dashboard/src/components/error/ErrorBoundary.tsx` 的 fallback 改为 Tailwind（其余逻辑不动）:

```tsx
            return (
                <div className="flex flex-col items-center gap-3 p-8 text-center">
                    <h2 className="text-lg font-semibold">Something went wrong.</h2>
                    <details className="whitespace-pre-wrap text-sm text-muted-foreground">
                        {this.state.error?.toString()}
                    </details>
                </div>
            );
```

- [ ] **Step 4: 删除死文件与遗留 barrel**

```bash
cd dashboard
rm src/App.css
grep -rn "App.css" src                        # 预期无输出
grep -rn "components/table'" src              # 预期无输出(遗留 barrel 的引用形式)
git rm src/components/table/index.tsx         # 遗留 antd 列助手
```

随后把 `src/components/table/SearchFilter.tsx` 末尾的 `useSearchFilter` hook 及其 `antd/es/table/interface` 类型 import 一并删除（此时已无引用）。

- [ ] **Step 5: 全量门禁**

```bash
cd dashboard
pnpm install
pnpm lint
pnpm build
! grep -rn "antd\|@ant-design\|fetcher-viewer" src
```

预期：lint 无错误；build 成功；grep 无输出（`pnpm-lock.yaml`、`package.json` 中也不再有这三个依赖）。

- [ ] **Step 6: bundle 对比并更新 CLAUDE.md**

```bash
cd dashboard
du -sh dist && ls -la dist/assets | head -20
```

与 Task 0 Step 1 基线对比，把结果写进提交信息（如 `dist: 12.4MB → 7.1MB`)。

更新 `dashboard/CLAUDE.md` 的 Tech Stack 段：

```markdown
### Tech Stack
- **Framework**: React 19 with TypeScript
- **Build**: Vite with React Compiler (babel-plugin-react-compiler)
- **UI**: shadcn/ui (Radix UI) + Tailwind CSS v4, lucide-react icons, sonner toasts
- **Routing**: React Router v7
- **Tables**: @tanstack/react-table (shared DataTable in `src/components/table/`)
- **Forms**: react-hook-form + zod
- **Topology**: @xyflow/react for service topology visualization
- **Config Editor**: Monaco Editor
- **API Client**: @ahoo-wang/fetcher with auto-generated clients
```

同时把 `### State Management` 段中 `DrawerProvider/DrawerContext - Ant Design drawer for detail panels` 改为 `DrawerProvider/DrawerContext - Global Sheet (side panel) host for detail editors`。

- [ ] **Step 7: 最终手动走查（spec §8 全量清单）**

`pnpm dev` 下逐项确认：登录/登出；token 过期自动刷新；命名空间切换驱动各页刷新；config CRUD、版本历史、diff、回滚、zip 导入/导出；service 列表、实例编辑/删除、手动注册；user 角色绑定/解锁/删除；role 动态授权列表；audit-log 服务端翻页；拓扑搜索/高亮/全屏；修改密码；light/dark 切换后 Monaco 与拓扑可读；⌘K 面板全部命令。

- [ ] **Step 8: 提交**

```bash
git add -A dashboard && git commit -m "refactor(dashboard): remove antd and finalize shadcn/ui migration (dist: <基线> → <现值>)"
```

---

## Self-Review 记录

- **Spec 覆盖**:§3 选型（T0/T1/T4/T5 依赖清单）、§4 令牌与布局（T0/T2)、§5 映射与交互（T1/T3/T4/T5/T6-T13/T14)、§6 目录（File Structure)、§7 顺序（T0→T15，命令面板单独成任务，置于页面之后、清理之前）、§8 验证（各任务门禁 + T15 走查）、§9 风险（T0 preflight 备注、T5 `"use no memo"`、T10/T11 Monaco 主题、T3 嵌套 portal)。✅
- **占位符**：无 TBD/TODO；所有"以现文件为准"处均已给出明确转换规则与目标代码模式，现文件即列数据/校验规则的事实来源。✅
- **类型一致性**:`DrawerOptions`(T3)与 T2 AppHeader 调用一致；`DataTablePagination`(T4)与 T7 使用一致；`createActionColumn`/`ActionItem`(T4)与 T6+ 使用一致；`NamespaceSelector` props(T2)与 T9 RoleEditor 用法一致；`useDashboardCommand`(T14)四页订阅一致。✅
