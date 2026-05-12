import fs from "node:fs";
import path from "node:path";

const WIKI_ROOT = path.resolve(import.meta.dirname, "..");

const pages = [
  { title: "Overview", path: "guide/index.md" },
  { title: "Getting Started", path: "guide/getting-started.md" },
  { title: "Installation", path: "guide/installation.md" },
  { title: "Architecture Overview", path: "guide/architecture.md" },
  { title: "Core Module", path: "guide/core.md" },
  { title: "Configuration Management", path: "guide/config-service.md" },
  { title: "Consistency Layer", path: "guide/config-consistency.md" },
  { title: "Service Registry", path: "guide/service-registry.md" },
  { title: "Service Discovery", path: "guide/service-discovery.md" },
  { title: "Load Balancers", path: "guide/load-balancers.md" },
  { title: "Service Topology", path: "guide/service-topology.md" },
  { title: "Spring Cloud Config Starter", path: "guide/spring-cloud-config.md" },
  { title: "Spring Cloud Discovery Starter", path: "guide/spring-cloud-discovery.md" },
  { title: "REST API Server", path: "guide/rest-api.md" },
  { title: "Security & RBAC", path: "guide/security-rbac.md" },
  { title: "Dashboard", path: "guide/dashboard.md" },
  { title: "Docker Deployment", path: "guide/deployment-docker.md" },
  { title: "Kubernetes Deployment", path: "guide/deployment-kubernetes.md" },
  { title: "Standalone Deployment", path: "guide/deployment-standalone.md" },
  { title: "Performance Benchmarks", path: "guide/performance.md" },
];

function stripFrontmatter(content) {
  const match = content.match(/^---\n[\s\S]*?\n---\n?/);
  if (match) {
    return content.substring(match[0].length);
  }
  return content;
}

let output = "";

for (const page of pages) {
  const filePath = path.join(WIKI_ROOT, page.path);
  if (!fs.existsSync(filePath)) {
    console.warn(`  Skipping missing: ${page.path}`);
    continue;
  }
  const raw = fs.readFileSync(filePath, "utf-8");
  const content = stripFrontmatter(raw);

  output += `<doc title="${page.title}" path="${page.path}">\n`;
  output += content.trim();
  output += `\n</doc>\n\n`;
}

const outPath = path.join(WIKI_ROOT, "llms-full.txt");
fs.writeFileSync(outPath, output.trim(), "utf-8");
console.log(`Generated ${outPath} (${(output.length / 1024).toFixed(1)} KB)`);
