// ============================================================================
// Claude Code の PreToolUse の hook（.claude/settings.json から呼ぶ）。
// 同じディレクトリの pre-commit・commit-msg は git の hook で、これとは別のもの（git でなく Claude Code が実行する）。
// 本書は入口と判定のまとめで、処理は agent-guard/ の下に責務ごとに分ける：語の分割（shell-words）、
// 前に付けたコマンドとディレクトリ（commands）、git の規則（git-rules）、gh の規則（gh-rules）、マージの条件（merge-rules）。
//
// エージェントが Bash で実行しようとするコマンドの全文を読み、例外のない規則に反するものを拒否する。
// - agent/ で始まるブランチ以外の push 済みの履歴の書き換え：git push の force（-f、--force、--force-with-lease、--force-if-includes、--mirror、+ の refspec）。
//   push の先（refspec か、ないときは今のブランチと上流）が全て agent/ で始まるときだけ通し、先を決められないときは拒否する
// - commit の前の検査の省略：git commit の --no-verify と -n、git -c core.hooksPath=…
// - pre-commit hook を置いていないリポジトリでの git commit（git rev-parse --git-path hooks/pre-commit のファイルが、
//   scripts/dev/hooks/pre-commit を呼んでいなければ拒否する。作業ツリーでは共通の .git の hooks を見る）
// - gh api によるマージ（REST の …/pulls/<番号>/merge と、語に mergePullRequest を含む GraphQL）
// - --match-head-commit のない gh pr merge
// 利用者の許可が必要な操作では、確認を求める（利用者に確認の画面を表示させる）。
// - マージ：gh pr merge のうち、利用者の許可なしにマージしてよい条件（docs/05-開発規則/merging.md「マージの許可と後片付け」）を満たさないもの。
//   条件は gh で pull request を読んで調べ、読めないときも確認を求める。未解決の指摘が 0 かは読めないので見ない（文章の規則のまま）。
//   向き先が agent/ で始まる pull request は、開いていることと head の commit だけを見て通す。
// - tag：git tag での作成、git push の --tags・--follow-tags・tag の refspec（tag の削除を含む）、gh release create、
//   Release と tag の参照を変える gh api（GET 以外のリクエストで、パスが releases・git/refs・git/tags のもの）
// - ruleset：ruleset とブランチの保護を変える gh api（GET 以外のリクエスト）
// GitHub が受け付けない操作（gh pr merge の --admin、自動のマージ）と、意図した回避（bash -c、エイリアス、ファイルから読む GraphQL の本文）は見ない。
// permission の規則は git -C やフルパスで書いた git・gh に当たらず、commit のメッセージにある語にも当たるので、hook で語を読む。
//
// PowerShell のツールのコマンドは、書き方を読まずに git・gh のサブコマンドだけを見る。
// サブコマンドが hook の判定する操作（judgePowerShell）なら拒否し、Bash のツールで実行するよう返す。読むだけのコマンドは通す。
//
// .claude/settings.json の hook のコマンドは、tool_input.command に git か gh の語がなければ、この script を起動せずに通す
// （判定は全て git・gh のコマンドだけが対象なので、他のコマンドで node を起動する時間を省く）。command は node を使わずに awk で取り出し、
// JSON のエスケープと引用符を戻してから語を探す。\ を消した形（gi\t）と、\ を空白に置き換えた形（C:\tools\gh）の両方で語を探す。Windows では bash -c の引数の \\ が \ に減って届くので、hook のコマンドに \ を書かず、awk の中で作る。
// 入力：標準入力の JSON（tool_name、tool_input.command、cwd など）。Bash と PowerShell 以外のツールは何もせずに通す。
// 出力：拒否か確認を求めるときは hookSpecificOutput の permissionDecision を標準出力に出力する。通すときは何も出力しない。終了コードは常に 0。
// 対象のディレクトリは、入力の cwd を起点に、コマンドの中の cd・pushd・popd、env -C、git -C で決める。前に付けた GIT_DIR・GIT_WORK_TREE・GH_REPO も見る
// （$CLAUDE_PROJECT_DIR は、作業ツリーのセッションでもメインの作業ツリーを指すことがあるので使わない。cd・pushd・git -C の先が変数かコマンドの置き換えを含めば、ディレクトリを変えない）。
// $(…) とバッククォートの中のコマンド、timeout・env・xargs などを前に付けたコマンド、
// シェルの予約語（do、then、{、! など）の後のコマンド、function f { … } の本体も判定する。
// 判定の中で例外が発生したときは、git か gh を含むコマンドは拒否し、含まないコマンドは通す
// （判定の不具合で force push や許可のないマージを通さず、かつ git・gh と関係のない Bash まで止めないため）。
// ============================================================================
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { parseCommands } from './agent-guard/commands.mjs'
import { TAG_REASON, ask, deny } from './agent-guard/decision.mjs'
import { GH_PR_OPTIONS, GH_RELEASE_OPTIONS, judgeGhApi, parseGhArgs, parseGhInvocation } from './agent-guard/gh-rules.mjs'
import { GIT_JUDGES, parseGitInvocation } from './agent-guard/git-rules.mjs'
import { GH_BUDGET_MS, judgeMerge } from './agent-guard/merge-rules.mjs'

