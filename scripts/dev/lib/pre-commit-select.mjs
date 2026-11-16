// commit の前の hook が実行する検査と script のテストを、ステージしたファイルから選ぶ。
// CI は今までどおり全てを実行するので、選び漏れは CI で見つかる。対応の表と考え方は、CI の検査の script の「commit の前の hook」。
//
// 検査（scripts/ci/ の直下）とテストは、次のどれかに当たれば実行する。
//   - ステージしたファイルが、検査・テストが import・require でたどれるファイル（自分を含む）である
//   - ステージしたファイルのパスか名前が、たどれるファイルの中に書いてある（読む設定や除外の一覧の名前）
//   - ステージしたファイルが、CHECK_INPUTS・TEST_INPUTS のパスの正規表現に当たる（ファイルの一覧から読む検査）
// テストは、名前が同じ検査（X.test.mjs と X.mjs）を実行するときも実行する（リポジトリのファイルを読むテストがあるため）。
// RUN_ALL に当たるファイル（検査をまとめて実行する仕組みと依存）をステージしたときは、全てを実行する。
import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'

/** 全てを実行するファイル：まとめて実行する script、結果の判定、hook の選び方、依存 */
export const RUN_ALL = [/^scripts\/ci\/check-all\.sh$/, /^scripts\/ci\/lib\/check-result\.mjs$/, /^scripts\/dev\/lib\/pre-commit[\w-]*\.mjs$/, /^package\.json$/, /^pnpm-lock\.yaml$/]

/** 常に実行する検査の印（リポジトリのほぼ全てのファイルを読む検査） */
const ALWAYS = 'always'

/**
 * 検査ごとの、読むファイルのパスの正規表現。import と、ソースの中のパス・名前から分からないもの（Git のファイルの一覧から選ぶもの）を書く。<br />
 * 表にない検査は、常に実行する（選び漏れで検査が実行されないことを避けるため）。
 */
