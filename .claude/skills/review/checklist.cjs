// 書き方の規則の各文書の「規則の一覧」から、確認方法に「人手」を含む行を抜き出し、レビューのチェックリストにする
// 使い方：node .claude/skills/review/checklist.cjs（リポジトリのルートで。結果を標準出力に Markdown で出す）
// 対象は、docs/ の下で「## 規則の一覧」の節を持つ文書（置き場所が変わっても探せるように、名前では決めない）
const fs = require('fs'), { execSync } = require('child_process')

const files = execSync('git ls-files "docs/*.md"', { encoding: 'utf8' }).split('\n').filter(Boolean)
const out = []
for (const file of files) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
  const start = lines.findIndex((l) => l.trim() === '## 規則の一覧')
  if (start < 0) continue
  const title = (lines.find((l) => l.startsWith('# ')) || file).replace(/^# /, '')
  const rows = []
  for (const line of lines.slice(start + 1)) {
    if (line.startsWith('## ')) break
    const cells = line.split('|').slice(1, -1).map((c) => c.trim())
    if (cells.length < 3 || /^-+$/.test(cells[0]) || cells[0] === '節') continue
    const [section, rule, check] = cells
    if (check.includes('人手')) rows.push(`- [ ] 第 ${section} 節：${rule}（${check}）`)
  }
  if (rows.length) out.push(`## ${title}（${file}）`, '', ...rows, '')
}
if (!out.length) {
  console.error('「規則の一覧」に「人手」の行がない')
  process.exit(1)
}
console.log(out.join('\n'))
