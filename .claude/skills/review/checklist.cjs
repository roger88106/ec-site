// 書き方の規則の各文書の「確認方法：」の行から、確認方法に「レビュー」を含むものを抜き出し、レビューのチェックリストにする
// 使い方：node .claude/skills/review/checklist.cjs（リポジトリのルートで。結果を標準出力に Markdown で出す）
// 対象は、docs/05-開発規則/ の文書。確認方法の行は、直前の見出し（## と ###）の節のものとして扱う。
// コードブロックの中は読まない。
// 形 1：「確認方法：<方法>」の 1 行（節の規則が 1 つ、または確認方法が 1 つ）。
// 形 2：「確認方法：」だけの行の後に、「- <規則の短い名前>：<方法>」の箇条を並べる（確認方法の違う規則が複数ある節）。
const fs = require('fs'), { execSync } = require('child_process')

const TARGET_DIR = 'docs/05-開発規則/'

/**
 * チェックリストの対象の文書か。
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} docs/05-開発規則/ の文書なら true
 */
function isTargetFile(file) {
  return file.startsWith(TARGET_DIR) && file.endsWith('.md')
}

/**
 * 文書の「確認方法：」の行を読む。
 * 箇条は、「確認方法：」だけの行の後の、空行を挟んで続く「- 」の行（最初の「：」で名前と方法に分ける）。
 * @param {string} text 文書の全文
 * @param {string} file 題名がないときに使う文書のパス
 * @returns {{title: string, entries: {section: string, name: string, check: string}[]} | null} 確認方法の行がなければ null。形 1 の name は空文字
 */
function readCheckMethods(text, file) {
  const lines = text.split(/\r?\n/)
  let title = null
  let section = ''
  let inCode = false
  let inBullets = false
  const entries = []
  for (const raw of lines) {
    const line = raw.trimEnd()
    if (line.startsWith('```')) {
      inCode = !inCode
      continue
    }
    if (inCode) continue
    if (title === null && line.startsWith('# ')) title = line.replace(/^# /, '')
    const heading = /^#{2,3}\s+(.*)$/.exec(line)
    if (heading) {
      section = heading[1].trim()
      inBullets = false
      continue
    }
    const method = /^確認方法：(.*)$/.exec(line)
    if (method) {
      const check = method[1].trim()
      inBullets = check === ''
      if (check) entries.push({ section, name: '', check })
      continue
    }
    if (inBullets) {
      const bullet = /^- (.*?)：(.*)$/.exec(line)
      if (bullet) entries.push({ section, name: bullet[1].trim(), check: bullet[2].trim() })
      else if (line.trim() !== '') inBullets = false
    }
  }
  if (!entries.length) return null
  return { title: title || file, entries }
}

/**
 * 確認方法に「レビュー」を含むものを、チェックリストの項目（Markdown）にする。
 * @param {{section: string, name: string, check: string}[]} entries 確認方法の行
 * @returns {string[]} チェック項目の行
 */
function reviewItems(entries) {
  return entries
    .filter((e) => e.check.includes('レビュー'))
    .map((e) => `- [ ] 「${e.section}」${e.name}：${e.check}`)
}

/** git で管理している docs/05-開発規則/ の文書からチェックリストを作って標準出力に出す。項目がなければ異常終了する。 */
function main() {
  // 日本語を含むパスを、引用符と 8 進数の形にせずに読む
  const files = execSync('git -c core.quotepath=false ls-files "docs/*.md"', { encoding: 'utf8' })
    .split('\n').filter(Boolean).filter(isTargetFile)
  const out = []
  for (const file of files) {
    const doc = readCheckMethods(fs.readFileSync(file, 'utf8'), file)
    if (!doc) continue
    const items = reviewItems(doc.entries)
    if (items.length) out.push(`## ${doc.title}（${file}）`, '', ...items, '')
  }
  if (!out.length) {
    console.error('docs/05-開発規則/ に確認方法が「レビュー」の行がない')
    process.exit(1)
  }
  console.log(out.join('\n'))
}

module.exports = { isTargetFile, readCheckMethods, reviewItems }

if (require.main === module) main()
