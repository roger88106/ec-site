// 用語集（docs/design/glossary.md）の識別子の列の名前が、コードか DB のマイグレーションにあることを確認する（skill docs 第 2 節「事実」）。
// 調べる場所は backend/、frontend/、api/、docker/、e2e/、scripts/ の Git のファイル（生成物を除く）。
// 名前の形の例（XxxRepository など、Xxx を含むもの）は調べない。コードにない語でよいものは glossary-ids-exclusions.json に名前と理由を書く。
// 使い方：node scripts/ci/glossary-ids.mjs（リポジトリのルートで）
import { execSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** 用語集 */
const GLOSSARY = 'docs/design/glossary.md'

/** 調べる場所 */
const SEARCH_ROOTS = ['backend', 'frontend', 'api', 'docker', 'e2e', 'scripts']

/** 調べないファイル（生成物、画像など） */
const SKIPPED = /\/(?:node_modules|build|dist|\.next|generated)\/|\.(?:png|jpe?g|webp|gif|ico|svg|woff2?|jar|lock)$|pnpm-lock\.yaml$/

/**
 * 用語集の表の識別子の列から、バッククォートの中の名前を読む。
 *
 * @param {string} markdown 用語集
 * @returns {string[]} 識別子（書いた順、重ねない）。名前の形の例（Xxx を含む）は除く
 */
export function parseGlossaryIds(markdown) {
  const ids = []
  for (const line of markdown.split(/\r?\n/)) {
    const cells = line.split('|').slice(1, -1)
    if (cells.length < 3) continue
    let previous = ''
    for (const match of cells[1].matchAll(/`([^`]+)`/g)) {
      let id = match[1].trim()
      // 省略形（`cancelled_by_id`・`_name`）は、前の名前の最後の部分を置き換える
      if (id.startsWith('_') && previous.includes('_')) id = previous.slice(0, previous.lastIndexOf('_')) + id
      previous = id
      // 名前の形の例（Xxx、<モジュール>、*）は調べない
      if (/Xxx|[<*]/.test(id) || ids.includes(id)) continue
      ids.push(id)
    }
  }
  return ids
}

/**
 * kebab-case の名前（設定の名前）を camelCase にする。
 *
 * @param {string} id 名前
 * @returns {string} camelCase の名前
 */
function camelOf(id) {
  return id.replace(/-([a-z])/g, (_, char) => char.toUpperCase())
}

/**
 * コードにない識別子を返す。
 *
 * @param {string[]} ids 識別子
 * @param {Set<string>} tokens コードの語（英数字とアンダースコアの並び）
 * @param {string} content コードの中身とファイルのパス（つないだもの。パスや . を含む名前を探す）
 * @returns {string[]} コードにない識別子（書いた順）
 */
export function findMissing(ids, tokens, content) {
  return ids.filter((id) => {
    if (/^\w+$/.test(id)) return !tokens.has(id)
    if (/^[a-z0-9]+(-[a-z0-9]+)+$/.test(id)) return !tokens.has(camelOf(id)) && !content.includes(id)
    return !content.includes(id)
  })
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const exclusionsFile = path.join(path.dirname(fileURLToPath(import.meta.url)), 'glossary-ids-exclusions.json')
  const exclusions = JSON.parse(readFileSync(exclusionsFile, 'utf8'))
  const files = execSync(`git ls-files ${SEARCH_ROOTS.join(' ')}`, { encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && !SKIPPED.test(file) && existsSync(file))
  // パスの形の識別子（docs/constitution.md など）は、リポジトリのすべてのファイルのパスにも当たる
  const allPaths = execSync('git ls-files', { encoding: 'utf8' })
  const contents = [allPaths, ...files.map((file) => readFileSync(file, 'utf8'))]
  const tokens = new Set(contents.flatMap((text) => text.match(/\w+/g) ?? []))
  const ids = parseGlossaryIds(readFileSync(GLOSSARY, 'utf8'))
  const missing = findMissing(ids, tokens, contents.join('\n')).filter((id) => !(id in exclusions))
  for (const id of missing) console.log(`${GLOSSARY}：識別子 \`${id}\` がコードにない`)
  for (const id of Object.keys(exclusions).filter((id) => !ids.includes(id))) {
    console.log(`glossary-ids-exclusions.json：\`${id}\` が用語集にない（除外の一覧から消す）`)
  }
  const count = missing.length + Object.keys(exclusions).filter((id) => !ids.includes(id)).length
  console.log(`用語集の識別子の不一致：${count}`)
  process.exit(count ? 1 : 0)
}
