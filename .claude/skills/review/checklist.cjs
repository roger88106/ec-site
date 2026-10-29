// 書き方の規則の各文書の「規則の一覧」から、確認方法に「レビュー」を含む行を抜き出し、レビューのチェックリストにする
// 使い方：node .claude/skills/review/checklist.cjs（リポジトリのルートで。結果を標準出力に Markdown で出す）
// 対象は、docs/ の下で「## 規則の一覧」の節を持つ文書（置き場所が変わっても探せるように、名前では決めない）
const fs = require('fs'), { execSync } = require('child_process')

/**
 * 文書の「規則の一覧」の表を読む。
 * 表の見出しの行（節）と「-」だけの区切りの行、列が 3 つ未満の行は読み飛ばし、次の「## 」の見出しで終える。
 * @param {string} text 文書の全文
 * @param {string} file 題名がないときに使う文書のパス
 * @returns {{title: string, rows: {section: string, rule: string, check: string}[]} | null} 「規則の一覧」の節がなければ null
 */
function readRuleTable(text, file) {
  const lines = text.split(/\r?\n/)
  const start = lines.findIndex((l) => l.trim() === '## 規則の一覧')
  if (start < 0) return null
  const title = (lines.find((l) => l.startsWith('# ')) || file).replace(/^# /, '')
  const rows = []
  for (const line of lines.slice(start + 1)) {
    if (line.startsWith('## ')) break
    const cells = line.split('|').slice(1, -1).map((c) => c.trim())
    if (cells.length < 3 || /^-+$/.test(cells[0]) || cells[0] === '節') continue
    const [section, rule, check] = cells
    rows.push({ section, rule, check })
  }
  return { title, rows }
}

/**
 * 確認方法に「レビュー」を含む行を、チェックリストの項目（Markdown）にする。
 * @param {{section: string, rule: string, check: string}[]} rows 表の行
 * @returns {string[]} チェック項目の行
 */
function reviewItems(rows) {
  return rows
    .filter((r) => r.check.includes('レビュー'))
    .map((r) => `- [ ] 第 ${r.section} 節：${r.rule}（${r.check}）`)
}

/** git で管理している docs/ の文書からチェックリストを作って標準出力に出す。項目がなければ異常終了する。 */
function main() {
  // 日本語を含むパスを、引用符と 8 進数の形にせずに読む
  const files = execSync('git -c core.quotepath=false ls-files "docs/*.md"', { encoding: 'utf8' }).split('\n').filter(Boolean)
  const out = []
  for (const file of files) {
    const table = readRuleTable(fs.readFileSync(file, 'utf8'), file)
    if (!table) continue
    const items = reviewItems(table.rows)
    if (items.length) out.push(`## ${table.title}（${file}）`, '', ...items, '')
  }
  if (!out.length) {
    console.error('「規則の一覧」に確認方法が「レビュー」の行がない')
    process.exit(1)
  }
  console.log(out.join('\n'))
}

module.exports = { readRuleTable, reviewItems }

if (require.main === module) main()
