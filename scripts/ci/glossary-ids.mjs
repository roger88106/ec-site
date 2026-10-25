// 用語集（docs/design/glossary.md）の識別子の列の名前が、コードか DB のマイグレーションにあることを確認する（skill docs 第 2 節「事実」）。
// 調べる場所は backend/、frontend/、api/、docker/、e2e/、scripts/ の Git のファイル（生成物を除く）。
// 名前の形の例（XxxRepository など、Xxx を含むもの）は調べない。コードにない語でよいものは glossary-ids-exclusions.json に名前と理由を書く。
// 使い方：node scripts/ci/glossary-ids.mjs（リポジトリのルートで）
import { execFileSync } from 'node:child_process'
import { exitIfNoFiles, splitNulList } from './lib/git-files.mjs'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { checkExclusions, readExclusions } from './lib/exclusions.mjs'
import { isMain } from './lib/is-main.mjs'

/** 用語集 */
const GLOSSARY = 'docs/design/glossary.md'

/** 調べる場所 */
const SEARCH_ROOTS = ['backend', 'frontend', 'api', 'docker', 'e2e', 'scripts']

/**
 * 検査の script 自身のファイル（テスト、テストのデータ、除外の一覧、使わない語の一覧）。
 * 検査の script のテストと例の文字を根拠にすると、コードにない識別子が通るため、調べない。
 * scripts/ci/ の本番の script（.mjs）と scripts/dev/ は、調べる。
 */
const SELF_FILES = /^scripts\/ci\/(?:.*\.test\.mjs$|(?:.*\/)?testdata\/|[\w-]+-exclusions\.json$|doc-terms\.json$)/

/** 調べないファイル（生成物、画像など） */
const SKIPPED = /\/(?:node_modules|build|dist|\.next|generated)\/|\.(?:png|jpe?g|webp|gif|ico|svg|woff2?|jar|lock)$|pnpm-lock\.yaml$/

/**
 * 識別子の根拠として調べるファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isSearchedFile(file) {
  return !SKIPPED.test(file) && !SELF_FILES.test(file)
}

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

/**
 * 除外が今も必要かを返す。
 * 不要とするのは、用語集になくなった名前だけ。コードに見つかったことでは不要としない
 * （ブランチの名前や tasks の記号は、型の paths[P]、テストのデータ、sourceSets.main などに偶然一致し、機械で見分けられないため）。
 *
 * @param {string} id 除外の名前
 * @param {string[]} ids 用語集の識別子
 * @returns {boolean} 用語集にあれば true
 */
export function isExclusionNeeded(id, ids) {
  return ids.includes(id)
}

if (isMain(import.meta.url)) {
  const exclusions = readExclusions(path.join(path.dirname(fileURLToPath(import.meta.url)), 'glossary-ids-exclusions.json'))
  const files = splitNulList(execFileSync('git', ['ls-files', '-z', '--', ...SEARCH_ROOTS], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }))
    .filter((file) => isSearchedFile(file) && existsSync(file))
  exitIfNoFiles(files, '用語の ID を探すファイル')
  // パスの形の識別子（docs/constitution.md など）は、リポジトリのすべてのファイルのパスにも当たる
  const allPaths = splitNulList(execFileSync('git', ['ls-files', '-z'], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })).join('\n')
  const contents = [allPaths, ...files.map((file) => readFileSync(file, 'utf8'))]
  const tokens = new Set(contents.flatMap((text) => text.match(/\w+/g) ?? []))
  const ids = parseGlossaryIds(readFileSync(GLOSSARY, 'utf8'))
  const absent = findMissing(ids, tokens, contents.join('\n'))
  const missing = absent.filter((id) => !(id in exclusions))
  for (const id of missing) console.log(`${GLOSSARY}：識別子 \`${id}\` がコードにない`)
  const problems = checkExclusions('glossary-ids-exclusions.json', exclusions, (id) => isExclusionNeeded(id, ids))
  for (const problem of problems) console.log(problem)
  const count = missing.length + problems.length
  console.log(`用語集の識別子の不一致：${count}`)
  process.exit(count ? 1 : 0)
}
