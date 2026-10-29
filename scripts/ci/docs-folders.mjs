// docs/ の下の各ファイルが、constitution.md か、許可するフォルダーの下にあることを確認する。
// 許可するフォルダー名の一覧（ALLOWED_DOCS_FOLDERS）はこの script の 1 か所だけに置く。
// フォルダーを追加・変更するときは、この一覧と constitution の文書の地図（第 3 節）を一緒に修正する（skill docs 第 1 節）。
// 理由：「開発」「記録」は、繁体字の「開發」「記錄」と取り違えやすく、取り違えたフォルダーができても気づきにくいため。
// 使い方：node scripts/ci/docs-folders.mjs（リポジトリのルートで）
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** docs/ の直下に置いてよいフォルダー名 */
export const ALLOWED_DOCS_FOLDERS = ['01-概要', '02-設計', '03-機能仕様', '04-決定記録', '05-開発規則', '06-手順書', '07-記録', '08-spec']

/** docs/ の直下に置いてよいファイル */
const ALLOWED_DOCS_FILE = 'constitution.md'

/**
 * 許可する場所にないファイルを探す。
 *
 * @param {string[]} files リポジトリのルートからのパス（/ 区切り）
 * @returns {string[]} docs/ の下にあり、パスの最初の要素が constitution.md でも許可するフォルダーでもないファイル
 */
export function findDocsFolderProblems(files) {
  return files.filter((file) => {
    if (!file.startsWith('docs/')) return false
    const rest = file.slice('docs/'.length)
    const slash = rest.indexOf('/')
    // 直下のファイルは constitution.md だけ。フォルダーの下のファイルは、フォルダー名が一覧にあるものだけ
    return slash === -1 ? rest !== ALLOWED_DOCS_FILE : !ALLOWED_DOCS_FOLDERS.includes(rest.slice(0, slash))
  })
}

if (isMain(import.meta.url)) {
  const files = listGitFiles(['docs'])
  exitIfNoFiles(files, 'docs/ の下のファイル')
  const problems = findDocsFolderProblems(files)
  for (const file of problems) {
    console.log(`${file} 許可する一覧にない（許可：${ALLOWED_DOCS_FILE}、${ALLOWED_DOCS_FOLDERS.join('、')}）`)
  }
  console.log(`docs/ の許可されない場所：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