/** @typedef {import('./agent-guard/decision.mjs').Decision} Decision */
/** @typedef {import('./agent-guard/decision.mjs').Deps} Deps */
/** @typedef {import('./agent-guard/commands.mjs').PathOptions} PathOptions */
/** @typedef {import('./agent-guard/gh-rules.mjs').GhInvocation} GhInvocation */

/** git の呼び出しのタイムアウト（ミリ秒）。hook 全体のタイムアウトより短くする */
export const GIT_TIMEOUT_MS = 5000

/**
 * gh の呼び出しを判定する（マージ、gh release create、gh api）。
 *
 * @param {GhInvocation} gh gh の呼び出し
 * @param {Deps} deps 外部の処理
 * @param {{ deadline: number }} budget gh に使える時間の期限
 * @returns {Decision | null} 判定
 */
function judgeGh(gh, deps, budget) {
  const [group, ...rest] = gh.args
  if (group === 'api') return judgeGhApi(rest)
  if (group === 'release') return parseGhArgs(rest, GH_RELEASE_OPTIONS).positionals[0] === 'create' ? ask(TAG_REASON) : null
  if (group === 'pr' && parseGhArgs(rest, GH_PR_OPTIONS).positionals[0] === 'merge') return judgeMerge(rest, gh, deps, budget)
  return null
}

/**
 * コマンドの全文を判定する。拒否があればそれを、なければ最初の確認を返す。
 *
 * @param {string} command コマンドの全文
 * @param {string} cwd 入力の cwd
 * @param {Deps} deps 外部の処理
 * @param {PathOptions} [options] パスの解き方
 * @returns {Decision | null} 判定。通すときは null
 */
export function judgeCommand(command, cwd, deps, options = {}) {
  const decisions = []
  const budget = { deadline: Date.now() + GH_BUDGET_MS }
  for (const parsed of parseCommands(command, cwd, options)) {
    const git = parseGitInvocation(parsed, options)
    const gh = git === null ? parseGhInvocation(parsed) : null
    const judges = git === null ? [] : GIT_JUDGES.map((judge) => () => judge(git, deps))
    if (gh !== null) judges.push(() => judgeGh(gh, deps, budget))
    for (const judge of judges) {
      const decision = judge()
      if (decision?.decision === 'deny') return decision
      if (decision) decisions.push(decision)
    }
  }
  return decisions[0] ?? null
}

/**
 * 実際の git・gh とファイルを使う外部の処理を作る。gh は PATH にあるものを使う。
 *
 * @param {Record<string, string | undefined>} [env] git と gh に渡す環境変数
 * @returns {Deps} 外部の処理
 */
export function createDeps(env = process.env) {
  return {
    runGit: (args, cwd, extra = {}) => spawnSync('git', args, { cwd, env: { ...env, ...extra }, encoding: 'utf8', timeout: GIT_TIMEOUT_MS, windowsHide: true }),
    // hook の中では答えられないので、gh に入力を求めさせない
    runGh: (args, { cwd, timeout }) => spawnSync('gh', args, { cwd, env: { ...env, GH_PROMPT_DISABLED: '1' }, encoding: 'utf8', timeout, windowsHide: true }),
    readText: (file) => {
      try {
        return readFileSync(file, 'utf8')
      } catch {
        return null
      }
    },
  }
}

/** git・gh のコマンドの名前（git.exe、gh.exe、フルパスを含む） */
const GIT_OR_GH_PROGRAM = /(^|[\\/])(git|gh)(\.exe)?$/i

/** hook が判定する、git・gh のサブコマンド（gh pr merge は pr の次の語で見る） */
const OPERATIONS = ['push', 'commit', 'tag', 'merge', 'api', 'release']

/** git の前のオプションのうち、値を次の語に取るもの */
const GIT_VALUE_OPTIONS = ['-C', '-c', '--config-env', '--git-dir', '--work-tree', '--namespace']

