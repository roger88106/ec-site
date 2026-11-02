// 用語集（docs/03-機能仕様/glossary.md）と開発の用語集（docs/05-開発規則/glossary.md）の識別子の列の名前が、コードか DB のマイグレーションにあるかを確認する。
// 文書の書き方の「言い回しと論理」にある「事実」の検査である。
// 調べる場所は、次のディレクトリの Git のファイル（生成物を除く）か、ファイルのパス。
//   - backend/、frontend/、api/
//   - docker/、e2e/、scripts/
// scripts/ci/ のテスト、testdata/、ja-text.json、除外の一覧は根拠にしない。kebab-case の設定の名前は camelCase の語でもよい。
// 名前の形の例（XxxRepository など、Xxx、<…>、* を含むもの）は調べない。
// 表の形：見出しの行（区切りの行の直前）に「用語」か「識別子」がある表は、見出しの行が「用語、識別子、…」の順でなければ失敗にする。
//   識別子を 1 つも取り出せない用語集も失敗にする（列を入れ替えると、読まずに通るため）
// 失敗したとき：用語集の識別子を、今のコードの名前に修正する。表の形は、列を 用語、識別子、… の順にする。
// 除外：glossary-ids-exclusions.json に {キー: 理由}。キーは識別子。コードにない語でよいものを書く。用語集にない識別子の除外は失敗にする（コードに偶然の一致があっても除外は残す）
// 使い方：node scripts/ci/glossary-ids.mjs（リポジトリのルートで）
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { readFileSync } from 'node:fs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { isMain } from './lib/is-main.mjs'

/** 調べる用語集（業務の用語集と、開発の用語集） */
export const GLOSSARIES = ['docs/03-機能仕様/glossary.md', 'docs/05-開発規則/glossary.md']

/** 調べる場所 */
const SEARCH_ROOTS = ['backend', 'frontend', 'api', 'docker', 'e2e', 'scripts']

/**
 * 検査の script 自身のファイル（テスト、テストのデータ、除外の一覧、使わない語の一覧）。
 * 検査の script のテストと例の文字を根拠にすると、コードにない識別子が通るため、調べない。
 * scripts/ci/ の本番の script（.mjs）と scripts/dev/ は、調べる。
 */
const SELF_FILES = /^scripts\/ci\/(?:.*\.test\.mjs$|(?:.*\/)?testdata\/|[\w-]+-exclusions\.json$|ja-text\.json$)/

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
 * 用語集の表の形を調べる。parseGlossaryIds は 3 列以上の行の 2 列目だけを読み、見出しを見ないので、列を入れ替えると識別子が 0 件になり、検査が通ってしまう。<br />
 * 見出しの行（区切りの行の直前）に「用語」か「識別子」があるときは、1 列目が「用語」、2 列目が「識別子」でなければならない。
 * 他の見出しの表（配送業者の一覧など）は見ない。識別子を 1 つも取り出せない用語集も問題にする。
 *
 * @param {string} markdown 用語集
 * @returns {string[]} 問題（行を付ける）
 */
export function checkGlossary(markdown) {
  const problems = []
  const lines = markdown.split(/\r?\n/)
  lines.forEach((line, index) => {
    if (!/^\|(?:\s*:?-+:?\s*\|)+$/.test((lines[index + 1] ?? '').trim()) || !line.trim().startsWith('|')) return
    const cells = line.split('|').slice(1, -1).map((cell) => cell.trim())
    if (!cells.includes('識別子') && cells[0] !== '用語') return
    if (cells[0] !== '用語' || cells[1] !== '識別子') problems.push(`${index + 1} 行：表の見出しの行の 2 列目が「識別子」でない（列は 用語、識別子、… の順にする）`)
  })
  if (parseGlossaryIds(markdown).length === 0) problems.push('識別子を 1 つも取り出せない（表の 2 列目に、バッククォートで囲んだ識別子を書く）')
  return problems
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
  const exclusions = readExclusions(exclusionsPath('glossary-ids-exclusions.json'))
  const files = listGitFiles(SEARCH_ROOTS).filter((file) => isSearchedFile(file))
  exitIfNoFiles(files, '用語の ID を探すファイル')
  // パスの形の識別子（docs/constitution.md など）は、リポジトリのすべてのファイルのパスにも当たる
  const allPaths = listGitFiles().join('\n')
  const contents = [allPaths, ...files.map((file) => readFileSync(file, 'utf8'))]
  const tokens = new Set(contents.flatMap((text) => text.match(/\w+/g) ?? []))
  const content = contents.join('\n')
  const ids = []
  const missing = []
  const shapeProblems = []
  for (const glossary of GLOSSARIES) {
    const markdown = readFileSync(glossary, 'utf8')
    for (const problem of checkGlossary(markdown)) {
      shapeProblems.push(problem)
      console.log(`${glossary}：${problem}`)
    }
    const glossaryIds = parseGlossaryIds(markdown)
    ids.push(...glossaryIds)
    const absent = findMissing(glossaryIds, tokens, content)
    for (const id of absent.filter((name) => !(name in exclusions))) {
      missing.push(id)
      console.log(`${glossary}：識別子 \`${id}\` がコードにない`)
    }
  }
  const problems = checkExclusions('glossary-ids-exclusions.json', exclusions, (id) => isExclusionNeeded(id, ids))
  for (const problem of problems) console.log(problem)
  const count = missing.length + problems.length + shapeProblems.length
  console.log(`用語集の識別子の不一致：${count}`)
  process.exit(count ? 1 : 0)
}