export const CHECK_INPUTS = {
  'ac-check': [/^docs\/03-機能仕様\//, /^docs\/08-spec\//, /^backend\/ecsite\/src\/(?:test|integrationTest)\//, /^frontend\//, /^e2e\//],
  'ci-docs': [/^scripts\/ci\//],
  'compose-ports': [],
  'dependabot-actions': [/(?:^|\/)action\.ya?ml$/],
  'doc-refs': ALWAYS,
  'doc-shape': [/\.md$/],
  'docs-folders': [/^docs\//],
  'frontend-rules': [/^frontend\//, /^e2e\//],
  'glossary-ids': ALWAYS,
  'gradle-plugins': [/^backend\/ecsite\/.*\.gradle\.kts$/],
  'image-versions': [/^backend\/ecsite\/src\/integrationTest\//],
  'integration-shards': [],
  'ja-text': ALWAYS,
  'java-rules': [/^backend\/ecsite\/src\//],
  'license-policy': [],
  'local-ports': [],
  'main-entry': [/^scripts\//, /^frontend\/shared\/scripts\//],
  'message-comments': [/^frontend\//],
  'message-numbers': [],
  migrations: [/^backend\/ecsite\/src\/main\/resources\/db\/migration\//],
  'note-numbers': [/\.md$/, /^\.github\/.*\.ya?ml$/],
  progress: [/^docs\/08-spec\//],
  risk: ALWAYS,
  'role-permissions': [/^api\/openapi\//],
  'rule-sections': [/^docs\/05-開発規則\//],
  'runtime-versions': [/^\.github\//, /(?:^|\/)package\.json$/],
  'spec-numbers': ALWAYS,
  'spec-structure': [/^docs\/03-機能仕様\//, /^docs\/08-spec\//],
}

/** テストごとの、読むファイルのパスの正規表現（import と名前の検査で分からないもの）。script の一覧を走査するテストと、文書を読むテスト */
export const TEST_INPUTS = {
  'scripts/ci/lib/exclusions.test.mjs': [/^scripts\/ci\/.*\.mjs$/],
  'scripts/ci/lib/unreadable-line.test.mjs': [/^scripts\/ci\/.*\.mjs$/],
  '.claude/skills/review/checklist.test.cjs': [/^\.claude\/skills\/review\//, /^docs\/05-開発規則\//],
}

/** import・require の先（相対パスだけ） */
const IMPORT = /(?:\bfrom\s*|\bimport\s*\(?\s*|\brequire\s*\(\s*)['"](\.{1,2}\/[^'"]+)['"]/g

/**
 * ファイルが import・require でたどれるファイル（自分を含む）を集める。
 *
 * @param {string} file リポジトリのルートからの相対パス（/ 区切り）
 * @param {(file: string) => string | null} read ファイルの内容を返す。ないときは null
 * @returns {Map<string, string>} たどれるファイルの相対パスから内容
 */
export function importClosure(file, read) {
  const found = new Map()
  const pending = [file]
  while (pending.length > 0) {
    const current = pending.pop()
    if (found.has(current)) continue
    const text = read(current)
    if (text === null) continue
    found.set(current, text)
    for (const [, target] of text.matchAll(IMPORT)) {
      pending.push(path.posix.normalize(path.posix.join(path.posix.dirname(current), target)))
    }
  }
  return found
}

/**
 * ステージしたファイルのどれかが、たどれるファイルに当たるか、たどれるファイルの中に書いてあるか、正規表現に当たるか。
 *
 * @param {string[]} staged ステージしたファイル
 * @param {Map<string, string>} closure たどれるファイルと内容
 * @param {RegExp[]} patterns 読むファイルのパスの正規表現
 * @returns {boolean} 当たれば true
 */
function touches(staged, closure, patterns) {
  const sources = [...closure.values()].join('\n')
  return staged.some(
    (file) => closure.has(file) || sources.includes(file) || sources.includes(path.posix.basename(file)) || patterns.some((pattern) => pattern.test(file)),
  )
}

/**
 * ステージしたファイルから、実行する検査とテストを選ぶ。
 *
 * @param {object} input
 * @param {string[]} input.staged ステージしたファイル（削除と改名の元を含む。リポジトリのルートからの相対パス）
 * @param {string[]} input.checks 検査の名前（scripts/ci/ の直下の、拡張子を除いた名前）
 * @param {string[]} input.tests テストのファイル（リポジトリのルートからの相対パス）
 * @param {(file: string) => string | null} input.read ステージした内容のファイルを読む。ないときは null
 * @returns {{ all: boolean, checks: string[], tests: string[] }} 全てを実行するか、実行する検査、実行するテスト（与えた順）
 */
export function selectChecks({ staged, checks, tests, read }) {
  if (staged.some((file) => RUN_ALL.some((pattern) => pattern.test(file)))) {
    return { all: true, checks, tests }
  }
  const always = checks.filter((name) => (CHECK_INPUTS[name] ?? ALWAYS) === ALWAYS)
  const touched = checks.filter((name) => !always.includes(name) && touches(staged, importClosure(`scripts/ci/${name}.mjs`, read), CHECK_INPUTS[name]))
  // 常に実行する検査のテストは、検査の作りに関わるファイルを変えたとき（import でたどれるとき）だけ実行する
  const subject = (test) => (/^scripts\/ci\/[^/]+\.test\.mjs$/.test(test) ? path.posix.basename(test, '.test.mjs') : null)
  const selectedTests = tests.filter((test) => touched.includes(subject(test)) || touches(staged, importClosure(test, read), TEST_INPUTS[test] ?? []))
  return { all: false, checks: checks.filter((name) => always.includes(name) || touched.includes(name)), tests: selectedTests }
}

/**
 * 取り出した先から、検査の名前とテストのファイルを集める。
 *
 * @param {string} root 取り出した先のディレクトリ
 * @returns {{ checks: string[], tests: string[] }} 検査の名前と、テストのファイル（名前の順）
 */
export function listChecksAndTests(root) {
  const list = (dir) => {
    try {
      return readdirSync(path.join(root, dir), { recursive: true }).map((name) => `${dir}/${String(name).split(path.sep).join('/')}`)
    } catch {
      return []
    }
  }
  const checks = list('scripts/ci')
    .filter((file) => /^scripts\/ci\/[^/]+\.mjs$/.test(file) && !file.endsWith('.test.mjs'))
    .map((file) => path.posix.basename(file, '.mjs'))
  const tests = [...list('scripts/ci'), ...list('scripts/dev')].filter((file) => file.endsWith('.test.mjs'))
  tests.push(...list('.claude/skills').filter((file) => file.endsWith('.test.cjs')))
  return { checks: checks.sort(), tests: tests.sort() }
}

/**
 * 取り出した先のファイルを読む関数を作る。
 *
 * @param {string} root 取り出した先のディレクトリ
 * @returns {(file: string) => string | null} 相対パスのファイルの内容。ないときは null
 */
export function fileReader(root) {
  return (file) => {
    try {
      return readFileSync(path.join(root, file), 'utf8')
    } catch {
      return null
    }
  }
}
