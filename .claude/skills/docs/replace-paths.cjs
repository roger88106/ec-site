// 文書を移した後に、リンクでないパスの文字（コード・skill・CI・script・文書の本文）を、移した先に置き換える
// リンクは fix-links.cjs で直す。こちらはリポジトリのルートからのパスの文字だけを見る
// 使い方：node .claude/skills/docs/replace-paths.cjs 旧パス=新パス ...（リポジトリのルートで。フォルダーも可）
// 例：node .claude/skills/docs/replace-paths.cjs docs/08-spec/001-catalog-and-inventory=docs/08-spec/archive/mvp/001-catalog-and-inventory
// 次は置き換えない（変えると壊れるもの、当時の記録）：
//   - 適用済みのマイグレーションと seed（Flyway の checksum が変わり、既存の DB で起動に失敗する）
//   - テストのデータ（*.test.* のファイル。旧パスを例として持つ）
//   - アーカイブした spec の本文（当時の記録。文書の書き方の「spec と plan の書き方」）
const fs = require('node:fs')
const { execSync } = require('node:child_process')

/** 置き換えないファイル */
const EXCLUDED = [/^backend\/ecsite\/src\/main\/resources\/db\//, /\.test\.[^/]+$/, /^docs\/08-spec\/archive\//]

/**
 * 置き換えないファイルか。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 置き換えないなら true
 */
function isExcluded(file) {
  return EXCLUDED.some((pattern) => pattern.test(file))
}

/**
 * 引数（旧パス=新パス）を、旧パスと新パスの組にする。
 *
 * @param {string[]} args 引数
 * @returns {[string, string][]} 旧パスと新パスの組（末尾の / を除く）
 */
function parseMap(args) {
  const pairs = args.map((arg) => arg.split('=').map((p) => p.replace(/\/$/, '')))
  if (!pairs.length || pairs.some((pair) => pair.length !== 2 || !pair[0] || !pair[1])) {
    throw new Error('旧パス=新パス を 1 つ以上渡す')
  }
  return pairs
}

/**
 * 文の中の旧パスを新パスに置き換える。全ての組を 1 回で置き換え、置き換えた後の文字を再度置き換えない。
 * パスの一部（001-catalog-x の 001-catalog、x/docs/... の docs/...）は置き換えない。
 *
 * @param {string} text 文
 * @param {[string, string][]} map 旧パスと新パスの組
 * @returns {string} 置き換えた文
 */
function replacePaths(text, map) {
  const targets = new Map(map)
  const alternatives = [...targets.keys()]
    .sort((a, b) => b.length - a.length)
    .map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  const pattern = new RegExp(`(?<![\\w./-])(${alternatives.join('|')})(?![\\w-]|\\.\\w)`, 'g')
  return text.replace(pattern, (match) => targets.get(match))
}

if (require.main === module) {
  let map
  try {
    map = parseMap(process.argv.slice(2))
  } catch (error) {
    console.error(error.message)
    process.exit(2)
  }
  // 日本語を含むパスを、引用符と 8 進数の形にせずに読む
  const files = execSync('git -c core.quotepath=false ls-files -co --exclude-standard', { encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && !isExcluded(file) && fs.existsSync(file))
  let changed = 0
  for (const file of files) {
    const src = fs.readFileSync(file, 'utf8')
    if (src.includes('\u0000')) continue
    const out = replacePaths(src, map)
    if (out === src) continue
    fs.writeFileSync(file, out)
    console.log(file)
    changed += 1
  }
  console.log(`置き換えたファイル：${changed}`)
}

module.exports = { isExcluded, parseMap, replacePaths }
