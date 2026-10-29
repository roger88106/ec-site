// 語の一括の置き換えの後に、新しい語の語幹の後に続くかなを数える（第 5 節）。少ない続き方に、元の語の活用の語尾が残った崩れがある
// 使い方：node .claude/skills/docs/suffixes.cjs 語幹（例：node .claude/skills/docs/suffixes.cjs 確認）
// 文書（*.md）を対象にし、アーカイブを除く。少ない順に、続き方、数、最初の場所を出す
const fs = require('node:fs')
const { execSync } = require('node:child_process')

const stem = process.argv[2]
if (!stem) {
  console.error('語幹を 1 つ渡す')
  process.exit(2)
}
const escaped = stem.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const pattern = new RegExp(`${escaped}(\\p{Script=Hiragana}{1,2})`, 'gu')
// 日本語を含むパスを、引用符と 8 進数の形にせずに読む
const files = execSync('git -c core.quotepath=false ls-files -co --exclude-standard "*.md"', { encoding: 'utf8' })
  .split('\n')
  .filter((file) => file && !file.startsWith('docs/08-spec/archive/') && fs.existsSync(file))
const counts = new Map()
for (const file of files) {
  fs.readFileSync(file, 'utf8').split(/\r?\n/).forEach((line, index) => {
    for (const match of line.matchAll(pattern)) {
      const entry = counts.get(match[1]) ?? { count: 0, first: `${file}:${index + 1}` }
      entry.count += 1
      counts.set(match[1], entry)
    }
  })
}
for (const [suffix, { count, first }] of [...counts].sort((a, b) => a[1].count - b[1].count)) {
  console.log(`${count}\t${stem}${suffix}\t${first}`)
}
