// docs/ の下の各ファイルが、constitution.md か、許可するフォルダーの下にあることを確認する。
// 許可するフォルダー名の一覧（ALLOWED_DOCS_FOLDERS）はこの script の 1 か所だけに置く。
// フォルダーを追加・変更するときは、この一覧と constitution の「リポジトリ構成」の文書の地図を一緒に修正する（skill `docs` の「置き場所」）。
// 最初の要素が docs と大文字・小文字だけ違うもの（Docs、DOCS）も失敗にする。Windows と macOS では、既存の docs/ の中に取り出され、取り違えたフォルダーができるため。
// 理由：「開発」「記録」は、繁体字の「開發」「記錄」と取り違えやすく、取り違えたフォルダーができても気づきにくいため。
// 使い方：node scripts/ci/docs-folders.mjs（リポジトリのルートで）
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** docs/ の直下に置いてよいフォルダー名 */
export const ALLOWED_DOCS_FOLDERS = ['01-概要', '02-設計', '03-機能仕様', '04-決定記録', '05-開発規則', '06-手順書', '07-記録', '08-spec']

/** docs/ の直下に置いてよいファイル */
const ALLOWED_DOCS_FILE = 'constitution.md'

/**
 * 最初の要素が docs と大文字・小文字だけ違うファイルか。
 *
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} Docs/、DOCS/ などの下ならば true
 */
export function isWrongCaseDocs(file) {
  const first = file.split('/')[0]
  return file.includes('/') && first !== 'docs' && first.toLowerCase() === 'docs'
}

/**
 * 許可する場所にないファイルを探す。
 *
 * @param {string[]} files リポジトリのルートからのパス（/ 区切り）
 * @returns {string[]} 大文字・小文字だけ違う docs の下のファイルと、docs/ の下で許可する場所にないファイル
 */
export function findDocsFolderProblems(files) {
  return files.filter((file) => {
    if (isWrongCaseDocs(file)) return true
    if (!file.startsWith('docs/')) return false
    const rest = file.slice('docs/'.length)
    const slash = rest.indexOf('/')
    // 直下のファイルは constitution.md だけ。フォルダーの下のファイルは、フォルダー名が一覧にあるものだけ
    return slash === -1 ? rest !== ALLOWED_DOCS_FILE : !ALLOWED_DOCS_FOLDERS.includes(rest.slice(0, slash))
  })
}

if (isMain(import.meta.url)) {
  const files = listGitFiles()
  exitIfNoFiles(
    files.filter((file) => file.split('/')[0].toLowerCase() === 'docs' && file.includes('/')),
    'docs/ の下のファイル',
  )
  const problems = findDocsFolderProblems(files)
  for (const file of problems) {
    if (isWrongCaseDocs(file)) {
      console.log(`${file} 最初の要素が docs と大文字・小文字が違う（docs/ にそろえる）`)
    } else {
      console.log(`${file} 許可する一覧にない（許可：${ALLOWED_DOCS_FILE}、${ALLOWED_DOCS_FOLDERS.join('、')}）`)
    }
  }
  console.log(`docs/ の許可されない場所：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
