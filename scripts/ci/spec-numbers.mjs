// 本番のコードと、テストと設定のファイルと script の、それぞれのコメントに、spec・plan・tasks の番号を書いた所がないことを確認する（コメントの書き方の「何を書くか」）。
// 規則は言葉で書き、spec との対応はテストの名前（@DisplayName、test の名前）に書く。
// 番号は、MVP の AC-n とタスクの番号（T の後に 3 桁の spec の番号）、1→10 の AC-s-n とタスクの番号（T の後に 0 で埋めない spec の番号）。s は spec の番号、n は連番。記号と数字の間に区切りのない形（AC の直後に数字）と、全角で書いた形（NFKC で正規化して照合する）も見つける。
// - 本番のコード：行の全体を見る
// - バックエンドのテストとテストの補助（src/test、src/integrationTest、src/testFixtures の .java）：コメントだけを見る（@DisplayName の文字列は見ない）
// - 設定のファイル（ルートの .ts・.mjs・.cjs、各パッケージ直下の *.config.ts・*.config.mjs、backend/ecsite/ の *.gradle.kts）：コメントだけを見る
// - フロントのテストとテストの補助（*.test.ts(x)、src/test/）と、E2E（e2e/ の下の全ての TypeScript。設定と e2e/scripts/ を含む）：コメントだけを見る（テスト名と test.step の名前などの文字列、テンプレート文字列、正規表現のリテラルは見ない）
// - scripts/ の下の script（.mjs・.cjs・.sh と、拡張子のない hook の script。テストを含む）：コメントだけを見る（テストの入力の例などの文字列は見ない）
// 生成物、適用済みのマイグレーションは見ない。
// 失敗したとき：番号を削除し、規則を言葉で書く。spec との対応はテストの名前に書く。
// 除外：なし。
// 使い方：node scripts/ci/spec-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { codeKind, commentText } from './lib/comment-text.mjs'
import { isMain } from './lib/is-main.mjs'
import { keepJavaComments, keepTsComments } from './lib/strip-comments.mjs'
import { E2E_FILE, FRONTEND_TEST, FRONTEND_TEST_SUPPORT } from './frontend-rules.mjs'

/**
 * 本番のコードの置き場所。バックエンドも見るので、画面の本番のソースの定義（lib/screen-sources.mjs）は使わない。
 * 画面は、定義が除く共有の ui（src/ui/）と shadcn/ui の部品（components/ui/）も、番号を書かないものとして見る
 */
const SOURCE_DIRS = ['backend/ecsite/src/main/', 'frontend/admin/src/', 'frontend/store/src/', 'frontend/shared/src/']

