import fs from "node:fs";
import path from "node:path";

const WIKI_ROOT = path.resolve(import.meta.dirname, "..");

const MERMAID_REGEX = /```mermaid\s*\n([\s\S]*?)```/g;

const COMMON_ISSUES = [
  { pattern: /<br\/>/g, fix: "<br>", desc: "Replace <br/> with <br>" },
  {
    pattern: /style\s+(\w+)\s+fill:#fff/gi,
    fix: (m, id) => `style ${id} fill:#2d333b,color:#e6edf3`,
    desc: "Replace white fills with dark equivalents",
  },
  {
    pattern: /style\s+(\w+)\s+fill:#ffffff/gi,
    fix: (m, id) => `style ${id} fill:#2d333b,color:#e6edf3`,
    desc: "Replace white fills with dark equivalents",
  },
  {
    pattern: /style\s+(\w+)\s+fill:#f9f9f9/gi,
    fix: (m, id) => `style ${id} fill:#2d333b,color:#e6edf3`,
    desc: "Replace light gray fills with dark equivalents",
  },
  {
    pattern: /style\s+(\w+)\s+fill:#fafafa/gi,
    fix: (m, id) => `style ${id} fill:#2d333b,color:#e6edf3`,
    desc: "Replace light fills with dark equivalents",
  },
];

const VALID_DIAGRAM_TYPES = [
  "graph",
  "flowchart",
  "sequenceDiagram",
  "classDiagram",
  "stateDiagram",
  "erDiagram",
  "gantt",
  "pie",
  "gitGraph",
  "mindmap",
  "C4Context",
  "C4Container",
  "C4Component",
  "block-beta",
  "journey",
  "requirementDiagram",
  "quadrantChart",
  "sankey-beta",
  "timeline",
  "xychart-beta",
  "block-beta",
];

function isBlankMermaid(content) {
  return !content.trim();
}

function isGraphType(content) {
  const firstLine = content.trim().split("\n")[0];
  return /^graph\s+(LR|TD|TB|RL|BT)/i.test(firstLine);
}

function replaceGraphWithFlowchart(content) {
  return content.replace(/^graph\s+(LR|TD|TB|RL|BT)/im, "flowchart $1");
}

function hasBalancedBrackets(content) {
  let depth = 0;
  for (const ch of content) {
    if (ch === "{") depth++;
    if (ch === "}") depth--;
    if (depth < 0) return false;
  }
  return depth === 0;
}

function hasBalancedParens(content) {
  let depth = 0;
  for (const ch of content) {
    if (ch === "(") depth++;
    if (ch === ")") depth--;
    if (depth < 0) return false;
  }
  return depth === 0;
}

function processFile(filePath) {
  let content = fs.readFileSync(filePath, "utf-8");
  let modified = false;
  const issues = [];

  let match;
  const matches = [];

  while ((match = MERMAID_REGEX.exec(content)) !== null) {
    matches.push({
      fullMatch: match[0],
      code: match[1],
      index: match.index,
    });
  }

  if (matches.length === 0) return { issues: [], modified: false };

  for (const m of matches) {
    if (isBlankMermaid(m.code)) {
      issues.push("Empty mermaid block");
      continue;
    }

    let newCode = m.code;

    // Replace graph with flowchart
    if (isGraphType(newCode)) {
      const fixed = replaceGraphWithFlowchart(newCode);
      if (fixed !== newCode) {
        newCode = fixed;
        issues.push("Replaced 'graph' with 'flowchart'");
      }
    }

    // Fix common issues
    for (const rule of COMMON_ISSUES) {
      if (rule.pattern.test(newCode)) {
        if (typeof rule.fix === "function") {
          newCode = newCode.replace(rule.pattern, rule.fix);
        } else {
          newCode = newCode.replace(rule.pattern, rule.fix);
        }
        issues.push(rule.desc);
        rule.pattern.lastIndex = 0;
      }
    }

    if (newCode !== m.code) {
      const newBlock = "```mermaid\n" + newCode + "```";
      content =
        content.substring(0, m.index) +
        newBlock +
        content.substring(m.index + m.fullMatch.length);
      modified = true;
      MERMAID_REGEX.lastIndex = 0;
    }
  }

  if (modified) {
    fs.writeFileSync(filePath, content, "utf-8");
  }

  return { issues, modified };
}

function walkDir(dir) {
  const results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === "node_modules" || entry.name === ".vitepress")
      continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkDir(fullPath));
    } else if (entry.name.endsWith(".md")) {
      results.push(fullPath);
    }
  }
  return results;
}

// Main
const mdFiles = walkDir(WIKI_ROOT);
let totalIssues = 0;
let totalFixed = 0;

for (const file of mdFiles) {
  const { issues, modified } = processFile(file);
  if (issues.length > 0) {
    const rel = path.relative(WIKI_ROOT, file);
    console.log(`\n  ${rel}:`);
    for (const issue of issues) {
      console.log(`    - ${issue}`);
      totalIssues++;
    }
    if (modified) {
      console.log("    -> Auto-fixed");
      totalFixed++;
    }
  }
}

console.log(`\nMermaid validation complete.`);
console.log(`  Files scanned: ${mdFiles.length}`);
console.log(`  Issues found: ${totalIssues}`);
console.log(`  Files auto-fixed: ${totalFixed}`);
