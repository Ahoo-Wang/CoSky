# Dashboard 重写设计：Ant Design → Radix UI + shadcn/ui

- **日期**:2026-08-16
- **状态**:待审阅
- **范围**:`dashboard/` 前端全部 UI 层（React 19 + Vite 8)

## 1. 背景与目标

dashboard 当前基于 Ant Design v6.3.7 + `@ant-design/icons`，约 4,600 行业务代码、60 个源文件、8 个页面（login、home、config、service、namespace、user、role、audit-log)。本次重写将组件体系整体替换为 **Radix UI + shadcn/ui**，并借机优化视觉与交互体验。

**目标**（按优先级）:

1. **技术栈现代化**:UI 组件源码自有（shadcn/ui 复制进仓库），摆脱 antd 黑盒样式与升级负担。
2. **视觉质感升级**:CSS 变量设计令牌、统一的留白/层级/圆角、支持 light + dark 双主题。
3. **交互体验优化**:⌘K 命令面板、统一且更强的表格（列显隐/过滤/排序/展开）、一致的 toast/确认/加载/空状态。
4. **包体积与性能**:移除 antd 全量运行时，按需加载的 Radix 组件 + Tailwind 原子化 CSS。

**非目标（YAGNI)**:

- 不引入 i18n（维持现状硬编码英文）。
- 不新增单元测试体系（维持现状；质量门禁为 `pnpm lint` + `pnpm build` + 手动走查清单）。
- 不改动 `@ahoo-wang/fetcher*` 数据/认证层、`src/generated/` 代码、路由结构、REST API。
- 不做 WebSocket/SSE 实时推送、不做移动端深度适配（桌面管理后台优先，保证移动端可用）。
- 不改变 CI/打包契约:`dashboard/` 下 `pnpm install / lint / build` 命令与 `dist/` 产物路径保持不变（被 `cosky-rest-api` distributions 与 `docker-deploy.yml` 依赖）。

## 2. 迁移策略（方案选型）

### 方案 A：原地页级渐进迁移（推荐）

在同一 `dashboard/` 内，先搭建 Tailwind + shadcn/ui 基建（设计令牌、主题、基础组件、Sheet host、DataTable、表单基建），随后**按页面由简到繁逐个替换** antd 组件，最后卸载 antd。antd 与新体系短期共存。

- ✅ 每一步 `lint`/`build` 保持绿，随时可中断、可回滚；review 粒度小。
- ✅ 共存期包体临时变大、antd 外观受 Tailwind preflight 轻微影响——通过快速推进缩短共存窗口，可接受。
- ✅ React Compiler、Vite 配置全程无需大改。

### 方案 B:big-bang 特性分支

单分支一次性完成全部替换再合并。

- ✅ 无共存期、历史干净。
- ❌ 长周期分支合并冲突风险高，中途不可发布，单次 review 无法消化。

### 方案 C：并行新应用

新建 `dashboard-v2` 完成后切换。

- ❌ 双份维护、切换时集成风险，对本规模（4.6k LOC）过度工程。

**决策：采用方案 A。** 迁移顺序见 §7。

## 3. 技术选型

### 引入

| 依赖 | 用途 |
|---|---|
| `tailwindcss` v4 + `@tailwindcss/vite` | 原子化 CSS,CSS-first 配置（无 `tailwind.config.js`) |
| shadcn/ui(CLI,style: `new-york`,base color: `zinc`) | 组件源码生成至 `src/components/ui/` |
| Radix UI primitives | 经 shadcn 组件间接引入（Dialog、DropdownMenu、Select、Switch、AlertDialog、Tooltip、Popover、Label、Slot 等） |
| `class-variance-authority` + `clsx` + `tailwind-merge` | `cn()` 与组件 variants(shadcn 标配） |
| `lucide-react` | 全部图标（替换 ~30 处 `@ant-design/icons`) |
| `sonner` | toast（替换 ~40 处 `App.useApp().message`) |
| `cmdk` | ⌘K 命令面板 |
| `@tanstack/react-table` v8 | DataTable 核心（替换 8 处 antd Table) |
| `react-hook-form` + `zod` + `@hookform/resolvers` | 表单与校验（替换 8 处 antd Form,`Form.List` → `useFieldArray`) |

