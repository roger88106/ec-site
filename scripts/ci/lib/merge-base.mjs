// 差分と比べる基準の commit（develop・main との分岐点）を求める。
// マイグレーションの検査（migrations）と、差分の追加行の文体の検査（ja-text --changed）が使う。
// CI の checkout には origin/ の ref しかないことがあるので、origin/ の方を先に見る。shallow clone の確認は呼ぶ側で行う（lib/shallow-clone）
import { execFileSync } from 'node:child_process'

/**
 * git を実行し、出力の前後の空白を除いて返す。失敗したら null を返す。
 *
 * @param {string[]} args 引数
 * @param {string} cwd 実行する場所
 * @returns {string | null} 出力。失敗したら null
 */
export function tryGit(args, cwd) {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch {
    return null
  }
}

/**
 * HEAD と、ブランチ（origin/ の方を先に見る）との merge-base を返す。
 * ref があっても履歴が繋がらなければ、次の候補（origin/ のないブランチ）を見る。
 *
 * @param {string} branch ブランチの名前（develop、main）
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {{ base: string, revision: string } | null} 使った ref と merge-base の commit。どちらの候補でも求められなければ null
 */
export function mergeBaseWith(branch, cwd = process.cwd()) {
  for (const base of [`origin/${branch}`, branch]) {
    const ref = (base.includes('/') ? 'refs/remotes/' : 'refs/heads/') + base
    if (!tryGit(['rev-parse', '--verify', '-q', ref], cwd)) continue
    const revision = tryGit(['merge-base', 'HEAD', ref], cwd)
    if (revision) return { base, revision }
  }
  return null
}

/** 1 つ目の親を基準にする、push の先のブランチ */
const PUSH_BRANCHES = ['develop', 'main']

/**
 * develop か main への push（の後の状態）かを返す。
 * 現在のブランチの名前が develop か main のとき、または CI の push のイベントで、push の先が develop か main のとき（ブランチを checkout していなくてもよい）。
 *
 * @param {string} cwd 実行する場所（リポジトリの中）
 * @param {Record<string, string | undefined>} env 環境変数
 * @returns {boolean} develop か main への push なら true
 */
function isPushToMainline(cwd, env) {
  if (PUSH_BRANCHES.includes(tryGit(['symbolic-ref', '--short', '-q', 'HEAD'], cwd))) return true
  return env.GITHUB_EVENT_NAME === 'push' && PUSH_BRANCHES.includes(env.GITHUB_REF_NAME)
}

/**
 * 差分の基準の commit を返す。HEAD と develop との merge-base である。
 * merge-base が HEAD 自身で、develop か main への push（isPushToMainline）のときは、HEAD の 1 つ目の親（マージの前の develop）にする。
 * それ以外で merge-base が HEAD 自身のとき（develop から分けたばかりで commit がない作業ブランチ）は、HEAD にする（作業ツリーの変更だけを見る）。
 * develop が求められないとき（ref がない、履歴が繋がらない）は HEAD の 1 つ目の親にし、使った ref を null にする（呼ぶ側が警告を出す）。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @param {Record<string, string | undefined>} [env] 環境変数（CI の push のイベントを見る）。なければ `process.env`
 * @returns {{ revision: string | null, base: string | null }} 基準の commit（HEAD がないか、初めの commit で親がなければ null）と、使った ref
 */
export function resolveDiffBase(cwd = process.cwd(), env = process.env) {
  const head = tryGit(['rev-parse', '--verify', '-q', 'HEAD'], cwd)
  if (!head) return { revision: null, base: null }
  const parent = tryGit(['rev-parse', '--verify', '-q', 'HEAD^1'], cwd)
  const found = mergeBaseWith('develop', cwd)
  if (!found) return { revision: parent, base: null }
  if (found.revision !== head) return { revision: found.revision, base: found.base }
  return { revision: isPushToMainline(cwd, env) ? parent : head, base: found.base }
}
