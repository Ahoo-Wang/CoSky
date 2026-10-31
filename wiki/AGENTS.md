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

# Parse every Mermaid diagram in headless Chrome (CI runs this too)
pnpm check:mermaid
```

## Project Structure

```
wiki/
├── .vitepress/
│   ├── config.mts          # VitePress config with i18n (en + zh)
│   ├── config/en.ts, zh.ts # Navigation and sidebar per language
│   └── theme/              # Mermaid.vue renders diagrams, with zoom and full screen
├── guide/                  # English pages
├── zh/guide/               # Chinese pages, same file names
├── public/                 # Static assets (images, logo)
└── scripts/
    ├── generate-llms-full.mjs     # llms.txt (wiki and repository root) and llms-full.txt
    └── check-mermaid.mjs          # Mermaid validation with the real parser
```

## Information Architecture

Pages are organized by what the reader wants to do, not by code module. Each page belongs to one sidebar section:

| Section | Pages | Reader |
|---|---|---|
| Introduction | `index`, `getting-started` | Evaluators, newcomers |
| Guides | `configuration`, `service-discovery`, `sdk` | Application developers |
| Operations | `deployment`, `security`, `dashboard`, `upgrade-*` | Operators of the server |
| Reference | `properties`, `rest-api`, `requirements` | Anyone looking up a fact |
| Concepts | `architecture`, `performance` | Engineers evaluating or operating at scale |
| Contributing | `internals` | Contributors |

## Content Conventions

- **One fact, one place.** Properties live in `properties.md`, endpoints in `rest-api.md`, benchmark numbers in `performance.md`, the data model in `architecture.md`; other pages link to them instead of repeating them.
- **Start with the reader**: the first paragraph says who the page is for and what it lets them do.
- **Verify against the code**: every property, default, endpoint and behavior is checked in the source before it is written.
- **Frontmatter**: every page has `title` and `description` (the description also feeds `llms.txt`).
- **Source links** point to files (`https://github.com/Ahoo-Wang/CoSky/blob/main/<path>`), never to line numbers, which go stale with every change.
- **Diagrams** only where they explain a mechanism that prose cannot. Leave out `%%{init}` and `style` lines, so that the colors follow the light or dark theme. Use `<br>` for line breaks, and never `;` in sequence-diagram message text.
- **Cross-references**: relative Markdown links between pages; anchors use VitePress slugs.
- **i18n**: English is the default (root). Every page exists in `zh/guide/` with the same name and structure.

## Boundaries

- ✅ Run `node scripts/generate-llms-full.mjs` after adding, removing or renaming a page (and add it to the script's sections)
- ✅ Run `pnpm check:mermaid` after editing any Mermaid diagram
- ✅ Run `pnpm build`: it fails on dead links
- ✅ Keep English and Chinese pages in sync (same structure, same filenames)
- ✅ Test with `pnpm dev` before committing
- ⚠️ Ask before modifying `.vitepress/config.mts` — sidebar structure affects navigation
- 🚫 Never delete generated documentation pages without confirmation
- 🚫 Never modify the dark theme colors without testing in both light and dark modes
