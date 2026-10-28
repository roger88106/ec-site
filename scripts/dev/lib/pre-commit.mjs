// commit の前の hook（scripts/dev/hooks/pre-commit）の処理。ステージした内容を取り出して、CI の検査を実行する。
// 今のブランチが develop か main なら、検査の前に commit を失敗にする（直接 commit しない規則。AGENTS.md の Git の表）。
// detached HEAD（rebase の途中を含む）とブランチを読めないときは通す。
// CI の検査には日本語の文の検査（ja-text）が含まれ、ステージした内容の error は失敗にする。
// 検査が通ったら、ステージした追加行の warning だけを表示する（追加していない既存の行の warning は表示しない。warning は失敗にしない）
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

/** 取り出しに失敗したときの終了コード（検査の失敗の 1 と区別する） */
export const CHECKOUT_FAILED = 2

/** 追加行の差分、または warning の一覧の取得に失敗したときの終了コード。取得できないまま成功にしない */
export const WARNING_FAILED = 3

/** develop か main に commit しようとしたときの終了コード（検査の失敗と区別する） */
export const BRANCH_REJECTED = 4

/**
 * 直接 commit しないブランチと、そのときに表示する手順。変更は作業ブランチから pull request で入れる（AGENTS.md の Git の表）。
 * main の作業ブランチは緊急の修正だけで、main から切る（ブランチとリリース 第 2.1 節、第 5 節）。それ以外の変更は、develop から作業ブランチを作る
 */
const REJECTIONS = new Map([
  [
    'develop',
    [
      'develop には直接 commit しない。今の develop から作業ブランチを作り、そこで commit する（今の HEAD から切るので、ステージした変更は持ち越される）。',
      '',
      '  git switch -c <issue の番号>-<短い名前>',
    ],
  ],
  [
    'main',
    [
      'main には直接 commit しない。',
      '緊急の修正なら、今の main から hotfix のブランチを作り、そこで commit する。',
      '',
      '  git switch -c hotfix-<issue の番号>-<短い名前>',
      '',
      'それ以外の変更は、変更を退避して develop から作業ブランチを作り、そこで変更を戻して commit する。',
      '',
      '  git stash push -m <退避の名前>',
      '  git switch -c <issue の番号>-<短い名前> develop',
      '  git stash pop',
    ],
  ],
])

/**
 * 今のブランチに commit してよいかを判定し、だめなら表示する手順を返す。
 * detached HEAD（rebase の途中を含む）とブランチを読めないときは通す。判定を誤って全ての commit を失敗にしないため。
 *
 * @param {string | null} branch 今のブランチの名前。detached HEAD か読めなければ null
 * @returns {string | null} commit を失敗にするときの表示。通すなら null
 */
export function branchRejection(branch) {
  if (branch === null) return null
  return REJECTIONS.get(branch)?.join('\n') ?? null
}

/**
 * `git symbolic-ref -q HEAD` の結果から、今のブランチの名前を取り出す。
 *
 * @param {{ status: number | null, stdout: string }} result symbolic-ref の終了コードと出力
 * @returns {string | null} ブランチの名前。detached HEAD（終了コード 1）か、読めなければ null
 */
export function branchOfHead({ status, stdout }) {
  if (status !== 0) return null
  const ref = stdout.trim()
  return ref.startsWith('refs/heads/') ? ref.slice('refs/heads/'.length) : null
}

/**
 * `git diff --cached -U0` の出力から、追加した行の番号をファイルごとに取り出す。
 *
 * @param {string} diff 差分（-U0）
 * @returns {Map<string, Set<number>>} ファイルのパスと、追加した行の番号（1 から）
 */