/**
 * PowerShell のコマンドの git・gh が、hook が判定する操作か。git・gh の後のオプションを飛ばした最初の語（gh pr は次の語）を見る。
 * 前のオプションに hooksPath を含むときも、操作とみなす。
 *
 * @param {string[]} words git・gh の後の語
 * @returns {boolean} 操作なら true
 */
function isOperation(words) {
  let i = 0
  // 前のオプションを、値と一緒に飛ばす
  for (; i < words.length && words[i].startsWith('-'); i++) {
    if (/hookspath/i.test(words[i])) return true
    if (GIT_VALUE_OPTIONS.includes(words[i])) {
      i++
      if (/hookspath/i.test(words[i] ?? '')) return true
    }
  }
  const subcommand = (words[i] ?? '').toLowerCase()
  return OPERATIONS.includes(subcommand) || (subcommand === 'pr' && (words[i + 1] ?? '').toLowerCase() === 'merge')
}

/**
 * PowerShell のツールのコマンドを判定する。git・gh のサブコマンドが、hook が判定する操作なら拒否する。
 * PowerShell の書き方は読まず、空白と引用符と区切り（; | & 括弧）で語に分けるだけにする（判定は Bash の 1 か所に置き、読むだけのコマンドは通すため）。
 *
 * @param {string} command コマンドの全文
 * @returns {Decision | null} 判定。通すときは null
 */
function judgePowerShell(command) {
  const words = command.split(/[\s;|&(){}"']+/).filter((word) => word !== '')
  const found = words.some((word, index) => GIT_OR_GH_PROGRAM.test(word) && isOperation(words.slice(index + 1)))
  if (!found) return null
  return deny(
    'PowerShell のツールでは、git・gh の push、commit、tag、merge、api、release、hooksPath を含むコマンドを実行しない。' +
      '同じコマンドを Bash のツールで実行し直す（Bash では、Claude Code の hook がコマンドの全文を読んで判定する）。',
  )
}

/**
 * hook の入力（JSON の文字列）を判定し、標準出力に出力する文字列を返す。
 * 読めない入力は通す（Claude Code は常に JSON を渡すので、読めないときに全てのツールを止めないため）。
 * 先頭の BOM は除いてから読む。Windows PowerShell 5.1 はパイプで BOM を付け、そのままでは読めずに通してしまうため。
 *
 * @param {string} text 標準入力の全文
 * @param {Deps} [deps] 外部の処理
 * @param {typeof judgeCommand} [judge] 判定の関数
 * @returns {string} 出力する JSON。通すときは空文字
 */
export function handleInput(text, deps = createDeps(), judge = judgeCommand) {
  let input
  try {
    input = JSON.parse(text.startsWith('﻿') ? text.slice(1) : text)
  } catch {
    return ''
  }
  if (input?.tool_name !== 'Bash' && input?.tool_name !== 'PowerShell') return ''
  const command = input.tool_input?.command
  if (typeof command !== 'string' || command === '') return ''
  if (input.tool_name === 'PowerShell') return output(judgePowerShell(command))
  const cwd = typeof input.cwd === 'string' && input.cwd !== '' ? input.cwd : process.cwd()
  let decision
  try {
    decision = judge(command, cwd, deps)
  } catch (error) {
    decision = /\b(git|gh)\b/i.test(command)
      ? deny(
          `Claude Code の hook（scripts/dev/hooks/agent-guard.mjs）が、git か gh を含むコマンドの判定で失敗した（${error instanceof Error ? error.message : String(error)}）。` +
            '判定できない git・gh のコマンドは、force push や許可のないマージを通さないために拒否する。コマンドを単純な形に分けて実行し直し、通らなければ hook の不具合として報告する。',
        )
      : null
  }
  return output(decision)
}

/**
 * 判定を、標準出力に出力する文字列にする。
 *
 * @param {Decision | null} decision 判定。通すときは null
 * @returns {string} 出力する JSON。通すときは空文字
 */
function output(decision) {
  if (decision === null) return ''
  return JSON.stringify({ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: decision.decision, permissionDecisionReason: decision.reason } })
}

/**
 * 標準入力を読み、判定を標準出力に出力する。
 */
async function main() {
  process.stdin.setEncoding('utf8')
  let text = ''
  for await (const chunk of process.stdin) text += chunk
  const output = handleInput(text)
  if (output !== '') process.stdout.write(output)
}

if (import.meta.main) {
  // 標準入力を読めないなどの失敗では、判定できるコマンドがないので通す（終了コードを 0 以外にしない）
  main().catch(() => {
    process.exitCode = 0
  })
}
