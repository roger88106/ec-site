// 追跡しているファイルが、リポジトリの .gitignore（各フォルダーのものを含む）で除外の対象になっていないことを確認する。
// .gitignore は許可リストの形（全てを除外し、Git に入れるものだけを `!` で戻す）なので、変更で追跡しているファイルが除外の対象になっても、通常は気づけない。
// `git add -f` で除外のファイルを加えた場合も、同じ状態になる。
//
// 判定は `git ls-files -z -i -c --exclude-per-directory=.gitignore` で行う。.git/info/exclude と全体の core.excludesFile は読まない（--exclude-standard を使わない）。
// 理由：手元の除外の設定で手元でだけ失敗することを避け、手元と CI で同じ結果にするため。
// 大文字と小文字は区別する（`core.ignorecase=false`）。Windows の clone は区別しない設定になり、Linux の CI と結果が変わるため。
//
// 失敗したとき：出力の各ファイルについて `git check-ignore -v --no-index <ファイル>` で、一致した行を調べる。
//   その行を直す（許可の行を戻す、除外の行を狭める）か、追跡が間違いなら `git rm --cached <ファイル>` で追跡をやめる。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/ignored-tracked.mjs（リポジトリのルートで）
import { execFileSync } from 'node:child_process'
import { splitNulList } from '../../shared/git/git-files.mjs'

/** 出力の最大の大きさ（バイト） */
const MAX_BUFFER = 64 * 1024 * 1024

/**
 * git を実行して、NUL 区切りの出力をパスの配列にして返す。
 *
 * @param {string[]} args git の引数
 * @param {string} cwd 実行する場所
 * @returns {string[]} パス
 */
function gitList(args, cwd) {
  return splitNulList(execFileSync('git', ['-c', 'core.ignorecase=false', ...args], { cwd, encoding: 'utf8', maxBuffer: MAX_BUFFER }))
}

/**
 * 追跡しているファイルのうち、リポジトリの .gitignore で除外の対象になるものを返す。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {string[]} cwd からの相対パス（/ 区切り）
 */
export function findIgnoredTracked(cwd = process.cwd()) {
  return [...new Set(gitList(['ls-files', '-z', '-i', '-c', '--exclude-per-directory=.gitignore'], cwd))]
}

/**
 * 検査を実行して、結果を出力する。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）
 * @param {(line: string) => void} [log] 1 行を出力する関数
 * @returns {number} 終了コード（0：通る、1：除外された追跡しているファイルがある、2：追跡しているファイルが 1 件もない）
 */
export function runIgnoredTracked(cwd = process.cwd(), log = console.log) {
  const tracked = gitList(['ls-files', '-z', '-c'], cwd)
  if (tracked.length === 0) {
    log('追跡しているファイルが 1 件も見つからない（リポジトリの中で実行しているか確認する）')
    log('追跡しているファイル：0 件、除外されたもの：0 件')
    return 2
  }
  const ignored = findIgnoredTracked(cwd)
  if (ignored.length > 0) {
    log('追跡しているファイルが、.gitignore で除外の対象になっている：')
    for (const file of ignored) log(`  ${file}`)
    log('一致した行は、`git check-ignore -v --no-index <ファイル>` で調べる。.gitignore を直すか、追跡が間違いなら `git rm --cached <ファイル>` で追跡をやめる')
  }
  log(`追跡しているファイル：${new Set(tracked).size} 件、除外されたもの：${ignored.length} 件`)
  return ignored.length > 0 ? 1 : 0
}

if (import.meta.main) {
  process.exit(runIgnoredTracked())
}
