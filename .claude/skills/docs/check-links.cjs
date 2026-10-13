// Markdown のリンクの先（ファイルと #見出し）があるかを確かめる。見出しの slug は GitHub と同じ作り方
// 使い方：node .claude/skills/docs/check-links.cjs [パスの前方一致]
const fs = require('fs'), path = require('path'), { execSync } = require('child_process')
const files = execSync('git ls-files -co --exclude-standard "*.md"', { encoding: 'utf8' }).split('\n').filter((f) => f && fs.existsSync(f))
const slug = (h) => h.trim().toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/ /g, '-')
const cache = {}
const anchors = (f) => (cache[f] ??= (() => {
  const seen = {}, out = new Set()
  let inCode = false
  for (const line of fs.readFileSync(f, 'utf8').split('\n')) {
    if (line.startsWith('```')) inCode = !inCode
    const m = !inCode && line.match(/^#{1,6}\s+(.*)$/)
    if (!m) continue
    let s = slug(m[1].replace(/`/g, ''))
    if (seen[s] !== undefined) { seen[s]++; s = `${s}-${seen[s]}` } else seen[s] = 0
    out.add(s)
  }
  return out
})())
const only = process.argv[2]
let bad = 0
for (const f of files) {
  if (only && !f.startsWith(only)) continue
  for (const [, link] of fs.readFileSync(f, 'utf8').matchAll(/\]\(([^)\s]+)\)/g)) {
    if (/^(https?:|mailto:)/.test(link)) continue
    const [rel, anchor] = link.split('#')
    const target = rel ? path.posix.join(path.posix.dirname(f), rel) : f
    if (!fs.existsSync(target)) { console.log('ファイルがない', f, link); bad++; continue }
    if (anchor && target.endsWith('.md') && !anchors(target).has(decodeURIComponent(anchor))) { console.log('見出しがない', f, link); bad++ }
  }
}
console.log(`切れたリンク：${bad}`)
process.exit(bad ? 1 : 0)
