// 文書を移して切れた Markdown のリンクを、移した先に直す。切れていないリンクは変えない
// 使い方：node .claude/skills/docs/fix-links.cjs 旧パス=新パス ...（リポジトリのルートからのパス。フォルダーも可）
// 例：node .claude/skills/docs/fix-links.cjs docs/style/backend.md=docs/backend/coding.md docs/modules=docs/backend/modules
// 移す（git mv）のを先に済ませてから流す
const fs = require('fs'), path = require('path'), { execSync } = require('child_process')
const MAP = process.argv.slice(2).map((a) => a.split('=').map((p) => p.replace(/\/$/, '')))
if (!MAP.length || MAP.some((m) => m.length !== 2)) { console.error('旧パス=新パス を 1 つ以上渡す'); process.exit(2) }
const swap = (p, from, to) => {
  for (const pair of MAP) {
    const [a, b] = [pair[from], pair[to]]
    if (p === a) return b
    if (p.startsWith(a + '/')) return b + p.slice(a.length)
  }
  return p
}
const files = execSync('git ls-files -co --exclude-standard "*.md"', { encoding: 'utf8' }).split('\n').filter((f) => f && fs.existsSync(f))
let changed = 0
for (const file of files) {
  const dir = path.posix.dirname(file)
  const oldDir = path.posix.dirname(swap(file, 1, 0)) // 移した文書は、移す前の場所からリンクを解く
  const src = fs.readFileSync(file, 'utf8')
  const out = src.replace(/\]\(([^)\s]+)\)/g, (m, link) => {
    if (/^(https?:|mailto:|#)/.test(link)) return m
    const [rel, anchor] = link.split('#')
    if (fs.existsSync(path.posix.join(dir, rel))) return m
    const target = swap(path.posix.normalize(path.posix.join(oldDir, rel)).replace(/\/$/, ''), 0, 1)
    if (!fs.existsSync(target)) { console.log('直せない', file, link); return m }
    changed++
    return `](${path.posix.relative(dir, target) || '.'}${anchor !== undefined ? '#' + anchor : ''})`
  })
  if (out !== src) fs.writeFileSync(file, out)
}
console.log(`直したリンク：${changed}`)
