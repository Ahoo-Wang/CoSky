#!/usr/bin/env node

/**
 * Validates every ```mermaid block in the wiki with the real Mermaid parser.
 *
 * Mermaid renders in the browser, so a successful `vitepress build` says nothing about
 * diagram syntax. This script extracts all diagrams, serves them with the installed
 * mermaid bundle on a local port, and lets a headless Chrome call `mermaid.parse()` on each.
 * Exits non-zero and lists `file:line` for every diagram that fails to parse.
 *
 * Chrome: $CHROME_BIN, else google-chrome / chromium on PATH, else the macOS app.
 */

import { execFile, execFileSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const wikiRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const mermaidBundle = join(wikiRoot, 'node_modules/mermaid/dist/mermaid.min.js')

function markdownFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    if (name === 'node_modules' || name.startsWith('.')) return []
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return markdownFiles(path)
    return name.endsWith('.md') ? [path] : []
  })
}

function extractDiagrams() {
  const diagrams = []
  for (const file of markdownFiles(wikiRoot)) {
    const lines = readFileSync(file, 'utf8').split('\n')
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].trim() !== '```mermaid') continue
      const start = i + 1
      let end = start
      while (end < lines.length && lines[end].trim() !== '```') end++
      diagrams.push({ file: relative(wikiRoot, file), line: start + 1, code: lines.slice(start, end).join('\n') })
      i = end
    }
  }
  return diagrams
}

function findChrome() {
  const candidates = [
    process.env.CHROME_BIN,
    'google-chrome',
    'google-chrome-stable',
    'chromium',
    'chromium-browser',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  ].filter(Boolean)
  for (const candidate of candidates) {
    if (candidate.startsWith('/')) {
      if (existsSync(candidate)) return candidate
      continue
    }
    try {
      return execFileSync('which', [candidate], { encoding: 'utf8' }).trim()
    } catch {
      // not on PATH
    }
  }
  throw new Error('Chrome not found; set CHROME_BIN')
}

const page = `<!doctype html><html><body><pre id="result">pending</pre>
<script src="/mermaid.min.js"></script>
<script>
  mermaid.initialize({ startOnLoad: false })
  fetch('/diagrams.json').then((r) => r.json()).then(async (diagrams) => {
    const failures = []
    for (const d of diagrams) {
      try { await mermaid.parse(d.code) } catch (e) {
        failures.push(d.file + ':' + d.line + '  ' + String(e && e.message || e).split('\\n').slice(0, 3).join(' | '))
      }
    }
    document.getElementById('result').textContent = JSON.stringify({ total: diagrams.length, failures })
  })
</script></body></html>`

const diagrams = extractDiagrams()
const server = createServer((req, res) => {
  if (req.url === '/mermaid.min.js') {
    res.writeHead(200, { 'content-type': 'text/javascript' }).end(readFileSync(mermaidBundle))
  } else if (req.url === '/diagrams.json') {
    res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify(diagrams))
  } else {
    res.writeHead(200, { 'content-type': 'text/html' }).end(page)
  }
})

// Chrome 必须异步启动：同步调用会阻塞事件循环，本进程的 HTTP 服务无法响应而死锁
server.listen(0, '127.0.0.1', async () => {
  const { port } = server.address()
  let exitCode = 1
  try {
    const { stdout: dom } = await promisify(execFile)(
      findChrome(),
      ['--headless=new', '--disable-gpu', '--no-sandbox', '--virtual-time-budget=120000', '--dump-dom', `http://127.0.0.1:${port}/`],
      { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, timeout: 180_000 },
    )
    const json = dom.match(/<pre id="result">([\s\S]*?)<\/pre>/)?.[1]?.replace(/&quot;/g, '"').replace(/&gt;/g, '>').replace(/&lt;/g, '<').replace(/&amp;/g, '&')
    if (!json || json === 'pending') throw new Error('Mermaid validation did not finish in the browser')
    const { total, failures } = JSON.parse(json)
    if (failures.length === 0) {
      console.log(`✓ ${total} mermaid diagrams parsed`)
      exitCode = 0
    } else {
      console.error(`✗ ${failures.length} of ${total} mermaid diagrams failed to parse:`)
      for (const failure of failures) console.error(`  ${failure}`)
    }
  } catch (error) {
    console.error(String(error.message || error))
  } finally {
    server.close()
    process.exit(exitCode)
  }
})
