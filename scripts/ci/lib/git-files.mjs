// Git の対象のファイル（追跡しているファイルと、追跡していない無視されないファイル）の一覧を返す。
// 追跡していないファイルも含める理由：検査は commit の前に実行し、新しく作ったファイル（テスト、文書）はその時点では git add していないことが多い。
// 追跡しているファイルだけにすると、新しいファイルが検査されずに手元で通り、CI で初めて失敗する。
// 手元と CI の違い：CI の作業ツリーには追跡していないファイルがない。手元にだけある commit しないファイルで、手元だけ失敗することがある（.gitignore に書くか /.local/ に置いて避ける）。
// commit し忘れたファイルを指す参照は、手元で通り CI で失敗することがある。
// ファイルを探す検査（doc-refs、ja-text、frontend-rules、java-rules、message-comments、spec-numbers、test-names、time-waits など）が使う
import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'

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
 * 追跡していない、無視されないファイルの一覧を返す（差分に出ない新しいファイルを、全ての行を追加行として見る検査が使う。ja-text の --changed）。
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
  const output = execFileSync('git', ['ls-files', '-z', ...kinds, '--exclude-standard', '--', ...pathspecs], { cwd, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  return [...new Set(splitNulList(output))].filter((file) => existsSync(path.join(cwd, file))).sort()
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
 * 対象のファイルが 0 件なら、理由を出して終了コード 1 で終わる。
 *
 * @param {string[]} files 検査の対象のファイル
 * @param {string} label 対象の呼び名
 */
export function exitIfNoFiles(files, label) {
  const reason = emptyListReason(files, label)
  if (!reason) return
  console.log(reason)
  process.exit(1)
}
