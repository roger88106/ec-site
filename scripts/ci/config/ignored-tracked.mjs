// 追跡しているファイルが、リポジトリの .gitignore（各フォルダーのものを含む）で除外の対象になっていないことを確認する。
// .gitignore は許可リストの形（全てを除外し、Git に入れるものだけを `!` で戻す）なので、変更で追跡しているファイルが除外の対象になっても、通常は気づけない。
// `git add -f` で除外のファイルを加えた場合も、同じ状態になる。
//
// 判定は git-files.mjs の listIgnoredTrackedFiles で行う（.git/info/exclude と全体の core.excludesFile は読まず、大文字と小文字は区別する。理由はその関数のコメント）。
//
// 手で実行したときは、作業ツリーにある追跡していない .gitignore も読むので、手元でだけ失敗することがある。commit の前の hook と CI では起きない。
//
// 失敗したとき：出力の各ファイルについて `git check-ignore -v --no-index <ファイル>` で、一致した行を調べる。
//   その行を直す（許可の行を戻す、除外の行を狭める）か、追跡が間違いなら `git rm --cached <ファイル>` で追跡をやめる。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/ignored-tracked.mjs（リポジトリのルートで）
import { emptyListReason, listIgnoredTrackedFiles, listTrackedFiles } from '../../shared/git/git-files.mjs'

/**
 * 追跡しているファイルのうち、リポジトリの .gitignore で除外の対象になるものを返す。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）
 */
export function findIgnoredTracked(cwd = process.cwd()) {
  return listIgnoredTrackedFiles(cwd)
}

/**
 * 検査を実行して、結果を出力する。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）
 * @param {(line: string) => void} [log] 1 行を出力する関数
 * @param {{ findIgnored?: (cwd: string) => string[] }} [deps] 除外の判定の関数（テストで差し替える）
 * @returns {number} 終了コード（0：通る、1：除外された追跡しているファイルがある、2：git の失敗か、追跡しているファイルが 1 件もない）
 */
export function runIgnoredTracked(cwd = process.cwd(), log = console.log, { findIgnored = findIgnoredTracked } = {}) {
  let tracked = []
  let ignored
  try {
    tracked = listTrackedFiles(cwd)
    ignored = tracked.length === 0 ? [] : findIgnored(cwd)
  } catch (e) {
    log(`git で追跡しているファイルを読めない（リポジトリの中で実行しているか確認する）：${e.message}`)
    log(`追跡しているファイル：${tracked.length} 件、除外されたもの：0 件`)
    return 2
  }
  const reason = emptyListReason(tracked, '追跡しているファイル')
  if (reason) {
    log(reason)
    log('追跡しているファイル：0 件、除外されたもの：0 件')
    return 2
  }
  if (ignored.length > 0) {
    log('追跡しているファイルが、.gitignore で除外の対象になっている：')
    for (const file of ignored) log(`  ${file}`)
    log('一致した行は、`git check-ignore -v --no-index <ファイル>` で調べる。.gitignore を直すか、追跡が間違いなら `git rm --cached <ファイル>` で追跡をやめる')
  }
  log(`追跡しているファイル：${tracked.length} 件、除外されたもの：${ignored.length} 件`)
  return ignored.length > 0 ? 1 : 0
}

if (import.meta.main) {
  process.exit(runIgnoredTracked())
}
