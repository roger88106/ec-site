// 複合の action（action.yml）を置いたディレクトリが、Dependabot の github-actions の対象に入っていることを確認する。
// Dependabot の `/` は、.github/workflows とルートの action.yml だけを見る。
// `.github/actions/<名前>/` は、.github/dependabot.yml の directories に加えなければ更新されず、action のバージョンが古いまま残る。
// 対象の `*` は 1 つの階層として読む。
//
// 失敗したとき：新しい複合の action のディレクトリを directories に加える。
// 除外：なし
// 使い方：node scripts/ci/dependabot-actions.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { load } from 'js-yaml'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** Dependabot の設定 */
const DEPENDABOT_FILE = '.github/dependabot.yml'

/**
 * dependabot.yml の github-actions の項目の `directory` と `directories` を読む。
 *
 * @param {string} text dependabot.yml の内容
 * @returns {string[]} 対象のディレクトリ（書いた順）
 */
export function readActionsDirectories(text) {
  const updates = load(text)?.updates ?? []
  return updates
    .filter((update) => update?.['package-ecosystem'] === 'github-actions')
    .flatMap((update) => [update.directory, ...(update.directories ?? [])])
    .filter((directory) => typeof directory === 'string' && directory !== '')
}

/**
 * Git の対象のファイルから、action.yml（または action.yaml）を置いたディレクトリを返す。
 *
 * @param {string[]} files リポジトリのルートからの相対パス（/ 区切り）
 * @returns {string[]} 先頭に `/` を付けたディレクトリ（ルートは `/`）。名前の順
 */
export function findActionDirectories(files) {
  const directories = new Set()
  for (const file of files) {
    const match = /^(?:(.*)\/)?action\.ya?ml$/.exec(file)
    if (match) {
      directories.add(`/${match[1] ?? ''}`)
    }
  }
  return [...directories].sort()
}

/**
 * Dependabot の対象が、ディレクトリを含むか。`*` は 1 つの階層の中の任意の文字として扱う。`/` はルートだけで、下の階層を含まない。
 *
 * @param {string} target 対象の書き方
 * @param {string} directory ディレクトリ
 * @returns {boolean} 含めば true
 */
function covers(target, directory) {
  const pattern = target.split('*').map((part) => part.replace(/[.+?^${}()|[\]\\]/g, '\\$&'))
  return new RegExp(`^${pattern.join('[^/]*')}$`).test(directory)
}

/**
 * Dependabot の対象に入っていない action のディレクトリを探す。
 *
 * @param {string[]} actionDirectories action.yml を置いたディレクトリ
 * @param {string[]} targets Dependabot の github-actions の対象
 * @returns {string[]} 入っていないディレクトリの説明
 */
export function findUncoveredActions(actionDirectories, targets) {
  return actionDirectories
    .filter((directory) => !targets.some((target) => covers(target, directory)))
    .map((directory) => `${DEPENDABOT_FILE} の github-actions の directories にない（加える）：${directory}`)
}

if (isMain(import.meta.url)) {
  const files = listGitFiles()
  exitIfNoFiles(files, 'Git のファイル')
  exitIfNoFiles(files.filter((file) => file === DEPENDABOT_FILE), 'Dependabot の設定')
  const actionDirectories = findActionDirectories(files)
  const targets = readActionsDirectories(readFileSync(DEPENDABOT_FILE, 'utf8'))
  const problems = findUncoveredActions(actionDirectories, targets)
  if (targets.length === 0) {
    problems.push(`${DEPENDABOT_FILE} に github-actions の対象がない`)
  }
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`複合の action：${actionDirectories.length} 件、Dependabot の対象：${targets.length} 件、問題：${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
