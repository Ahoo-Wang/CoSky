# AGENTS.md — Wiki (VitePress Documentation Site)

## Build & Run Commands

```bash
cd wiki

# Install dependencies
pnpm install

# Run dev server (hot-reload)
pnpm dev

# Build for production
pnpm build

# Preview production build
pnpm preview

# Validate Mermaid syntax in all .md files
pnpm validate:mermaid
```

## Project Structure

```
wiki/
├── .vitepress/
│   ├── config.mts          # VitePress config with i18n (en + zh)
│   ├── theme/index.ts      # Custom theme (dark Mermaid, medium-zoom)
│   └── styles/index.css    # Dark theme CSS overrides
├── guide/                  # English documentation pages
├── zh/
│   └── guide/              # Chinese documentation pages
├── public/                 # Static assets (images, logo)
├── scripts/
│   └── validate-mermaid.mjs # Mermaid syntax validator & auto-fixer
├── package.json
└── pnpm-lock.yaml
```

## Content Conventions

- **Frontmatter**: Every page must have `title` and `description` in VitePress frontmatter
- **Mermaid diagrams**: Use dark-mode colors (fills `#2d333b`, borders `#6d5dfc`, text `#e6edf3`). Use `<br>` not `<br/>`. Add `autonumber` to all `sequenceDiagram` blocks.
- **Source citations**: Use format `[file_path:line_number](https://github.com/Ahoo-Wang/CoSky/blob/main/file_path#Lline_number)`
- **Diagrams**: Follow every Mermaid diagram with `<!-- Sources: file:line, file:line -->` comment
- **Cross-references**: Use relative Markdown links between pages
- **i18n**: English is the default (root). Chinese pages live in `zh/guide/` with identical filenames.

## Boundaries

- ✅ Run `pnpm validate:mermaid` after editing any Mermaid diagrams
- ✅ Keep English and Chinese pages in sync (same structure, same filenames)
- ✅ Test with `pnpm dev` before committing
- ⚠️ Ask before modifying `.vitepress/config.mts` — sidebar structure affects navigation
- 🚫 Never delete generated documentation pages without confirmation
- 🚫 Never modify the dark theme colors without testing in both light and dark modes
