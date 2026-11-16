// commit の前の hook が実行する検査と script のテストを、ステージしたファイルから選ぶ。
//
// CI は全てを実行するので、選び漏れは CI で見つかる。対応の表と考え方は、CI の検査の script の「commit の前の hook」。
//
// 検査（scripts/ci/ の検査のフォルダの直下）とテストは、次のどれかに当たれば実行する。
//   - ステージしたファイルが、検査・テストが import・require でたどれるファイル（自分を含む）である
//   - ステージしたファイルのパスか名前が、たどれるファイルの中に書いてある（読む設定や除外の一覧の名前）
//   - ステージしたファイルが、CHECK_INPUTS・TEST_INPUTS のパスの正規表現に当たる（ファイルの一覧から読む検査）
// テストは、名前が同じ検査（X.test.mjs と X.mjs）を実行するときも実行する（リポジトリのファイルを読むテストがあるため）。
// RUN_ALL に当たるファイル（scripts/ の下の全てと依存）をステージしたときは、全てを実行する。
// scripts/ の下は、他の script を走査するテストがあり、どの検査に関わるかを表で保つのが難しいため。
import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'

/** 全てを実行するファイル：scripts/ の下の全てと、依存 */
const RUN_ALL = [/^scripts\//, /^package\.json$/, /^pnpm-lock\.yaml$/]

/** 検査のフォルダ（scripts/ci/ の下。ここの直下の .mjs のうち、テスト以外が検査）。lib と report など引数で動くものは含めない */
export const CHECK_GROUPS = ['docs', 'spec', 'ja', 'java', 'config', 'frontend']

/** 検査のテストのファイル（検査と同じ名前の X.test.mjs） */
const CHECK_TEST = new RegExp(`^scripts/ci/(?:${CHECK_GROUPS.join('|')})/[^/]+\\.test\\.mjs$`)

/** 常に実行する検査の印（リポジトリのほぼ全てのファイルを読む検査） */
const ALWAYS = 'always'

/**
 * 検査ごとの、読むファイルのパスの正規表現。
 *
 * import と、ソースの中のパス・名前から分からないもの（Git のファイルの一覧から選ぶもの）を書く。
 *
 * 表にない検査は、常に実行する（選び漏れで検査が実行されないことを避けるため）。
 */
export const CHECK_INPUTS = {
  'ac-check': [/^docs\/03-機能仕様\//, /^docs\/08-spec\//, /^backend\/ecsite\/src\/(?:test|integrationTest)\//, /^frontend\//, /^e2e\//],
  'ci-docs': [],
  'compose-ports': [],
  'doc-refs': ALWAYS,
  'docs-folders': [/^docs\//],
  'frontend-rules': [/^frontend\//, /^e2e\//],
  // 用語集は名前で選ぶ。コードの名前を探す場所（SEARCH_ROOTS）のうち scripts/ は全てを実行する形に含まれる
  'glossary-ids': [/^(?:backend|frontend|api|docker|e2e)\//],
  'gradle-plugins': [/^backend\/ecsite\/.*\.gradle\.kts$/],
  'ja-text': ALWAYS,
  'java-rules': [/^backend\/ecsite\/src\//],
  'local-ports': [],
  'message-comments': [/^frontend\//],
  migrations: [/^backend\/ecsite\/src\/main\/resources\/db\/migration\//],
  'note-numbers': [/\.md$/, /^\.github\/.*\.ya?ml$/],
  progress: [/^docs\/08-spec\//],
  risk: ALWAYS,
  'role-permissions': [/^api\/openapi\//],
  'rule-sections': [/^docs\/05-開発規則\//],
  'runtime-versions': [/^\.github\//],
  'spec-numbers': ALWAYS,
  'spec-structure': [/^docs\/03-機能仕様\//, /^docs\/08-spec\//],
}

/** テストごとの、読むファイルのパスの正規表現（import と名前の検査で分からないもの）。scripts/ の外の文書を読むテスト */
const TEST_INPUTS = {
  '.claude/skills/review/checklist.test.cjs': [/^docs\/05-開発規則\//],
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
  // 先にパスで当たるかを見て、当たらないときだけ内容をつなぐ
  if (staged.some((file) => closure.has(file) || patterns.some((pattern) => pattern.test(file)))) return true
  const sources = [...closure.values()].join('\n')
  return staged.some((file) => sources.includes(file) || sources.includes(path.posix.basename(file)))
}

/**
 * ステージしたファイルから、実行する検査とテストを選ぶ。
 *
 * @param {object} input
 * @param {string[]} input.staged ステージしたファイル（削除と改名の元を含む。リポジトリのルートからの相対パス）
 * @param {string[]} input.checks 検査の名前（検査のフォルダの直下の、拡張子を除いた名前）
 * @param {Record<string, string>} input.files 検査の名前から、検査のファイル（リポジトリのルートからの相対パス）
 * @param {string[]} input.tests テストのファイル（リポジトリのルートからの相対パス）
 * @param {(file: string) => string | null} input.read ステージした内容のファイルを読む。ないときは null
 * @returns {{ all: boolean, checks: string[], tests: string[] }} 全てを実行するか、実行する検査、実行するテスト（与えた順）
 */
export function selectChecks({ staged, checks, files, tests, read }) {
  if (staged.some((file) => RUN_ALL.some((pattern) => pattern.test(file)))) {
    return { all: true, checks, tests }
  }
  const always = checks.filter((name) => (CHECK_INPUTS[name] ?? ALWAYS) === ALWAYS)
  const touched = checks.filter((name) => !always.includes(name) && touches(staged, importClosure(files[name], read), CHECK_INPUTS[name]))
  // 常に実行する検査のテストは、検査の作りに関わるファイルを変えたとき（import でたどれるとき）だけ実行する
  const subject = (test) => (CHECK_TEST.test(test) ? path.posix.basename(test, '.test.mjs') : null)
  const selectedTests = tests.filter((test) => touched.includes(subject(test)) || touches(staged, importClosure(test, read), TEST_INPUTS[test] ?? []))
  return { all: false, checks: checks.filter((name) => always.includes(name) || touched.includes(name)), tests: selectedTests }
}

/**
 * 取り出した先から、検査の名前とテストのファイルを集める。
 *
 * @param {string} root 取り出した先のディレクトリ
 * @returns {{ checks: string[], files: Record<string, string>, tests: string[] }} 検査の名前（名前の順）、検査の名前から検査のファイル、テストのファイル（名前の順）
 */
export function listChecksAndTests(root) {
  const list = (dir) => {
    try {
      return readdirSync(path.join(root, dir), { recursive: true }).map((name) => `${dir}/${String(name).split(path.sep).join('/')}`)
    } catch {
      return []
    }
  }
  // 検査は、検査のフォルダ（CHECK_GROUPS）の直下の、テスト以外の .mjs
  const files = {}
  for (const group of CHECK_GROUPS) {
    for (const file of list(`scripts/ci/${group}`)) {
      if (/^scripts\/ci\/[^/]+\/[^/]+\.mjs$/.test(file) && !file.endsWith('.test.mjs')) {
        files[path.posix.basename(file, '.mjs')] = file
      }
    }
  }
  const tests = [...list('scripts/ci'), ...list('scripts/dev')].filter((file) => file.endsWith('.test.mjs'))
  tests.push(...list('.claude/skills').filter((file) => file.endsWith('.test.cjs')))
  return { checks: Object.keys(files).sort(), files, tests: tests.sort() }
}

/**
 * 取り出した先のファイルを読む関数を作る。
 *
 * 読んだ内容（ないことを含む）は覚えておく。共有の lib は、検査とテストごとに何度もたどられるため。
 *
 * @param {string} root 取り出した先のディレクトリ
 * @returns {(file: string) => string | null} 相対パスのファイルの内容。ないときは null
 */
export function fileReader(root) {
  const cache = new Map()
  return (file) => {
    if (!cache.has(file)) {
      let text = null
      try {
        text = readFileSync(path.join(root, file), 'utf8')
      } catch {
        // ないファイルは null として覚える
      }
      cache.set(file, text)
    }
    return cache.get(file)
  }
}
