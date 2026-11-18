// Git の対象のファイル（追跡しているファイルと、追跡していない無視されないファイル）の一覧を返す。
//
// 追跡していないファイルも含める理由：検査は commit の前に実行し、新しく作ったファイル（テスト、文書）はその時点では git add していないことが多い。
// 追跡しているファイルだけにすると、新しいファイルが検査されずに手元で通り、CI で初めて失敗する。
//
// 手元と CI の違い：CI の作業ツリーには追跡していないファイルがない。手元にだけある commit しないファイルで、手元だけ失敗することがある（.gitignore に書くか /.local/ に置いて避ける）。
// commit し忘れたファイルを指す参照は、手元で通り CI で失敗することがある。
//
// Git の一覧を読む検査は、対象が 0 件のとき失敗にする（exitIfNoFiles。文は emptyListReason）。
import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'

/** git の出力の最大の大きさ（バイト） */
const MAX_BUFFER = 64 * 1024 * 1024

/**
 * `git ls-files -z` の出力を、パスの配列に分ける（ASCII でない文字を含むパスも、引用符なしで扱える）。
 *
 * @param {string} output NUL 区切りの出力
 * @returns {string[]} 空でないパス
 */
export function splitNulList(output) {
  return output.split('\0').filter(Boolean)
}

/**
 * Git の対象のファイルの一覧を返す。作業ツリーから消えたファイルは含めない。
 *
 * @param {string[]} [pathspecs] 探すパス（ディレクトリなど）。なければリポジトリの全体
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）。名前の順
 */
export function listGitFiles(pathspecs = [], cwd = process.cwd()) {
  return listFiles(['-co'], pathspecs, cwd)
}

/**
 * 追跡していない、無視されないファイルの一覧を返す。git diff に出ない新しいファイルである。
 *
 * @param {string[]} [pathspecs] 探すパス。なければリポジトリの全体
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）。名前の順
 */
export function listUntrackedFiles(pathspecs = [], cwd = process.cwd()) {
  return listFiles(['-o'], pathspecs, cwd)
}

/**
 * git ls-files の一覧を、作業ツリーにあるものだけにして返す。
 *
 * @param {string[]} kinds 一覧の種類の引数（-co、-o）
 * @param {string[]} pathspecs 探すパス
 * @param {string} cwd 実行する場所
 * @returns {string[]} 相対パス。名前の順
 */
function listFiles(kinds, pathspecs, cwd) {
  const output = execFileSync('git', ['ls-files', '-z', ...kinds, '--exclude-standard', '--', ...pathspecs], { cwd, encoding: 'utf8', maxBuffer: MAX_BUFFER })
  return [...new Set(splitNulList(output))].filter((file) => existsSync(path.join(cwd, file))).sort()
}

/**
 * 追跡しているファイルの一覧を返す。作業ツリーから消えたファイルも含める。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）。重複なし
 */
export function listTrackedFiles(cwd = process.cwd()) {
  return [...new Set(splitNulList(runGit(['ls-files', '-z', '-c'], cwd)))]
}

/**
 * 追跡しているファイルのうち、リポジトリの .gitignore（各フォルダーのものを含む）だけで除外の対象になるものを返す。
 *
 * `--exclude-standard` を使わず `--exclude-per-directory=.gitignore` にする理由：.git/info/exclude と全体の core.excludesFile を読まず、
 * 手元の除外の設定で結果が変わらないようにし、手元と CI で同じ結果にするため。
 * 大文字と小文字は区別する（`core.ignorecase=false`）。Windows の clone は区別しない設定になり、Linux の CI と結果が変わるため。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）。重複なし
 */
export function listIgnoredTrackedFiles(cwd = process.cwd()) {
  return [...new Set(splitNulList(runGit(['ls-files', '-z', '-i', '-c', '--exclude-per-directory=.gitignore'], cwd)))]
}

/**
 * 大文字と小文字を区別する設定で git を実行して、標準出力を返す。
 *
 * @param {string[]} args git の引数
 * @param {string} cwd 実行する場所
 * @returns {string} 標準出力
 */
function runGit(args, cwd) {
  return execFileSync('git', ['-c', 'core.ignorecase=false', ...args], { cwd, encoding: 'utf8', maxBuffer: MAX_BUFFER })
}

/**
 * 対象のファイルが 0 件のときの失敗の理由を返す。置き場所の変更や除外の条件の広がりで、検査が何も見ずに成功するのを防ぐ。
 *
 * @param {string[]} files 検査の対象のファイル
 * @param {string} label 対象の呼び名（失敗の文に使う）
 * @returns {string | null} 0 件なら失敗の理由、あれば null
 */
export function emptyListReason(files, label) {
  return files.length === 0 ? `${label}が 1 件も見つからない（検査の対象の条件か置き場所が変わっていないか確認する）` : null
}

/**
 * 対象のファイルが 0 件なら、理由を出して終了コード 2（環境の誤り）で終わる。
 *
 * @param {string[]} files 検査の対象のファイル
 * @param {string} label 対象の呼び名
 */
export function exitIfNoFiles(files, label) {
  const reason = emptyListReason(files, label)
  if (!reason) return
  console.log(reason)
  process.exit(2)
}