export function parseAddedLines(diff) {
  const added = new Map()
  let lines = null
  let inHunk = false
  for (const line of diff.split('\n')) {
    if (line.startsWith('diff --git ')) {
      inHunk = false
      continue
    }
    // ファイルの見出しは、ハンクの外（diff --git の後）だけで読む。ハンクの中の `+++ ` は、本文が `++ ` で始まる追加行
    if (!inHunk && line.startsWith('+++ ')) {
      const target = line.slice(4).replace(/\r$/, '')
      if (target === '/dev/null') {
        lines = null
        continue
      }
      const file = target.replace(/^b\//, '')
      lines = added.get(file) ?? new Set()
      added.set(file, lines)
      continue
    }
    const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/.exec(line)
    if (hunk) {
      inHunk = true
      if (!lines) continue
      const count = hunk[2] === undefined ? 1 : Number(hunk[2])
      for (let i = 0; i < count; i++) lines.add(Number(hunk[1]) + i)
    }
  }
  return added
}

/**
 * warning の一覧（ja-text の --warning の出力）から、追加した行の warning だけを選ぶ。
 * 一覧の行は「<ファイル>:<行> <内容>」の形。ほかの行（件数の行など）は除く。
 *
 * @param {string} output ja-text --warning の出力
 * @param {Map<string, Set<number>>} added 追加した行（parseAddedLines の結果）
 * @returns {string[]} 追加した行の warning の行
 */
export function selectAddedWarnings(output, added) {
  return output
    .split('\n')
    .map((line) => line.replace(/\r$/, ''))
    .filter((line) => {
      const hit = /^(.+?):(\d+) /.exec(line)
      return hit !== null && (added.get(hit[1])?.has(Number(hit[2])) ?? false)
    })
}

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
 * 検査の script を実行する bash を返す。hook の shell script が、自分を実行している bash のパスを PRE_COMMIT_BASH で渡す。
 * Windows の素の PATH では、名前の bash が WSL の bash.exe を指すことがあり、C:/ のパスを渡した検査が失敗するため。
 *
 * @param {Record<string, string | undefined>} env 環境変数
 * @returns {string} bash のパス。渡されていなければ、PATH の bash
 */
export function checkShell(env) {
  return env.PRE_COMMIT_BASH || 'bash'
}

/**
 * 今のブランチが develop か main なら、何もせずに 4 を返す。
 * そうでなければ、ステージした内容を一時ディレクトリに取り出し、そこで CI の検査を実行して、hook の終了コードを返す。
 * 取り出しに失敗したら 2、そうでなければ検査の終了コード（検査が終了コードを返さなければ 1）。
 * 一時ディレクトリは、成功でも失敗でも消す。
 *
 * @param {object} deps 外部の処理（テストで差し替える）
 * @param {string} deps.root リポジトリのルート
 * @param {string} deps.gitDir .git のディレクトリの絶対パス
 * @param {string | null} deps.branch 今のブランチの名前。detached HEAD か読めなければ null
 * @param {Record<string, string | undefined>} deps.env 環境変数
 * @param {(prefix: string, env: Record<string, string | undefined>) => number | null} deps.checkout ステージした内容を prefix の下に取り出す。終了コードを返す
 * @param {(script: string, env: Record<string, string | undefined>) => number | null} deps.runCheck 検査の script を実行する。終了コードを返す
 * @param {(env: Record<string, string | undefined>) => { status: number | null, stdout: string }} deps.diffAdded ステージした差分（-U0）を返す
 * @param {(staged: string, env: Record<string, string | undefined>) => { status: number | null, stdout: string }} deps.listWarnings 取り出した先で warning の一覧を出力する
 * @param {(text: string) => void} deps.print warning を表示する
 * @param {(text: string) => void} deps.printError commit を失敗にした理由を表示する
 * @param {() => string} deps.makeTemp 一時ディレクトリを作る
 * @param {(dir: string) => void} deps.removeTemp 一時ディレクトリを消す
 * @returns {number} hook の終了コード
 */
export function runPreCommit({ root, gitDir, branch, env, checkout, runCheck, diffAdded, listWarnings, print, printError, makeTemp, removeTemp }) {
  // ブランチの誤りは、時間のかかる検査の前に知らせる
  const rejection = branchRejection(branch)
  if (rejection !== null) {
    printError(rejection)
    return BRANCH_REJECTED
  }
  const indexFile = resolveIndexFile(env.GIT_INDEX_FILE, root)
  const baseEnv = indexFile === undefined ? env : { ...env, GIT_INDEX_FILE: indexFile }
  const staged = makeTemp()
  try {
    if (checkout(`${staged}/`, baseEnv) !== 0) return CHECKOUT_FAILED
    // 検査の script は git（ls-files、log など）を呼ぶ。リポジトリは元のものを使い、作業ツリーだけを取り出した先にする
    const checkEnv = { ...baseEnv, GIT_DIR: gitDir, GIT_WORK_TREE: staged }
    const status = runCheck(`${staged}/scripts/ci/check-all.sh`, checkEnv)
    if (status !== 0) return status ?? 1
    // 検査が通ったら、追加行の warning を表示する。差分か一覧を取得できなければ、表示できないまま成功にせず失敗にする
    const diff = diffAdded(baseEnv)
    const warnings = listWarnings(staged, checkEnv)
    if (diff.status !== 0 || warnings.status !== 0) return WARNING_FAILED
    const lines = selectAddedWarnings(warnings.stdout, parseAddedLines(diff.stdout))
    if (lines.length > 0) print(`警告：追加した行に warning が ${lines.length} 件ある（読んで、直すかを決める）\n${lines.join('\n')}`)
    return 0
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
    branch: branchOfHead(git(['symbolic-ref', '-q', 'HEAD'])),
    env: process.env,
    checkout: (prefix, env) =>
      spawnSync('git', ['-c', 'core.longpaths=true', 'checkout-index', '-a', `--prefix=${toSlash(prefix)}`], { env, stdio: 'inherit' }).status,
    runCheck: (script, env) => spawnSync(checkShell(env), [toSlash(script)], { env, stdio: 'inherit' }).status,
    diffAdded: (env) => {
      const args = ['-c', 'core.quotepath=false', 'diff', '--cached', '--src-prefix=a/', '--dst-prefix=b/', '-U0', '--no-color', '--no-ext-diff', '--diff-filter=ACMR']
      const result = spawnSync('git', args, { env, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
      return { status: result.status, stdout: result.stdout ?? '' }
    },
    listWarnings: (staged, env) => {
      const result = spawnSync(process.execPath, [`${staged}/scripts/ci/ja-text.mjs`, '--warning'], { cwd: staged, env, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
      return { status: result.status, stdout: result.stdout ?? '' }
    },
    print: (text) => console.log(text),
    printError: (text) => console.error(text),
    makeTemp: () => toSlash(mkdtempSync(path.join(tmpdir(), 'pre-commit.'))),
    removeTemp: (dir) => rmSync(dir, { recursive: true, force: true }),
  })
}
