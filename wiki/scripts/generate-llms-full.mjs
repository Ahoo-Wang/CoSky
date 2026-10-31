// Generates the LLM-oriented indexes from one list of pages, so they never diverge:
// - wiki/llms-full.txt: every page's content,
// - wiki/llms.txt and the repository's llms.txt: the page index (llmstxt.org format).
import fs from "node:fs";
import path from "node:path";

const WIKI_ROOT = path.resolve(import.meta.dirname, "..");
const REPO_ROOT = path.resolve(WIKI_ROOT, "..");

const sections = [
  { title: "Introduction", pages: ["guide/index.md", "guide/getting-started.md"] },
  { title: "Guides", pages: ["guide/configuration.md", "guide/service-discovery.md", "guide/sdk.md"] },
  {
    title: "Operations",
    pages: ["guide/deployment.md", "guide/security.md", "guide/dashboard.md", "guide/upgrade-8.md"],
  },
  { title: "Reference", pages: ["guide/properties.md", "guide/rest-api.md", "guide/requirements.md"] },
  { title: "Concepts", pages: ["guide/architecture.md", "guide/performance.md"] },
  { title: "Contributing", pages: ["guide/internals.md"] },
];

const SUMMARY =
  "Configuration management and service discovery for microservices: a small server in front of Redis, with clients that read from memory.";

function parse(relativePath) {
  const raw = fs.readFileSync(path.join(WIKI_ROOT, relativePath), "utf-8");
  const match = raw.match(/^---\n([\s\S]*?)\n---\n?/);
  const frontmatter = match ? match[1] : "";
  const field = (name) => frontmatter.match(new RegExp(`^${name}:\\s*"?(.*?)"?\\s*$`, "m"))?.[1] ?? "";
  return {
    path: relativePath,
    title: field("title"),
    description: field("description"),
    content: (match ? raw.substring(match[0].length) : raw).trim(),
  };
}

const parsed = sections.map((section) => ({ ...section, pages: section.pages.map(parse) }));

function index(linkPrefix) {
  let out = `# CoSky\n\n> ${SUMMARY}\n`;
  for (const section of parsed) {
    out += `\n## ${section.title}\n\n`;
    for (const page of section.pages) {
      out += `- [${page.title}](${linkPrefix}${page.path}): ${page.description}\n`;
    }
  }
  return out;
}

let full = "";
for (const page of parsed.flatMap((section) => section.pages)) {
  full += `<doc title="${page.title}" path="${page.path}">\n${page.content}\n</doc>\n\n`;
}

const outputs = {
  [path.join(WIKI_ROOT, "llms-full.txt")]: full.trim() + "\n",
  [path.join(WIKI_ROOT, "llms.txt")]: index(""),
  [path.join(REPO_ROOT, "llms.txt")]: index("wiki/"),
};
for (const [file, content] of Object.entries(outputs)) {
  fs.writeFileSync(file, content, "utf-8");
  console.log(`Generated ${path.relative(REPO_ROOT, file)} (${(content.length / 1024).toFixed(1)} KB)`);
}
