// commit の前の hook（scripts/dev/hooks/pre-commit）の処理。ステージした内容を取り出して、CI の検査を実行する
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

/** 取り出しに失敗したときの終了コード（検査の失敗の 1 と区別する） */
export const CHECKOUT_FAILED = 2

/**
 * git が GIT_INDEX_FILE に相対パスを渡したときに、リポジトリのルートからの絶対パスにする。
 * git commit -a などは、一時の index をこの環境変数で渡す。
 *
 * @param {string | undefined} indexFile GIT_INDEX_FILE の値
 * @param {string} root リポジトリのルート
 * @returns {string | undefined} 絶対パス。値がなければ undefined
 */
export function resolveIndexFile(indexFile, root) {
  if (!indexFile) return undefined
  if (path.posix.isAbsolute(indexFile) || path.win32.isAbsolute(indexFile)) return indexFile
  return path.join(root, indexFile)
}

/**
 * ステージした内容を一時ディレクトリに取り出し、そこで CI の検査を実行して、hook の終了コードを返す。
 * 取り出しに失敗したら 2、そうでなければ検査の終了コード（検査が終了コードを返さなければ 1）。
 * 一時ディレクトリは、成功でも失敗でも消す。
 *
 * @param {object} deps 外部の処理（テストで差し替える）
 * @param {string} deps.root リポジトリのルート
 * @param {string} deps.gitDir .git のディレクトリの絶対パス
 * @param {Record<string, string | undefined>} deps.env 環境変数
 * @param {(prefix: string, env: Record<string, string | undefined>) => number | null} deps.checkout ステージした内容を prefix の下に取り出す。終了コードを返す
 * @param {(script: string, env: Record<string, string | undefined>) => number | null} deps.runCheck 検査の script を実行する。終了コードを返す
 * @param {() => string} deps.makeTemp 一時ディレクトリを作る
 * @param {(dir: string) => void} deps.removeTemp 一時ディレクトリを消す
 * @returns {number} hook の終了コード
 */
export function runPreCommit({ root, gitDir, env, checkout, runCheck, makeTemp, removeTemp }) {
  const indexFile = resolveIndexFile(env.GIT_INDEX_FILE, root)
  const baseEnv = indexFile === undefined ? env : { ...env, GIT_INDEX_FILE: indexFile }
  const staged = makeTemp()
  try {
    if (checkout(`${staged}/`, baseEnv) !== 0) return CHECKOUT_FAILED
    // 検査の script は git（ls-files、log など）を呼ぶ。リポジトリは元のものを使い、作業ツリーだけを取り出した先にする
    const status = runCheck(`${staged}/scripts/ci/check-all.sh`, { ...baseEnv, GIT_DIR: gitDir, GIT_WORK_TREE: staged })
    return status ?? 1
  } finally {
    removeTemp(staged)
  }
}

/**
 * 実際の git と一時ディレクトリで runPreCommit を実行する。
 * 直接実行の判定に頼らず、入口（pre-commit-main.mjs）が必ず呼ぶ。判定が外れて hook が何もせず成功するのを防ぐため。
 *
 * @returns {number} hook の終了コード
 */
export function main() {
  const git = (args) => spawnSync('git', args, { encoding: 'utf8' })
  const root = git(['rev-parse', '--show-toplevel']).stdout.trim()
  const gitDir = path.resolve(git(['rev-parse', '--git-dir']).stdout.trim())
  const toSlash = (value) => value.split(path.sep).join('/')
  return runPreCommit({
    root,
    gitDir: toSlash(gitDir),
    env: process.env,
    checkout: (prefix, env) =>
      spawnSync('git', ['-c', 'core.longpaths=true', 'checkout-index', '-a', `--prefix=${toSlash(prefix)}`], { env, stdio: 'inherit' }).status,
    runCheck: (script, env) => spawnSync('bash', [toSlash(script)], { env, stdio: 'inherit' }).status,
    makeTemp: () => toSlash(mkdtempSync(path.join(tmpdir(), 'pre-commit.'))),
    removeTemp: (dir) => rmSync(dir, { recursive: true, force: true }),
  })
}