/** 本番のコードでないもの（テスト、テストの補助、生成物、適用済みのマイグレーション） */
const EXCLUDED = [/\.test\.tsx?$/, /\/src\/test\//, /\/generated\//, /\/db\/migration\//]

/** spec の番号（MVP の AC-n、1→10 の spec の AC-s-n など）、タスクの番号（T の後に spec の番号・段階・連番を - で区切ったもの、古い形式の T と 3〜4 桁の数字）、spec・plan の番号（spec・plan の後の数字）。# の直後の語は 16 進の色（#DC2626 など）なので番号にしない */
const SPEC_NUMBER = /(?<!#)(?:\b(?:AC|DC|BR|NFR|US|DS|DN)-?\d+(?:-\d+)?\b|\bT\d+-\d{2}-\d{2}\b|\bT\d{3,4}\b|\b(?:spec|plan) ?\d+\b(?!\.))/g

/**
 * spec・plan・tasks の番号を書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, number: string, text: string }[]} 見つけた行、番号（正規化した後の形）、行の文字（正規化の前）
 */
export function findSpecNumbers(text) {
  const hits = []
  text.split('\n').forEach((line, index) => {
    // 全角の英字・数字・ハイフンで書いた番号も見つけるため、照合の前に NFKC で正規化する。報告する行の文字は正規化の前のものにする
    for (const match of line.normalize('NFKC').matchAll(SPEC_NUMBER)) {
      hits.push({ line: index + 1, number: match[0], text: line.trim() })
    }
  })
  return hits
}

/**
 * 調べるファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 本番のコードなら true
 */
export function isProductionFile(file) {
  return SOURCE_DIRS.some((dir) => file.startsWith(dir)) && !EXCLUDED.some((pattern) => pattern.test(file))
}

/**
 * コメントだけを調べる TypeScript のファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} フロントのテスト・テストの補助、または E2E（e2e/ の下の全ての TypeScript）なら true
 */
export function isCommentOnlyTsFile(file) {
  return FRONTEND_TEST.test(file) || FRONTEND_TEST_SUPPORT.test(file) || E2E_FILE.test(file)
}

/** バックエンドのテストとテストの補助の置き場所 */
const BACKEND_TEST_DIRS = ['backend/ecsite/src/test/', 'backend/ecsite/src/integrationTest/', 'backend/ecsite/src/testFixtures/']

/**
 * コメントだけを調べるファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} バックエンドのテスト・テストの補助の .java なら true
 */
export function isBackendTestFile(file) {
  return BACKEND_TEST_DIRS.some((dir) => file.startsWith(dir)) && file.endsWith('.java')
}

/**
 * 設定のファイルの置き場所。コメントだけを見る。
 * - リポジトリのルートの .ts・.mjs・.cjs（knip.ts、.pnpmfile.cjs など）
 * - 各パッケージ（frontend/admin、frontend/store、frontend/shared、e2e）の直下の *.config.ts・*.config.mjs
 * - バックエンド（backend/ecsite/）の Gradle の設定のファイル（*.gradle.kts）
 */
const CONFIG_FILES = [/^[^/]+\.(?:ts|mjs|cjs)$/,/^(?:frontend\/(?:admin|store|shared)|e2e)\/[^/]+\.config\.(?:ts|mjs)$/, /^backend\/ecsite\/(?:.+\/)?[^/]+\.gradle\.kts$/]

/**
 * コメントだけを調べる設定のファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 設定のファイルなら true
 */
export function isConfigFile(file) {
  return CONFIG_FILES.some((pattern) => pattern.test(file))
}

/** scripts/ の下の script（.mjs・.cjs・.sh と、拡張子のない hook の script）。コメントだけを見る */
const SCRIPT_FILES = [/^scripts\/.+\.(?:mjs|cjs|sh)$/, /^scripts\/dev\/hooks\/[^/.]+$/]

/**
 * コメントだけを調べる scripts/ の下の script かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} scripts/ の下の .mjs・.cjs・.sh、または拡張子のない hook の script なら true
 */
export function isScriptFile(file) {
  return SCRIPT_FILES.some((pattern) => pattern.test(file))
}

/**
 * コメントだけを残す。バックエンドのテストと Gradle の設定のファイル（Kotlin のコメントは Java と同じ書き方）は Java の読み方、
 * フロントのテスト・テストの補助、E2E、TypeScript・mjs・cjs の設定のファイルと script は TypeScript の読み方、
 * シェルの script（.sh と hook の script）は # のコメントの読み方で読む。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース（行番号は元と同じ）
 */
export function commentsOf(file, text) {
  if (/\.(?:tsx?|mjs|cjs)$/.test(file)) return keepTsComments(text)
  if (codeKind(file) === 'sh') return commentText(text, 'sh')
  return keepJavaComments(text)
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => isProductionFile(file) || isBackendTestFile(file) || isCommentOnlyTsFile(file) || isConfigFile(file) || isScriptFile(file))
  exitIfNoFiles(files, '本番のコードとテストと設定のファイルと script')
  let count = 0
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findSpecNumbers(isProductionFile(file) ? text : commentsOf(file, text))) {
      console.log(`${file}:${hit.line} ${hit.number}（${hit.text}）`)
      count += 1
    }
  }
  console.log(`spec の番号：${count}`)
  process.exit(count ? 1 : 0)
}