### 移除

`antd`、`@ant-design/icons`、`@ahoo-wang/fetcher-viewer`（其 `Fullscreen` 组件基于 antd，仅 `DashboardPage` 使用 → 用原生 Fullscreen API 自写按钮替换）。

### 保留不动

`react-router-dom` v7、`@monaco-editor/react` + `monaco-editor`(`monacoConfig.ts` 不变，编辑器主题随暗色模式联动）、`@xyflow/react`（拓扑图，样式令牌化适配暗色）、`dayjs`、`file-saver`、`@ahoo-wang/fetcher` 全家桶（fetcher / fetcher-cosec / fetcher-decorator / fetcher-generator / fetcher-react / fetcher-storage)、`src/generated/` 全部代码、React Compiler 构建链路。

### 新增路径别名

`@/` → `src/`(vite `resolve.alias` + `tsconfig.app.json` paths,shadcn/ui 需要）。

## 4. 视觉设计

### 设计令牌（CSS 变量，oklch)

- **主色**：沿用品牌紫。`#667eea` → oklch 近似值作为 `primary`，深紫 `#764ba2` 用于渐变 accent（登录页、品牌时刻、拓扑高亮）。
- **中性色**:shadcn `zinc` 刻度。
- **圆角**:`--radius: 0.5rem`(8px，与现状一致）；卡片用 `lg`，按钮/输入用 `md`。
- **双主题**:light + dark,`class` 策略。`ThemeProvider` 持久化到 localStorage(`cosky:theme`)，默认跟随系统；header 提供切换开关。Monaco(`vs` / `vs-dark`）与 xyflow（节点/边/背景色令牌）随主题联动。
- 所有令牌定义在 `src/index.css` 的 `@theme` / `:root` / `.dark` 块中，组件一律经 `cn()` 消费语义类（`bg-background`、`text-muted-foreground` 等），禁止再出现散落的面板级内联样式。

### 布局（AuthenticatedLayout 重做）

- 现状：左侧深色 sider(`#001529`)+ 顶部渐变 header + `Watermark`。
- 新布局：采用 shadcn **Sidebar** 块——左侧可折叠侧栏（导航 + 折叠持久化，沿用现有 `useLayoutCollapsed` 存储键），顶部 header 内含：全局命名空间选择器、⌘K 命令面板入口、主题切换、用户 DropdownMenu（修改密码、登出）。
- 品牌渐变收敛为点缀：侧栏 logo 区与登录页，不再铺满 header。
- 水印：用 CSS 重复背景 overlay 自绘（保留用户名水印的安全语义）。

### 登录页

保留"电路/粒子动画背景"的个性，但将 487 行手写 CSS 收敛为 Tailwind + 一个轻量 canvas 粒子组件；表单卡片改为 shadcn Card + RHF 校验，暗色下同样成立。

## 5. 组件映射与交互设计

### antd → 新体系映射

| antd（用量） | 替换 | 说明 |
|---|---|---|
| `App.useApp().message`(~40 处） | `sonner` 的 `toast.success/error` | 全局 `<Toaster richColors closeButton />`，逐文件机械替换 |
| `Drawer`（全局 1 处宿主） | shadcn `Sheet`(Radix Dialog) | **保留 `DrawerProvider` 的 `openDrawer(node, {title, size})` 命令式 API 不变**，内部实现换成 Sheet；调用点零改动。可拖拽调整宽度用 pointer events 自实现（现状 antd `resizable` 的对等能力） |
| `Table`(8 处调用） | DataTable(`@tanstack/react-table` + shadcn Table) | 统一封装，见下 |
| `Form`(8 处，含 1 个 `Form.List`) | `react-hook-form` + zod schema + shadcn `Form` 组件；`Form.List` → `useFieldArray` | RoleEditor 的 namespace + resource-action 动态列表是重点 |
| `Popconfirm`(6 处） | shadcn `AlertDialog` | 统一封装 `ConfirmDialog`，受控/命令式双用法 |
| `Select` + 5 个封装选择器 | shadcn `Select`(Radix) | `NamespaceSelector` 等薄封装逐个改写 |
| `Button`/`Input`/`InputNumber`/`Switch`/`Card`/`Divider`/`Skeleton` | shadcn 对应组件（`Divider`→`Separator`,`InputNumber`→`Input type=number`) | 机械替换 |
| `Dropdown`/`Menu`/`Layout` | `DropdownMenu` + shadcn Sidebar 布局 | 仅 AuthenticatedLayout |
| `Descriptions`(2 处） | 自写 `DescriptionList`(`<dl>` + Tailwind) | ConfigEditor、ConfigVersionDiffer |
| `Upload.Dragger`(1 处） | 自写 dropzone（原生 drag events + `<input type=file>`) | ConfigImporter,zip 上传，不引入额外依赖 |
| `Watermark`(1 处） | CSS overlay | 见 §4 |
| `ConfigProvider` 主题令牌 | Tailwind `@theme` 令牌 | 见 §4 |
| `Row`/`Col`、`Space`、`Typography` | Tailwind flex/grid 类 + 语义标签 | 机械替换 |
| `fetcher-viewer` 的 `Fullscreen`(1 处） | 自写按钮 + 原生 Fullscreen API | DashboardPage 拓扑卡片 |

### 统一 DataTable 封装（`src/components/table/`）

现状痛点：搜索 filterDropdown 在 ConfigPage/ServicePage 手抄两份、列定义样板多、只有 audit 页用服务端分页。新封装提供：

- 列定义即 TanStack `ColumnDef`；助手函数 `createSearchColumn` / `createActionColumn` 平移现有思路。
- 内建能力：列显隐（ViewOptions)、列排序、关键字过滤（保留现有"列内搜索框"交互）、行展开（config 版本历史、service 实例两个场景）、客户端分页（默认）、**服务端分页模式**(audit-log:`pageIndex/pageSize/total` 驱动 `useQuery.setQuery`)。
- 统一三态：加载（Skeleton 行）、空（Empty 插画占位 + 引导操作）、错误（错误条 + 重试按钮）。

### ⌘K 命令面板（cmdk）

全局 `Cmd/Ctrl+K` 唤起：

- **导航**：跳转 8 个页面。
- **命名空间切换**:与 header 选择器等价，键盘可达。
- **动作**:Add Config / Add Service / Add User / Add Role、Toggle Theme。
- 数据源复用现有 contexts(`NamespacesContext`、路由表、Sheet host)。

### 反馈与细节

- toast 统一走 sonner；破坏性操作统一 `ConfirmDialog`；按钮 loading 用 `Spinner`(lucide `Loader2`)+ disabled。
- 路由级懒加载 fallback 由整页 Skeleton 收敛为居中 Spinner + 骨架屏，避免闪烁。
- 拓扑图（home)：搜索高亮、Fullscreen 保留；节点/边/背景改为读 CSS 变量，暗色可读。

## 6. 架构与目录（目标态）

```
dashboard/src/
├── components/
│   ├── ui/                 # shadcn 生成（button, input, select, sheet, dialog,
│   │                       #  alert-dialog, dropdown-menu, table, form, card, …)
│   ├── layout/             # AppSidebar, AppHeader, AuthenticatedLayout, ThemeToggle, Watermark
│   ├── table/              # DataTable, columns 助手, DataTablePagination, ViewOptions
│   ├── command/            # CommandPalette(cmdk)
│   ├── feedback/           # ConfirmDialog, Empty, ErrorState, Spinner
│   ├── namespace/  security/  topology/  error/   # 业务组件，内部实现替换、对外 props 不变
├── contexts/               # DrawerProvider(实现换 Sheet)/ namespace contexts,不变
├── theme/                  # ThemeProvider, useTheme
├── lib/utils.ts            # cn()
├── hooks/  pages/  services/  generated/  security/   # 结构与职责不变
└── index.css               # Tailwind v4 入口 + @theme 令牌 + light/dark 变量
```

原则：**基础设施先行，页面只换实现不换接口**;`components/ui/` 内的 shadcn 源码允许按需要就地定制（这是选择 shadcn 的核心收益）。

## 7. 实施顺序（每步 lint+build 绿，可独立提交）

| # | 步骤 | 内容 | 验收 |
|---|---|---|---|
| 0 | 基建 | Tailwind v4 接入、`@/` alias、`components.json`、`cn()`、首批 shadcn 组件、`ThemeProvider` 与暗色令牌 | `pnpm build` 绿；antd 页面仍正常（preflight 影响可接受） |
| 1 | toast | 引入 sonner，全量替换 ~40 处 `message.*`，移除对 `App.useApp` 的依赖 | grep 无 `useApp` |
| 2 | 布局 | Sidebar + header + ThemeToggle + Watermark + 用户菜单；重做 `AuthenticatedLayout` | 导航/折叠/命名空间切换/水印可用；两主题 |
| 3 | Sheet host | `DrawerProvider` 内部实现换 Sheet，含 resizable；全部编辑器走查 | 各编辑器打开/关闭/提交正常 |
| 4 | DataTable | TanStack 封装 + 三态 + 两种分页模式 | 供步骤 6 使用 |
| 5 | 表单基建 | RHF + zod + shadcn Form;`ConfirmDialog` | 供步骤 6 使用 |
| 6 | 页面迁移（由简到繁） | namespace → audit-log → user → role(Form.List 难点）→ service → config(Monaco/diff/导入导出最重）→ dashboard(拓扑/统计卡）→ login | 每页功能走查清单全过 |
| 7 | 清理 | 卸载 `antd`、`@ant-design/icons`、`@ahoo-wang/fetcher-viewer`；删残留样式与 `@types/dagre`（死依赖）；bundle 体积对比记录 | grep 无 antd 引用；`pnpm build` 产物显著减小 |

## 8. 验证方案

- **自动**:每步 `pnpm lint` 与 `pnpm build`(`tsc -b` 严格类型 + Vite 构建）必须绿；CI 工作流无需修改。
- **手动走查清单**（步骤 6/7 必做）：登录/登出/token 刷新；命名空间切换驱动各页刷新；config CRUD、版本历史、diff 对比、回滚、zip 导入/导出；service 列表、实例编辑/删除、手动注册；user 角色绑定/解锁/删除；role 的资源授权动态列表；audit-log 翻页；拓扑搜索/高亮/全屏；修改密码；light/dark 切换后 Monaco 与拓扑可读。
- **联调**:`pnpm dev` 指向 `VITE_API_BASE_URL` 的 dev 环境，或本地 `cosky-rest-api`(`pnpm generate` 链路不变）。

## 9. 风险与对策

| 风险 | 对策 |
|---|---|
| Tailwind preflight 影响共存期 antd 外观 | 共存期短（步骤 0→6 快速推进）；preflight 影响多为细节，验收以新组件页为准 |
| React Compiler 与 `react-hook-form` 的已知摩擦（`formState` proxy 记忆化告警） | 先按默认开启验证；若个别表单告警/异常，对该文件加 `"use no memo"` 指令豁免，不全局关闭 |
| Monaco / xyflow 暗色适配遗漏 | 步骤 2 定义联动 hook(`useTheme` → editor theme / flow 颜色），步骤 6 走查 |
| 全局 Sheet 替换后编辑器内嵌套交互（如 diff 中再弹确认）层级问题 | Radix Dialog 原生支持嵌套；`ConfirmDialog` 以 portal 挂载，走查覆盖 |
| shadcn CLI 生成物与仓库代码风格（ESLint/react-compiler 规则）冲突 | 生成后跑 `pnpm lint --fix`；个别豁免写入 eslint config 的 `src/components/ui` 规则覆盖 |

## 10. 假设（用户未逐一确认，审阅时可推翻）

1. 四个目标全部成立，优先级如 §1 排序。
2. 采用方案 A（原地渐进）。
3. 视觉保留品牌紫为 accent，中性色 zinc，默认亮色、支持暗色。
4. 交互增强范围限定为 §5 所列（⌘K、DataTable、统一反馈），不重构信息架构/导航结构。
5. 维持无测试体系与硬编码英文的现状；质量门禁为 lint + build + 手动走查。
