import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const WIKI_ROOT = path.resolve(import.meta.dirname, "..");
const TMP_DIR = path.join(WIKI_ROOT, ".tmp-mermaid");

const MERMAID_REGEX = /```mermaid\s*\n([\s\S]*?)```/g;

if (!fs.existsSync(TMP_DIR)) {
  fs.mkdirSync(TMP_DIR, { recursive: true });
}

const mmdcPath = path.join(WIKI_ROOT, "node_modules", ".bin", "mmdc");

// Use system Chrome on macOS
const chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

function walkDir(dir) {
  const results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === "node_modules" || entry.name === ".vitepress" || entry.name === ".tmp-mermaid")
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

function extractMermaidBlocks(filePath) {
  const content = fs.readFileSync(filePath, "utf-8");
  const blocks = [];
  let match;
  while ((match = MERMAID_REGEX.exec(content)) !== null) {
    blocks.push(match[1].trim());
  }
  return blocks;
}

const mdFiles = walkDir(path.join(WIKI_ROOT, "guide"))
  .concat(walkDir(path.join(WIKI_ROOT, "zh", "guide")));

let totalBlocks = 0;
let totalErrors = 0;
let totalFixed = 0;
const errorDetails = [];

for (const file of mdFiles) {
  const blocks = extractMermaidBlocks(file);
  const rel = path.relative(WIKI_ROOT, file);

  for (let i = 0; i < blocks.length; i++) {
    totalBlocks++;
    const tmpFile = path.join(TMP_DIR, `block_${totalBlocks}.mmd`);
    let code = blocks[i];

    // Pre-fix common issues before validation
    code = code.replace(/<br\/>/g, "<br>");

    fs.writeFileSync(tmpFile, code, "utf-8");

    try {
      const outFile = path.join(TMP_DIR, `block_${totalBlocks}.svg`);
      const args = ["-i", tmpFile, "-o", outFile];
      if (fs.existsSync(chromePath)) {
        args.push("-p", path.join(WIKI_ROOT, "scripts", "puppeteer-config.json"));
      }
      execFileSync(mmdcPath, args, {
        timeout: 30000,
        encoding: "utf-8",
        stdio: ["pipe", "pipe", "pipe"],
        env: {
          ...process.env,
          PUPPETEER_EXECUTABLE_PATH: fs.existsSync(chromePath) ? chromePath : undefined,
        },
      });
    } catch (err) {
      totalErrors++;
      const stderr = (err.stderr || err.message || "").toString();
      const firstLine = stderr.split("\n").find(l => l.trim() && !l.includes("npm warn")) || "(empty error)";
      errorDetails.push({
        file: rel,
        blockIndex: i + 1,
        error: firstLine.trim(),
        code: code.split("\n").slice(0, 3).join(" | "),
      });
    }

    try { fs.unlinkSync(tmpFile); } catch {}
  }
}

// Cleanup
try { fs.rmSync(TMP_DIR, { recursive: true }); } catch {}

console.log(`\nMermaid CLI Validation Results:`);
console.log(`  Files scanned: ${mdFiles.length}`);
console.log(`  Total blocks: ${totalBlocks}`);
console.log(`  Errors: ${totalErrors}`);
console.log(`  Valid: ${totalBlocks - totalErrors}`);

if (errorDetails.length > 0) {
  console.log(`\n${"=".repeat(60)}`);
  console.log(`ERRORS (${errorDetails.length}):`);
  console.log(`${"=".repeat(60)}`);

  // Group errors by file
  const byFile = {};
  for (const d of errorDetails) {
    if (!byFile[d.file]) byFile[d.file] = [];
    byFile[d.file].push(d);
  }

  for (const [file, errs] of Object.entries(byFile)) {
    console.log(`\n❌ ${file} (${errs.length} error(s)):`);
    for (const e of errs) {
      console.log(`   Block #${e.blockIndex}: ${e.error.substring(0, 120)}`);
    }
  }
  process.exit(1);
} else {
  console.log(`\n✅ All Mermaid diagrams are valid.`);
  process.exit(0);
}
