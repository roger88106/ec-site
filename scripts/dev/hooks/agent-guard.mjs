// ============================================================================
// Claude Code の PreToolUse の hook（.claude/settings.json から呼ぶ）。
// 同じディレクトリの pre-commit・commit-msg は git の hook で、これとは別のもの（git でなく Claude Code が実行する）。
//
// エージェントが Bash で実行しようとするコマンドの全文を読み、例外のない規則に反するものを拒否する。
// - push 済みの履歴の書き換え：git push の force（-f、--force、--force-with-lease、--force-if-includes、--mirror、+ の refspec）
// - commit の前の検査の省略：git commit・git push の --no-verify、git commit の -n、git -c core.hooksPath=…
// - pre-commit hook を置いていないリポジトリでの git commit（git rev-parse --git-path hooks/pre-commit のファイルが、
//   scripts/dev/hooks/pre-commit を呼んでいなければ拒否する。作業ツリーでは共通の .git の hooks を見る）
// - gh api によるマージ、gh pr merge の --admin と、--match-head-commit のない gh pr merge
// 利用者の許可が必要な操作では、確認を求める（利用者に確認の画面を表示させる）。
// - マージ：gh pr merge のうち、利用者の許可なしにマージしてよい条件（AGENTS.md の Git の表）を満たさないもの。
//   条件は gh で pull request を読んで調べ、読めないときも確認を求める。未解決の指摘が 0 かは読めないので見ない（文章の規則のまま）
// - tag：git tag での作成、git push の --tags・--follow-tags・tag の refspec、gh release create
// - ruleset：ruleset とブランチの保護を変える gh api（GET 以外のリクエスト）
// permission の規則はコマンドの先頭の語でしか判定できず、フルパスの git・gh や、&& でつないだ後ろのコマンドに効かないので、hook で全文を読む。
//
// PowerShell のツールのコマンドは書き方を読まず、git か gh と、操作の語（push、commit、tag、merge、api、release、hooksPath）を
// 語として含めば拒否し、Bash のツールで実行し直すよう返す。それ以外（git status など）は通す。
//
// 入力：標準入力の JSON（tool_name、tool_input.command、cwd など）。Bash と PowerShell 以外のツールは何もせずに通す。
// 出力：拒否か確認を求めるときは hookSpecificOutput の permissionDecision を標準出力に出す。通すときは何も出さない。終了コードは常に 0。
// 対象のディレクトリは、入力の cwd を起点に、コマンドの中の cd、env -C、git -C で決め、前に付けた GIT_DIR・GIT_WORK_TREE・GH_REPO も見る
// （$CLAUDE_PROJECT_DIR は、作業ツリーのセッションでもメインの作業ツリーを指すことがあるので使わない）。
// $(…) とバッククォートの中のコマンド、timeout・env・xargs などを前に付けたコマンド、
// シェルの予約語（do、then、{、! など）の後のコマンドも判定する。
// 判定の中の例外では、git か gh を含むコマンドは拒否し、含まないコマンドは通す
// （判定の不具合で force push や許可のないマージを通さず、かつ git・gh と関係のない Bash まで止めないため）。
// ============================================================================
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import path from 'node:path'
import { isMain } from '../../ci/lib/is-main.mjs'
import { applyDependabotTitle, isDependabot, judgeRisk, parseRiskConfig, readBodyRisk } from '../../ci/risk.mjs'

/** git の呼び出しの時間切れ（ミリ秒）。hook 全体の時間切れより短くする */
export const GIT_TIMEOUT_MS = 5000

/**
 * gh の 1 回の呼び出しの時間切れ（ミリ秒）。pull request を読む GraphQL の 1 回のリクエストは、ふだん 1〜3 秒で終わるので、余裕を見て 10 秒にする。
 */
const GH_TIMEOUT_MS = 10000

/**
 * 1 つのコマンドを判定するとき、gh に使える時間の合計（ミリ秒）。hook 全体の時間切れ（.claude/settings.json の timeout）から、
 * node の起動と git の呼び出しの分（約 10 秒）を残す。使い切った後のマージは、読まずに確認を求める。
 */
export const GH_BUDGET_MS = 20000

/** 高の経路の一覧（Dependabot の pull request の危険度の判定に使う） */
const RISK_CONFIG_FILE = new URL('../../ci/risk.json', import.meta.url)

/** 利用者の許可なしにマージしてよい向き先のブランチ */
const MERGE_BASE = 'develop'

/** pull request の本文の危険度を、本文の判定と照合する CI の検査の名前 */
const RISK_CHECK = '危険度の照合'

/** 機能の spec の issue に付けるラベル */
const FEATURE_LABEL = '種類:機能'

/**
 * 通ったとみなす check run の結論。neutral は、GitHub が必須の検査で成功と同じに扱う結論で、
 * Dependabot の pull request の CodeQL が警告なしで返すので、成功に含める。
 */
const PASSING_CONCLUSIONS = ['SUCCESS', 'SKIPPED', 'NEUTRAL']

/** 終わっていないとみなす commit status の状態 */
const PENDING_STATES = ['PENDING', 'EXPECTED']

/**
 * マージの条件を読む GraphQL の問い合わせ。1 回のリクエストで、pull request、変更のファイル、閉じる issue のラベル、head の commit の検査を読む。
 * 一覧はどれも 1 ページ（100 件）だけ読み、続きがあれば条件を確認できないとする。
 */
const PULL_REQUEST_QUERY = `query($owner: String!, $name: String!, $number: Int!) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      state baseRefName headRefOid title body changedFiles
      author { __typename login }
      files(first: 100) { nodes { path } pageInfo { hasNextPage } }
      closingIssuesReferences(first: 100) {
        nodes { number labels(first: 100) { nodes { name } pageInfo { hasNextPage } } }
        pageInfo { hasNextPage }
      }
      commits(last: 1) {
        nodes { commit { statusCheckRollup { contexts(first: 100) {
          nodes {
            __typename
            ... on CheckRun { name status conclusion startedAt checkSuite { workflowRun { workflow { name } } } }
            ... on StatusContext { context state createdAt }
          }
          pageInfo { hasNextPage }
        } } } }
      }
    }
  }
}`

/**
 * gh の選択肢の読み方。shortNames は短い選択肢を長い名前にする対応、valueLong は値を取る長い選択肢（短い選択肢も長い名前で書く）。
 * 値を取らない選択肢は、値を "true" として読む。
 */
const GH_PR_OPTIONS = {
  shortNames: { b: 'body', F: 'body-file', t: 'subject', A: 'author-email', R: 'repo', d: 'delete-branch', m: 'merge', r: 'rebase', s: 'squash' },
  valueLong: ['body', 'body-file', 'subject', 'author-email', 'match-head-commit', 'repo'],
}

/** gh pr merge の選択肢のうち、自動のマージの解除と一緒に書いても解除だけになるもの */
const DISABLE_AUTO_OPTIONS = ['disable-auto', 'repo']

/** gh release の選択肢 */
const GH_RELEASE_OPTIONS = {
  shortNames: { R: 'repo', n: 'notes', F: 'notes-file', t: 'title', d: 'draft', p: 'prerelease' },
  valueLong: ['repo', 'notes', 'notes-file', 'title', 'target', 'discussion-category', 'notes-start-tag', 'latest', 'fail-on-no-commits'],
}

/** gh api の選択肢 */
const GH_API_OPTIONS = {
  shortNames: { X: 'method', H: 'header', f: 'raw-field', F: 'field', q: 'jq', t: 'template', p: 'preview', i: 'include' },
  valueLong: ['method', 'header', 'raw-field', 'field', 'input', 'jq', 'template', 'cache', 'preview', 'hostname'],
}

/** gh api で、-X がなくてもリクエストを POST にする選択肢（本文を送る） */
const GH_API_BODY_OPTIONS = ['raw-field', 'field', 'input']

/** gh api のパスのうち、pull request をマージするもの */
const API_MERGE_PATH = /(^|\/)pulls\/[^/]+\/merge\/?(\?|$)/

/** gh api のパスのうち、ruleset とブランチの保護を変えうるもの */
const API_RULESET_PATHS = [/(^|\/)rulesets(\/|\?|$)/, /(^|\/)branches\/.+\/protection(\/|\?|$)/]

/** pull request の URL（github.com のもの） */
const PULL_REQUEST_URL = /^https?:\/\/github\.com\/([^/]+)\/([^/]+)\/pull\/(\d+)(?:[/?#].*)?$/i

/** 置いた pre-commit hook が呼ぶ script（開発環境の起動 第 2 節の置き方） */
const PRE_COMMIT_SCRIPT = 'scripts/dev/hooks/pre-commit'

/**
 * 前に付けて、後ろの語を別のコマンドとして実行させるコマンドと、その選択肢。
 * valueShort・valueLong は値を次の語（短い選択肢は同じ語の続きも）に取るもの、optionalShort は値を同じ語の続きにだけ取るもの、
 * operands は選択肢の後に読む値の数（timeout の時間）。
 */
const PREFIX_COMMANDS = {
  builtin: { valueShort: '', valueLong: [] },
  command: { valueShort: '', valueLong: [] },
  exec: { valueShort: 'a', valueLong: [] },
  nohup: { valueShort: '', valueLong: [] },
  time: { valueShort: 'fo', valueLong: ['format', 'output'] },
  env: { valueShort: 'uCS', valueLong: ['unset', 'chdir', 'split-string'] },
  timeout: { valueShort: 'sk', valueLong: ['signal', 'kill-after'], operands: 1 },
  nice: { valueShort: 'n', valueLong: ['adjustment'] },
  stdbuf: { valueShort: 'ioe', valueLong: ['input', 'output', 'error'] },
  xargs: {
    valueShort: 'ILnPsdEa',
    optionalShort: 'ile',
    valueLong: ['arg-file', 'delimiter', 'eof', 'max-args', 'max-chars', 'max-lines', 'max-procs', 'process-slot-var'],
  },
}

/**
 * コマンドの前に来て、後ろの語をコマンドとして実行させるシェルの予約語（if・for・while の本体や条件、{ } の中、! の否定）。
 * `;` で区切った後の `do git push -f` のような形で、git・gh を語の先頭から見分けるために外す。
 */
const SHELL_KEYWORDS = ['if', 'then', 'else', 'elif', 'do', 'while', 'until', '!', '{']

/** 前に付けた環境変数のうち、対象のリポジトリを替えるもの（pre-commit hook を調べる rev-parse に渡す） */
const REPOSITORY_ENV = ['GIT_DIR', 'GIT_WORK_TREE']

/** 前に付けた環境変数のうち、gh の対象のリポジトリを替えるもの */
const GH_REPOSITORY_ENV = 'GH_REPO'

/** force push の長い選択肢（--mirror は、リモートの参照を force で更新し、リモートにしかない参照を消す） */
const FORCE_OPTIONS = ['force', 'force-with-lease', 'force-if-includes', 'mirror']

/** 検査を省略する長い選択肢 */
const NO_VERIFY_OPTIONS = ['no-verify']

/** git push の選択肢のうち、値を次の語に取るもの */
const PUSH_OPTIONS = { valueShort: 'o', optionalShort: '', valueLong: ['repo', 'receive-pack', 'exec', 'push-option'] }

/**
 * git tag の選択肢のうち、値を次の語（または同じ語の続き）に取るもの。
 * -n は値を同じ語の続きにだけ取るが、一覧の形かを見分けるために選択肢として数えたいので、optionalShort に入れない（続きの数字も選択肢の文字として読むが、判定に使わない）。
 */
const TAG_OPTIONS = {
  valueShort: 'mFu',
  optionalShort: '',
  valueLong: ['message', 'file', 'local-user', 'contains', 'no-contains', 'points-at', 'merged', 'no-merged', 'sort', 'format', 'cleanup', 'trailer'],
}

/** git tag を、作成でなく一覧・削除・検証にする短い選択肢 */
const TAG_LIST_SHORTS = ['l', 'd', 'v', 'n']

/** git tag を、作成でなく一覧・削除・検証にする長い選択肢（--contains などは一覧の形になる） */
const TAG_LIST_LONGS = ['list', 'delete', 'verify', 'contains', 'no-contains', 'points-at', 'merged', 'no-merged']

/** git push で tag を送る長い選択肢 */
const TAG_PUSH_OPTIONS = ['tags', 'follow-tags']

/** git commit の選択肢のうち、値を次の語（または同じ語の続き）に取るもの */
const COMMIT_OPTIONS = {
  valueShort: 'mFCct',
  // -u と -S は、値を同じ語の続きにだけ取る
  optionalShort: 'uS',
  valueLong: ['message', 'file', 'reuse-message', 'reedit-message', 'fixup', 'squash', 'template', 'author', 'date', 'cleanup', 'trailer', 'pathspec-from-file'],
}

/** force push を拒否する理由と代わりの手順 */
const FORCE_REASON =
  'push 済みの履歴を書き換えないため、force push（-f、--force、--force-with-lease、--force-if-includes、--mirror、+ で始まる refspec）は使えない（AGENTS.md の Git の表）。' +
  'push 済みの commit を直すときは、修正を新しい commit にして、force を付けずに push する。'

/** 検査の省略を拒否する理由と代わりの手順 */
const NO_VERIFY_REASON =
  'commit の前の検査を省略しないため、--no-verify（git commit の -n を含む）は使えない（docs/guides/setup.md 第 2 節の hook）。' +
  'hook で止まったときは、表示された問題を直して、--no-verify を付けずに実行し直す。'

/** hook の置き場所の変更を拒否する理由と代わりの手順 */
const HOOKS_PATH_REASON =
  'commit の前の検査を実行させるため、git -c core.hooksPath=…（--config-env を含む）で hook の置き場所を替えることはできない。' +
  'core.hooksPath を指定せずに実行する。'

/** gh api のマージを拒否する理由と代わりの手順 */
const API_MERGE_REASON =
  'マージは gh api（…/pulls/<番号>/merge）でなく、gh pr merge <番号> --match-head-commit <SHA> で行う（AGENTS.md の Git の表）。' +
  '承認を求めたときの head の commit を指定して実行し直す。'

/** --admin を拒否する理由と代わりの手順 */
const ADMIN_REASON =
  'gh pr merge の --admin は、ruleset の検査を飛ばしてマージするので使えない。' + '--admin を外し、gh pr merge <番号> --match-head-commit <SHA> で実行し直す。'

/** --match-head-commit のないマージを拒否する理由と代わりの手順 */
const NO_MATCH_HEAD_REASON =
  'マージは、承認を求めたとき（CI を確認したとき）の head の commit を --match-head-commit で指定して行う（AGENTS.md の Git の表）。' +
  'gh pr merge <番号> --match-head-commit <SHA> で実行し直す。'

/** 確認の画面で利用者に示す、承認してよいときの説明 */
const APPROVE_ONLY_IF_ALLOWED = '利用者が会話でこの操作を許可したときだけ承認する。'

/** tag の作成と push で確認を求める理由 */
const TAG_REASON = `tag の作成と push（gh release create を含む）は、利用者の許可が必要（AGENTS.md の Git の表）。${APPROVE_ONLY_IF_ALLOWED}`

/** ruleset の変更で確認を求める理由 */
const RULESET_REASON = `ruleset（ブランチの保護を含む）の変更は、利用者の許可が必要（docs/guides/branches.md 第 5 節）。${APPROVE_ONLY_IF_ALLOWED}`

/**
 * @typedef {object} Decision hook の判定
 * @property {'deny' | 'ask'} decision 拒否するか、利用者に確認を求めるか
 * @property {string} reason 理由。拒否はエージェントに返す代わりの手順を、確認は利用者が承認してよいときを含める
 */

/**
 * @typedef {object} CommandResult 外部のコマンドの結果（spawnSync の結果の一部）
 * @property {number | null} status 終了コード。時間切れなどで終わらなければ null
 * @property {string} stdout 標準出力
 * @property {string} [stderr] 標準エラー
 * @property {Error & { code?: string }} [error] 起動の失敗や時間切れ（code が ETIMEDOUT）
 */

/**
 * @typedef {object} Deps 差し替えられる外部の処理
 * @property {(args: string[], cwd: string, env?: Record<string, string>) => CommandResult} runGit git を実行する（env は加える環境変数）
 * @property {(file: string) => string | null} readText ファイルを読む。なければ null
 * @property {(args: string[], options: { cwd: string, timeout: number }) => CommandResult} runGh gh を実行する（timeout はミリ秒）
 */

/**
 * @typedef {object} PathOptions パスの解き方
 * @property {string} [platform] OS（`process.platform` の値）。なければ実行している OS
 * @property {string} [home] ホームのディレクトリ。なければ実行している利用者のもの
 */

/**
 * 拒否の判定を作る。
 *
 * @param {string} reason 理由と代わりの手順
 * @returns {Decision} 拒否の判定
 */
function deny(reason) {
  return { decision: 'deny', reason }
}

/**
 * 確認を求める判定を作る。
 *
 * @param {string} reason 理由と、利用者が承認してよいとき
 * @returns {Decision} 確認を求める判定
 */
function ask(reason) {
  return { decision: 'ask', reason }
}

/**
 * ヒアドキュメントの本文を読み飛ばす。
 *
 * @param {string} command コマンドの全文
 * @param {number} start 本文の始まり（`<<` のある行の次の行の先頭）
 * @param {{ delimiter: string, stripTabs: boolean }[]} heredocs その行で始まったヒアドキュメント
 * @returns {number} 最後の本文の、終わりの行の次の位置
 */
function skipHeredocs(command, start, heredocs) {
  let position = start
  for (const { delimiter, stripTabs } of heredocs) {
    while (position < command.length) {
      const newline = command.indexOf('\n', position)
      const end = newline < 0 ? command.length : newline
      let line = command.slice(position, end).replace(/\r$/, '')
      if (stripTabs) line = line.replace(/^\t+/, '')
      position = newline < 0 ? command.length : newline + 1
      if (line === delimiter) break
    }
  }
  return position
}

/**
 * コマンドの文字列を、1 つずつのコマンドの語の並びに分ける（bash の書き方の、判定に必要な範囲だけを読む）。
 * `&&`・`||`・`;`・`|`・`&`・改行・括弧で区切り、引用符の中は区切らない。
 * コメント、リダイレクトとその先のファイル、ヒアドキュメントの本文は、語に含めない。
 * `$(…)` とバッククォートの中は、二重引用符の中でも、別のコマンドとして同じ読み方で読み、外のコマンドより前に並べる
 * （外の語には `$(…)`・`` `…` `` と書く）。
 *
 * @param {string} command コマンドの全文
 * @returns {string[][]} コマンドごとの語（引用符と \ を外したもの）
 * @throws {Error} 引用符、`$(…)`、バッククォートが閉じていないとき
 */
export function splitCommands(command) {
  const commands = []
  tokenize(command, 0, null, commands)
  return commands
}

/**
 * コマンドの文字列を、`start` から、`closer` で閉じるまで（なければ終わりまで）読み、コマンドを `commands` に加える。
 *
 * @param {string} command コマンドの全文
 * @param {number} start 読み始める位置
 * @param {')' | '`' | null} closer 閉じる文字。`$(` の中は `)`、バッククォートの中は `` ` ``、一番外は null
 * @param {string[][]} commands 読んだコマンドを加える先
 * @returns {number} 閉じる文字の位置。一番外では文字列の長さ
 * @throws {Error} 引用符、`$(…)`、バッククォートが閉じていないとき
 */
function tokenize(command, start, closer, commands) {
  let words = []
  let word = ''
  let inWord = false
  let dropNextWord = false
  let heredocs = []
  // 括弧（サブシェル）の深さ。$( の中では、深さが 0 の ) で閉じる
  let depth = 0
  let i = start
  const n = command.length
  const unclosed = (what) => new Error(`閉じていない${what}がある`)
  /** `$(` か `` ` `` の中を別のコマンドとして読み、外の語には印を残す。閉じる文字の次の位置を返す */
  const substitute = (at, inner) => {
    const end = tokenize(command, at, inner, commands)
    word += inner === ')' ? '$(…)' : '`…`'
    inWord = true
    return end + 1
  }
  const endWord = () => {
    if (!inWord) return
    if (dropNextWord) dropNextWord = false
    else words.push(word)
    word = ''
    inWord = false
  }
  const endCommand = () => {
    endWord()
    dropNextWord = false
    if (words.length > 0) commands.push(words)
    words = []
  }
  // 1 文字ずつ、引用符・区切り・リダイレクト・ヒアドキュメントを見分けて語を作る
  while (i < n) {
    const c = command[i]
    if ((closer === '`' && c === '`') || (closer === ')' && c === ')' && depth === 0)) {
      endCommand()
      return i
    }
    if (c === "'") {
      const end = command.indexOf("'", i + 1)
      if (end < 0) throw unclosed("'")
      word += command.slice(i + 1, end)
      inWord = true
      i = end + 1
    } else if (c === '"') {
      inWord = true
      i++
      while (command[i] !== '"') {
        if (i >= n) throw unclosed('"')
        // 二重引用符の中の \ は、" \ $ ` 改行の前でだけ働く
        if (command[i] === '\\' && i + 1 < n && '"\\$`\n'.includes(command[i + 1])) {
          if (command[i + 1] !== '\n') word += command[i + 1]
          i += 2
        } else if (command.startsWith('$(', i) && command[i + 2] !== '(') {
          i = substitute(i + 2, ')')
        } else if (command[i] === '`') {
          i = substitute(i + 1, '`')
        } else {
          word += command[i]
          i++
        }
      }
      i++
    } else if (command.startsWith('$(', i) && command[i + 2] !== '(') {
      i = substitute(i + 2, ')')
    } else if (c === '`') {
      i = substitute(i + 1, '`')
    } else if (c === '\\') {
      if (i + 1 < n && command[i + 1] !== '\n') {
        word += command[i + 1]
        inWord = true
      }
      i += 2
    } else if (c === '#' && !inWord) {
      while (i < n && command[i] !== '\n') i++
    } else if (c === ' ' || c === '\t' || c === '\r') {
      endWord()
      i++
    } else if (c === '\n') {
      endCommand()
      i = skipHeredocs(command, i + 1, heredocs)
      heredocs = []
    } else if (command.startsWith('<<', i) && !command.startsWith('<<<', i)) {
      endWord()
      i += 2
      const stripTabs = command[i] === '-'
      if (stripTabs) i++
      while (command[i] === ' ' || command[i] === '\t') i++
      let delimiter = ''
      while (i < n && !/[\s;&|()<>]/.test(command[i])) {
        if (!`"'\\`.includes(command[i])) delimiter += command[i]
        i++
      }
      heredocs.push({ delimiter, stripTabs })
    } else if (c === '<' || c === '>' || (c === '&' && command[i + 1] === '>')) {
      // 2>&1 の 2 のような、リダイレクトの前の数字は語でない
      if (inWord && /^\d+$/.test(word)) {
        word = ''
        inWord = false
      } else {
        endWord()
      }
      i += c === '&' ? 2 : 1
      while (command[i] === '>' || command[i] === '<' || command[i] === '|') i++
      if (command[i] === '&') {
        i++
        while (i < n && /[\d-]/.test(command[i])) i++
      } else {
        dropNextWord = true
      }
    } else if (';&|()'.includes(c)) {
      if (c === '(') depth++
      else if (c === ')' && depth > 0) depth--
      endCommand()
      i++
    } else {
      word += c
      inWord = true
      i++
    }
  }
  if (closer !== null) throw unclosed(closer === ')' ? ' $(' : ' `')
  endCommand()
  return n
}

/**
 * cd の先などのパスを、ディレクトリの絶対パスにする。
 * Windows では、Git Bash の `/c/…` の形をドライブのパスにする。`~` はホームにする。
 *
 * @param {string} base 起点のディレクトリ
 * @param {string} target パス
 * @param {PathOptions} [options] パスの解き方
 * @returns {string} 絶対パス
 */
export function resolveDir(base, target, { platform = process.platform, home = homedir() } = {}) {
  const lib = platform === 'win32' ? path.win32 : path.posix
  let value = target
  if (value === '~' || value.startsWith('~/')) value = lib.join(home, value.slice(2))
  if (platform === 'win32' && /^\/[a-zA-Z](\/|$)/.test(value)) value = `${value[1].toUpperCase()}:\\${value.slice(2)}`
  return lib.resolve(base, value)
}

/**
 * 前に付けたコマンドの選択肢を読み飛ばす。
 *
 * @param {string[]} words コマンドの語
 * @param {number} start 選択肢の始まりの位置
 * @param {{ valueShort: string, optionalShort?: string, valueLong: string[], operands?: number }} spec 選択肢
 * @returns {{ index: number, values: { option: string, value: string }[] }} 後ろのコマンドの位置と、値を取った選択肢
 */
function skipPrefixOptions(words, start, { valueShort, optionalShort = '', valueLong, operands = 0 }) {
  const values = []
  let i = start
  // 選択肢を、値を取るものは値と一緒に、位置の引数の手前まで読む
  while (i < words.length) {
    const word = words[i]
    if (word === '--') {
      i++
      break
    }
    if (word === '-') {
      i++
      continue
    }
    if (!word.startsWith('-')) break
    if (word.startsWith('--')) {
      const [name, ...value] = word.slice(2).split('=')
      if (value.length > 0) {
        values.push({ option: name, value: value.join('=') })
      } else if (valueLong.includes(name)) {
        values.push({ option: name, value: words[i + 1] ?? '' })
        i++
      }
    } else {
      for (let j = 1; j < word.length; j++) {
        const option = word[j]
        if (valueShort.includes(option)) {
          const rest = word.slice(j + 1)
          values.push({ option, value: rest !== '' ? rest : (words[i + 1] ?? '') })
          if (rest === '') i++
          break
        }
        if (optionalShort.includes(option)) break
      }
    }
    i++
  }
  return { index: Math.min(i + operands, words.length), values }
}

/**
 * コマンドの前に付けた環境変数の設定、シェルの予約語（do、then など）、後ろの語を実行させるコマンド（env、timeout など）を外す。
 * env -C はディレクトリを、env -S は語を替える。
 *
 * @param {string[]} raw コマンドの語
 * @param {string} dir 実行するディレクトリ
 * @param {PathOptions} options パスの解き方
 * @returns {{ words: string[], dir: string, env: Record<string, string>, ghRepo: string | null }} 実行するコマンドの語、ディレクトリ、
 *   対象のリポジトリを替える環境変数、gh の対象のリポジトリ（前に付けた GH_REPO。なければ null）
 */
function stripPrefixes(raw, dir, options) {
  let words = raw
  let current = dir
  const assigned = {}
  // 環境変数の設定、予約語、前に付けるコマンドがなくなるまで外す
  while (words.length > 0) {
    const assignment = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/s.exec(words[0])
    if (assignment) {
      assigned[assignment[1]] = assignment[2]
      words = words.slice(1)
      continue
    }
    if (SHELL_KEYWORDS.includes(words[0])) {
      words = words.slice(1)
      continue
    }
    const spec = PREFIX_COMMANDS[words[0]]
    if (!spec) break
    const { index, values } = skipPrefixOptions(words, 1, spec)
    let split = []
    for (const { option, value } of values) {
      if (words[0] === 'env' && (option === 'C' || option === 'chdir')) current = resolveDir(current, value, options)
      if (words[0] === 'env' && (option === 'S' || option === 'split-string')) split = splitCommands(value)[0] ?? []
    }
    words = [...split, ...words.slice(index)]
  }
  const env = {}
  for (const name of REPOSITORY_ENV) {
    if (assigned[name] !== undefined && assigned[name] !== '') env[name] = resolveDir(current, assigned[name], options)
  }
  const ghRepo = assigned[GH_REPOSITORY_ENV] ? assigned[GH_REPOSITORY_ENV] : null
  return { words, dir: current, env, ghRepo }
}

/**
 * コマンドの全文を、コマンドごとの語と、実行するディレクトリに分ける。
 * ディレクトリは入力の cwd から始め、cd・pushd の後のコマンドでは、その先にする。
 *
 * @param {string} command コマンドの全文
 * @param {string} cwd 入力の cwd
 * @param {PathOptions} [options] パスの解き方
 * @returns {{ words: string[], dir: string, env: Record<string, string>, ghRepo: string | null }[]} コマンドごとの語（前に付けた環境変数の設定などを除いたもの）、
 *   ディレクトリ、対象のリポジトリを替える環境変数、gh の対象のリポジトリ（前に付けた GH_REPO）
 */
export function parseCommands(command, cwd, options = {}) {
  let dir = resolveDir(process.cwd(), cwd, options)
  const commands = []
  for (const raw of splitCommands(command)) {
    const { words, dir: commandDir, env, ghRepo } = stripPrefixes(raw, dir, options)
    if (words.length === 0) continue
    commands.push({ words, dir: commandDir, env, ghRepo })
    if (words[0] === 'cd' || words[0] === 'pushd') {
      const target = words.slice(1).find((w) => !/^-[LPe@]+$/.test(w))
      // cd - は前のディレクトリに戻るが、前のディレクトリは追わないので、今のままにする
      if (target !== '-') dir = resolveDir(dir, target ?? '~', options)
    }
  }
  return commands
}

/**
 * @typedef {object} GitInvocation git の呼び出し
 * @property {string} dir git -C を当てた後の、実行するディレクトリ
 * @property {string[]} configs -c・--config-env で渡した設定
 * @property {string[]} pathOptions --git-dir・--work-tree の選択肢（そのまま git に渡せる形）
 * @property {string | null} subcommand サブコマンド
 * @property {string[]} args サブコマンドの後の語
 * @property {Record<string, string>} env 前に付けた、対象のリポジトリを替える環境変数（GIT_DIR・GIT_WORK_TREE）
 */

/**
 * コマンドが git の呼び出しなら、git の前の選択肢とサブコマンドを読む。フルパスと git.exe の形も git として読む。
 *
 * @param {{ words: string[], dir: string, env?: Record<string, string> }} command コマンドの語、ディレクトリ、対象のリポジトリを替える環境変数
 * @param {PathOptions} [options] パスの解き方
 * @returns {GitInvocation | null} git の呼び出し。git でなければ null
 */
export function parseGitInvocation({ words, dir, env = {} }, options = {}) {
  const program = words[0].split(/[\\/]/).pop()
  if (!/^git(\.exe)?$/i.test(program)) return null
  let current = dir
  const configs = []
  const pathOptions = []
  let i = 1
  // git の前の選択肢を、サブコマンドまで読む
  for (; i < words.length && words[i].startsWith('-'); i++) {
    const word = words[i]
    if (word === '-C') {
      current = resolveDir(current, words[++i] ?? '', options)
    } else if (word === '-c' || word === '--config-env') {
      configs.push(words[++i] ?? '')
    } else if (word.startsWith('--config-env=')) {
      configs.push(word.slice('--config-env='.length))
    } else if (word.startsWith('-c')) {
      configs.push(word.slice(2))
    } else if (word === '--git-dir' || word === '--work-tree') {
      pathOptions.push(word, words[++i] ?? '')
    } else if (word.startsWith('--git-dir=') || word.startsWith('--work-tree=')) {
      pathOptions.push(word)
    } else if (word === '--namespace' || word === '--super-prefix') {
      i++
    }
  }
  return { dir: current, configs, pathOptions, subcommand: words[i] ?? null, args: words.slice(i + 1), env }
}

/**
 * サブコマンドの後の語を、長い選択肢・短い選択肢・位置の引数に分ける。選択肢の値は、どれにも含めない。
 *
 * @param {string[]} args サブコマンドの後の語
 * @param {{ valueShort: string, optionalShort: string, valueLong: string[] }} spec 値を取る選択肢
 * @returns {{ longs: string[], shorts: string[], positionals: string[] }} 長い選択肢の名前（-- と = の後を除く）、短い選択肢の文字、位置の引数
 */
function scanOptions(args, { valueShort, optionalShort, valueLong }) {
  const longs = []
  const shorts = []
  const positionals = []
  let rest = false
  // 選択肢の値を飛ばしながら、選択肢と位置の引数に分ける
  for (let i = 0; i < args.length; i++) {
    const arg = args[i]
    if (rest || arg === '-' || !arg.startsWith('-')) {
      positionals.push(arg)
    } else if (arg === '--') {
      rest = true
    } else if (arg.startsWith('--')) {
      const name = arg.slice(2).split('=')[0]
      longs.push(name)
      if (!arg.includes('=') && valueLong.includes(name)) i++
    } else {
      // -an のような短い選択肢の組み合わせ。値を取る文字の後ろは値なので読まない
      for (let j = 1; j < arg.length; j++) {
        const option = arg[j]
        if (valueShort.includes(option)) {
          if (j === arg.length - 1) i++
          break
        }
        if (optionalShort.includes(option)) break
        shorts.push(option)
      }
    }
  }
  return { longs, shorts, positionals }
}

/**
 * 長い選択肢の名前が、対象の選択肢のどれかか。git は一意な前方の一致の略記も受け付けるので、3 文字以上の略記も当てる。
 *
 * @param {string} name 選択肢の名前
 * @param {string[]} targets 対象の選択肢の名前
 * @returns {boolean} 当たれば true
 */
function matchesLong(name, targets) {
  return targets.some((target) => name === target || (name.length >= 3 && target.startsWith(name)))
}

/**
 * hook の置き場所を替える設定を拒否する。
 *
 * @param {GitInvocation} git git の呼び出し
 * @returns {Decision | null} 判定
 */
function judgeHooksPath(git) {
  return git.configs.some((config) => /^core\.hookspath(=|$)/i.test(config)) ? deny(HOOKS_PATH_REASON) : null
}

/**
 * git push の force と --no-verify を拒否する。dry-run（-n）は通す。
 *
 * @param {GitInvocation} git git の呼び出し
 * @returns {Decision | null} 判定
 */
function judgePush(git) {
  if (git.subcommand !== 'push') return null
  const { longs, shorts, positionals } = scanOptions(git.args, PUSH_OPTIONS)
  if (longs.some((name) => matchesLong(name, FORCE_OPTIONS)) || shorts.includes('f') || positionals.some((p) => p.startsWith('+'))) {
    return deny(FORCE_REASON)
  }
  return longs.some((name) => matchesLong(name, NO_VERIFY_OPTIONS)) ? deny(NO_VERIFY_REASON) : null
}

/**
 * git commit の --no-verify と -n を拒否する。
 *
 * @param {GitInvocation} git git の呼び出し
 * @returns {Decision | null} 判定
 */
function judgeCommitOptions(git) {
  if (git.subcommand !== 'commit') return null
  const { longs, shorts } = scanOptions(git.args, COMMIT_OPTIONS)
  return longs.some((name) => matchesLong(name, NO_VERIFY_OPTIONS)) || shorts.includes('n') ? deny(NO_VERIFY_REASON) : null
}

/**
 * git commit のとき、対象のリポジトリに pre-commit hook が置かれていなければ拒否する。
 * 置き場所は git rev-parse --git-path で求める（作業ツリーでは共通の .git の hooks になる）。
 * 対象のリポジトリを決められないときも拒否する（hook のないリポジトリの commit を通さないため。commit 自体も失敗する見込みが高い）。
 *
 * @param {GitInvocation} git git の呼び出し
 * @param {Deps} deps 外部の処理
 * @returns {Decision | null} 判定
 */
function judgePreCommitHook(git, deps) {
  if (git.subcommand !== 'commit') return null
  const result = deps.runGit([...git.pathOptions, 'rev-parse', '--git-path', 'hooks/pre-commit'], git.dir, git.env)
  const relative = result?.status === 0 ? result.stdout.trim() : ''
  if (relative === '') {
    return deny(
      `commit の前の検査の hook を調べるため、対象のリポジトリを決めようとしたが、${git.dir} で git rev-parse --git-path hooks/pre-commit が失敗した（リポジトリの外か、パスを読めない）。` +
        'リポジトリの中のディレクトリを、cd と git -C に変数やコマンドの置き換えを使わずに直接書いて、実行し直す。',
    )
  }
  const hook = path.resolve(git.dir, relative)
  const text = deps.readText(hook)
  if (text?.includes(PRE_COMMIT_SCRIPT)) return null
  const state = text === null ? 'がない' : `が ${PRE_COMMIT_SCRIPT} を呼んでいない`
  return deny(
    `commit の前の検査を通すため、git の pre-commit hook を置いていないリポジトリでは commit できない（${hook} ${state}）。` +
      `docs/guides/setup.md 第 2 節（hook の置き方）のとおり、このファイルに ${PRE_COMMIT_SCRIPT} を呼ぶ 1 行を書いてから commit する。`,
  )
}

/**
 * git tag の、tag の作成で確認を求める。一覧（引数なし、-l、--contains など）、削除（-d）、検証（-v）は通す
 * （削除はローカルの tag だけを消し、リモートの tag の削除は push の判定で見る）。
 *
 * @param {GitInvocation} git git の呼び出し
 * @returns {Decision | null} 判定
 */
function judgeTagCreate(git) {
  if (git.subcommand !== 'tag') return null
  const { longs, shorts, positionals } = scanOptions(git.args, TAG_OPTIONS)
  const listing = shorts.some((option) => TAG_LIST_SHORTS.includes(option)) || longs.some((name) => matchesLong(name, TAG_LIST_LONGS))
  return positionals.length > 0 && !listing ? ask(TAG_REASON) : null
}

/**
 * refspec の片側が tag を指すか。refs/tags/ を含めば tag、refs/ で始まらない名前は、対象のリポジトリにその名前の tag があれば tag とする。
 *
 * @param {string} name refspec の片側
 * @param {GitInvocation} git git の呼び出し
 * @param {Deps} deps 外部の処理
 * @returns {'tag' | 'unknown' | null} tag なら 'tag'、git で調べられなければ 'unknown'、tag でなければ null
 */
function refTagState(name, git, deps) {
  if (name.includes('refs/tags/')) return 'tag'
  if (name === '' || name.startsWith('refs/') || name.includes('*')) return null
  const result = deps.runGit([...git.pathOptions, 'show-ref', '--verify', '--quiet', `refs/tags/${name}`], git.dir, git.env)
  if (result?.status === 0) return 'tag'
  // 終了コード 1 はその tag がないこと。null（時間切れ）は調べられない
  return result?.status === null || result === undefined ? 'unknown' : null
}

/**
 * git push で tag を送る形（--tags、--follow-tags、`tag <名前>`、tag を指す refspec）で確認を求める。
 *
 * @param {GitInvocation} git git の呼び出し
 * @param {Deps} deps 外部の処理
 * @returns {Decision | null} 判定
 */
function judgeTagPush(git, deps) {
  if (git.subcommand !== 'push') return null
  const { longs, positionals } = scanOptions(git.args, PUSH_OPTIONS)
  if (longs.some((name) => matchesLong(name, TAG_PUSH_OPTIONS))) return ask(TAG_REASON)
  // 最初の位置の引数はリモート（--repo で指定したときは全てが refspec）
  const refspecs = longs.includes('repo') ? positionals : positionals.slice(1)
  if (refspecs.some((refspec, index) => refspec === 'tag' && index + 1 < refspecs.length)) return ask(TAG_REASON)
  for (const refspec of refspecs) {
    const sides = refspec.replace(/^\+/, '').split(':')
    for (const side of sides) {
      const state = refTagState(side, git, deps)
      if (state === 'tag') return ask(TAG_REASON)
      if (state === 'unknown') return ask(`push する名前（${side}）が tag かを、git で調べられなかった。${TAG_REASON}`)
    }
  }
  return null
}

/** git の呼び出しごとに当てる判定。拒否があれば、その時点で終える */
const GIT_JUDGES = [judgeHooksPath, judgePush, judgeCommitOptions, judgePreCommitHook, judgeTagCreate, judgeTagPush]

/**
 * @typedef {object} GhInvocation gh の呼び出し
 * @property {string} dir 実行するディレクトリ
 * @property {string | null} ghRepo 前に付けた GH_REPO
 * @property {string[]} args gh の後の語
 */

/**
 * コマンドが gh の呼び出しなら、gh の後の語を返す。フルパスと gh.exe の形も gh として読む。
 *
 * @param {{ words: string[], dir: string, ghRepo?: string | null }} command コマンドの語、ディレクトリ、前に付けた GH_REPO
 * @returns {GhInvocation | null} gh の呼び出し。gh でなければ null
 */
export function parseGhInvocation({ words, dir, ghRepo = null }) {
  const program = words[0].split(/[\\/]/).pop()
  if (!/^gh(\.exe)?$/i.test(program)) return null
  return { dir, ghRepo, args: words.slice(1) }
}

/**
 * gh の選択肢と位置の引数を読む。長い選択肢の略記は読まない（gh は略記を受け付けない）。
 *
 * @param {string[]} args 読む語
 * @param {{ shortNames: Record<string, string>, valueLong: string[] }} spec 選択肢の読み方
 * @returns {{ positionals: string[], options: { name: string, value: string }[] }} 位置の引数と、選択肢（値を取らないものは値が "true"）
 */
function parseGhArgs(args, { shortNames, valueLong }) {
  const positionals = []
  const options = []
  let rest = false
  // 値を取る選択肢は値と一緒に読み、残りを位置の引数にする
  for (let i = 0; i < args.length; i++) {
    const arg = args[i]
    if (rest || arg === '-' || !arg.startsWith('-')) {
      positionals.push(arg)
    } else if (arg === '--') {
      rest = true
    } else if (arg.startsWith('--')) {
      const [name, ...value] = arg.slice(2).split('=')
      if (value.length > 0) options.push({ name, value: value.join('=') })
      else if (valueLong.includes(name)) options.push({ name, value: args[++i] ?? '' })
      else options.push({ name, value: 'true' })
    } else {
      // -ds のような短い選択肢の組み合わせ。値を取る文字の後ろは値
      for (let j = 1; j < arg.length; j++) {
        const name = shortNames[arg[j]] ?? arg[j]
        if (valueLong.includes(name)) {
          const value = arg.slice(j + 1).replace(/^=/, '')
          options.push({ name, value: value !== '' ? value : (args[++i] ?? '') })
          break
        }
        options.push({ name, value: 'true' })
      }
    }
  }
  return { positionals, options }
}

/**
 * 選択肢の最後の値を返す。
 *
 * @param {{ name: string, value: string }[]} options 選択肢
 * @param {string} name 長い名前
 * @returns {string | null} 値。なければ null
 */
function optionValue(options, name) {
  return options.findLast((option) => option.name === name)?.value ?? null
}

/**
 * 値を取らない選択肢が有効か（`--auto=false` のような形は無効）。
 *
 * @param {{ name: string, value: string }[]} options 選択肢
 * @param {string} name 長い名前
 * @returns {boolean} 有効なら true
 */
function flagEnabled(options, name) {
  const value = optionValue(options, name)
  return value !== null && value !== 'false'
}

/**
 * gh api の判定。GET 以外のリクエストのうち、マージのパスへのものは拒否し、ruleset とブランチの保護へのものは確認を求める。GraphQL は見ない。
 *
 * @param {string[]} args api の後の語
 * @returns {Decision | null} 判定
 */
function judgeGhApi(args) {
  const { positionals, options } = parseGhArgs(args, GH_API_OPTIONS)
  const endpoint = positionals[0] ?? ''
  if (endpoint === 'graphql') return null
  // gh api は、-X がなくても本文を送る選択肢があれば POST にする
  const method = (optionValue(options, 'method') ?? (options.some((option) => GH_API_BODY_OPTIONS.includes(option.name)) ? 'POST' : 'GET')).toUpperCase()
  if (method === 'GET') return null
  if (API_MERGE_PATH.test(endpoint)) return deny(API_MERGE_REASON)
  return API_RULESET_PATHS.some((pattern) => pattern.test(endpoint)) ? ask(RULESET_REASON) : null
}

/**
 * `[HOST/]OWNER/REPO` の形のリポジトリを読む。github.com 以外のホストは読まない。
 *
 * @param {string} value リポジトリ
 * @returns {{ owner: string, name: string } | null} 所有者と名前。読めなければ null
 */
function parseRepository(value) {
  const parts = value.split('/')
  if (parts.length === 3 && parts[0].toLowerCase() !== 'github.com') return null
  const [owner, name] = parts.slice(-2)
  return parts.length >= 2 && parts.length <= 3 && owner && name ? { owner, name } : null
}

/**
 * @typedef {object} MergeTarget マージする pull request
 * @property {number} number 番号
 * @property {{ owner: string, name: string } | null} repository リポジトリ。null は対象のディレクトリで gh に決めさせる
 */

/**
 * gh pr merge の位置の引数と -R・GH_REPO から、マージする pull request を決める。
 *
 * @param {string | undefined} target 位置の引数
 * @param {string | null} repoOption -R・--repo の値
 * @param {string | null} ghRepo 前に付けた GH_REPO
 * @returns {MergeTarget | { error: string }} pull request か、決められない理由
 */
function resolveMergeTarget(target, repoOption, ghRepo) {
  const noTarget = { error: 'pull request を、github.com の番号か URL で指定していない（番号のない形は今のブランチの pull request を指す）' }
  if (target === undefined) return noTarget
  const url = PULL_REQUEST_URL.exec(target)
  if (url) return { number: Number(url[3]), repository: { owner: url[1], name: url[2] } }
  const number = /^#?(\d+)$/.exec(target)
  if (!number) return noTarget
  const repoValue = repoOption ?? ghRepo
  if (repoValue === null) return { number: Number(number[1]), repository: null }
  const repository = parseRepository(repoValue)
  return repository ? { number: Number(number[1]), repository } : { error: `リポジトリの指定（${repoValue}）を読めない` }
}

/**
 * gh の結果の失敗を、理由の文にする。
 *
 * @param {CommandResult} result gh の結果
 * @param {number} timeout 渡した時間切れ（ミリ秒）
 * @returns {string | null} 失敗の理由。成功なら null
 */
function describeGhFailure(result, timeout) {
  if (result.error?.code === 'ETIMEDOUT') return `gh が時間切れになった（${timeout / 1000} 秒）`
  if (result.status === 0) return null
  const message = (result.stderr ?? '').trim().split('\n')[0] || result.error?.message || `終了コード ${result.status}`
  return `gh が失敗した：${message}`
}

/**
 * gh api graphql で、マージの条件を読む。
 *
 * @param {MergeTarget} target マージする pull request
 * @param {string} dir 実行するディレクトリ（リポジトリを gh に決めさせるときに使う）
 * @param {Deps} deps 外部の処理
 * @param {number} timeout 時間切れ（ミリ秒）
 * @returns {{ pullRequest: object } | { error: string }} pull request（GraphQL の形）か、読めない理由
 */
function readPullRequest(target, dir, deps, timeout) {
  // リポジトリを指定しないときは、gh が対象のディレクトリのリモートから {owner}・{repo} を埋める（gh pr merge と同じ決め方）
  const repository = target.repository
    ? ['-f', `owner=${target.repository.owner}`, '-f', `name=${target.repository.name}`]
    : ['-F', 'owner={owner}', '-F', 'name={repo}']
  const args = ['api', 'graphql', '-f', `query=${PULL_REQUEST_QUERY}`, ...repository, '-F', `number=${target.number}`]
  let result
  try {
    result = deps.runGh(args, { cwd: dir, timeout })
  } catch (error) {
    return { error: `gh を実行できない：${error instanceof Error ? error.message : String(error)}` }
  }
  const failure = describeGhFailure(result, timeout)
  if (failure !== null) return { error: failure }
  try {
    const pullRequest = JSON.parse(result.stdout)?.data?.repository?.pullRequest
    if (pullRequest && typeof pullRequest === 'object') return { pullRequest }
  } catch {
    // 下で、読めない応答として返す
  }
  return { error: 'gh の応答（JSON）を読めない' }
}

/**
 * head の commit の検査を、同じ検査（check run は workflow と名前の組、commit status は context）ごとに、最も新しい実行だけにする。
 * GitHub は再実行の前の実行も同じ commit に残すので、古い失敗で判定しないため（gh pr checks も名前ごとに新しいものだけを見る）。
 * 同じ検査が重なり、時刻を読めない実行があるときは、どれが新しいか決められないので、その検査の実行を全て残す。
 *
 * @param {object} pullRequest pull request（GraphQL の形）
 * @returns {{ nodes: object[], undated: string[], truncated: boolean }} 残した検査、時刻を読めなかった検査の名前、一覧の続きがあるか
 */
function latestChecks(pullRequest) {
  const contexts = pullRequest.commits?.nodes?.[0]?.commit?.statusCheckRollup?.contexts
  const groups = new Map()
  for (const node of contexts?.nodes ?? []) {
    const key = JSON.stringify(
      node.__typename === 'CheckRun' ? ['run', node.checkSuite?.workflowRun?.workflow?.name ?? '', node.name] : ['status', node.context ?? node.__typename],
    )
    groups.set(key, [...(groups.get(key) ?? []), node])
  }
  const nodes = []
  const undated = []
  for (const group of groups.values()) {
    const times = group.map((node) => Date.parse((node.__typename === 'CheckRun' ? node.startedAt : node.createdAt) ?? ''))
    if (group.length > 1 && times.some(Number.isNaN)) {
      undated.push(group[0].name ?? group[0].context)
      nodes.push(...group)
      continue
    }
    // 時刻が同じ実行が複数あれば、どれも残す（どれが新しいか決められないため）
    const latest = Math.max(...times)
    nodes.push(...group.filter((_, index) => times[index] === latest || group.length === 1))
  }
  return { nodes, undated: [...new Set(undated)], truncated: Boolean(contexts?.pageInfo?.hasNextPage) }
}

/**
 * CI の検査が全て終わり、通っているかを調べる。
 *
 * @param {{ nodes: object[], undated: string[], truncated: boolean }} checks 同じ検査の最も新しい実行
 * @returns {string[]} 満たさない条件
 */
function checkProblems({ nodes, undated, truncated }) {
  if (nodes.length === 0) return ['CI の検査がない（まだ始まっていない）']
  const pending = []
  const failed = []
  // check run（GitHub Actions など）と commit status（外部のサービス）で、終わったか・通ったかの項目が違う
  for (const node of nodes) {
    if (node.__typename === 'CheckRun') {
      if (node.status !== 'COMPLETED') pending.push(node.name)
      else if (!PASSING_CONCLUSIONS.includes(node.conclusion)) failed.push(`${node.name}：${node.conclusion}`)
    } else if (node.__typename === 'StatusContext') {
      if (PENDING_STATES.includes(node.state)) pending.push(node.context)
      else if (node.state !== 'SUCCESS') failed.push(`${node.context}：${node.state}`)
    } else {
      failed.push(`読めない種類の検査：${node.__typename}`)
    }
  }
  const problems = []
  if (truncated) problems.push('CI の検査の一覧を全ては読めない（100 件を超える）')
  if (undated.length > 0) problems.push(`再実行で重なった CI の検査の時刻を読めず、最も新しい実行を決められない（${undated.join('、')}）`)
  if (pending.length > 0) problems.push(`CI の検査が終わっていない（${[...new Set(pending)].join('、')}）`)
  if (failed.length > 0) problems.push(`CI の検査が成功していない（${[...new Set(failed)].join('、')}）`)
  return problems
}

/**
 * 高の経路の一覧を読む。
 *
 * @returns {{ groups: { name: string, paths: string[] }[] }} 高の経路の一覧
 */
function loadRiskConfig() {
  return parseRiskConfig(readFileSync(RISK_CONFIG_FILE, 'utf8'))
}

/**
 * Dependabot の pull request の危険度を、変更のファイルと題から判定する（CI の危険度の照合と同じ関数を使う）。
 *
 * @param {object} pullRequest pull request（GraphQL の形）
 * @returns {string[]} 満たさない条件
 */
function dependabotProblems(pullRequest) {
  const files = pullRequest.files?.nodes?.map((node) => node.path) ?? []
  if (pullRequest.files?.pageInfo?.hasNextPage || files.length !== pullRequest.changedFiles) {
    return [`変更のファイルの一覧を全ては読めない（${pullRequest.changedFiles} 件）`]
  }
  let config
  try {
    config = loadRiskConfig()
  } catch (error) {
    return [`高の経路の一覧（scripts/ci/risk.json）を読めない：${error instanceof Error ? error.message : String(error)}`]
  }
  // Dependabot は spec.md を変えないので、spec の宣言を読む関数は常に null を返すものでよい
  const result = applyDependabotTitle(judgeRisk(files, config, () => null), pullRequest.title)
  if (result.level === '低') return []
  const reasons = [...result.hits.map((hit) => `${hit.name}：${hit.files.join('、')}`), result.dependabot]
  return [`Dependabot の更新の危険度が高（${reasons.join('、')}）`]
}

/**
 * Dependabot 以外の pull request の、危険度と、機能の spec でないことを調べる。
 *
 * @param {object} pullRequest pull request（GraphQL の形）
 * @param {object[]} checks 同じ検査の最も新しい実行
 * @returns {string[]} 満たさない条件
 */
function authoredProblems(pullRequest, checks) {
  const problems = []
  const bodyRisk = readBodyRisk(pullRequest.body ?? '')
  if ('error' in bodyRisk) problems.push(`本文の危険度を読めない（${bodyRisk.error}）`)
  else if (bodyRisk.value !== '低') problems.push(`本文の危険度が低でない（判定：${bodyRisk.value}）`)
  const riskChecks = checks.filter((node) => node.__typename === 'CheckRun' && node.name === RISK_CHECK)
  if (riskChecks.length === 0 || riskChecks.some((node) => node.status !== 'COMPLETED' || node.conclusion !== 'SUCCESS')) {
    problems.push(`検査「${RISK_CHECK}」が成功していない（ないか、成功以外）`)
  }
  const closing = pullRequest.closingIssuesReferences
  const issues = closing?.nodes ?? []
  if (closing?.pageInfo?.hasNextPage || issues.some((issue) => issue.labels?.pageInfo?.hasNextPage)) {
    problems.push('閉じる issue かそのラベルの一覧を全ては読めない（100 件を超える）')
  }
  if (issues.length === 0) problems.push('本文の Closes の issue がない（機能の spec でないことを確認できない）')
  const features = issues.filter((issue) => (issue.labels?.nodes ?? []).some((label) => label.name === FEATURE_LABEL))
  if (features.length > 0) problems.push(`閉じる issue にラベル「${FEATURE_LABEL}」がある（${features.map((issue) => `#${issue.number}`).join('、')}）`)
  return problems
}

/**
 * gh api graphql の作成者を、risk.mjs の作成者の形にする。GraphQL の Bot の login は `[bot]` を付けないので付ける。
 *
 * @param {{ __typename?: string, login?: string } | null | undefined} author 作成者
 * @returns {string | undefined} 作成者の名前
 */
function authorName(author) {
  if (author?.__typename === 'Bot' && author.login) return `${author.login.replace(/\[bot\]$/, '')}[bot]`
  return author?.login
}

/**
 * pull request が、利用者の許可なしにマージしてよい条件（未解決の指摘が 0 であることを除く）を満たすかを調べる。
 *
 * @param {object} pullRequest pull request（GraphQL の形）
 * @param {string} headCommit --match-head-commit の値
 * @returns {string[]} 満たさない条件。空なら全て満たす
 */
export function mergeProblems(pullRequest, headCommit) {
  const problems = []
  if (pullRequest.state !== 'OPEN') problems.push(`pull request が開いていない（${pullRequest.state}）`)
  if (pullRequest.baseRefName !== MERGE_BASE) problems.push(`向き先が ${MERGE_BASE} でない（${pullRequest.baseRefName}）`)
  if (String(pullRequest.headRefOid).toLowerCase() !== headCommit.toLowerCase()) {
    problems.push(`--match-head-commit の commit（${headCommit}）が、pull request の今の head の commit（${pullRequest.headRefOid}）と一致しない`)
  }
  const checks = latestChecks(pullRequest)
  problems.push(...checkProblems(checks))
  const dependabot = isDependabot({ author: authorName(pullRequest.author) })
  problems.push(...(dependabot ? dependabotProblems(pullRequest) : authoredProblems(pullRequest, checks.nodes)))
  return problems
}

/**
 * gh pr merge の判定。--admin と --match-head-commit のない形は拒否し、--auto は確認を求め、
 * pull request を読んで条件を全て満たせば通し、満たさないか読めなければ確認を求める。
 *
 * @param {string[]} args pr の後の語（merge を含む）
 * @param {GhInvocation} gh gh の呼び出し
 * @param {Deps} deps 外部の処理
 * @param {{ deadline: number }} budget gh に使える時間の期限（Date.now() の値）
 * @returns {Decision | null} 判定
 */
function judgeMerge(args, gh, deps, budget) {
  const { positionals, options } = parseGhArgs(args, GH_PR_OPTIONS)
  // 自動のマージの解除だけ（マージの選択肢を一緒に書かない形）は、マージしないので通す
  if (flagEnabled(options, 'disable-auto') && options.every((option) => DISABLE_AUTO_OPTIONS.includes(option.name))) return null
  if (flagEnabled(options, 'admin')) return deny(ADMIN_REASON)
  const headCommit = optionValue(options, 'match-head-commit')
  if (headCommit === null || headCommit === '') return deny(NO_MATCH_HEAD_REASON)
  const askMerge = (label, problems) =>
    ask(`利用者の許可なしにマージしてよい条件（AGENTS.md の Git の表）を満たさないので、確認を求める。pull request：${label}。満たさない条件：${problems.join('。')}。${APPROVE_ONLY_IF_ALLOWED}`)
  if (flagEnabled(options, 'auto')) return askMerge(positionals[1] ?? '（今のブランチ）', ['--auto（予約のマージ）は、マージの時点の条件を判定できない'])
  const target = resolveMergeTarget(positionals[1], optionValue(options, 'repo'), gh.ghRepo)
  if ('error' in target) return askMerge(positionals[1] ?? '（今のブランチ）', [target.error])
  const label = target.repository ? `${target.repository.owner}/${target.repository.name}#${target.number}` : `#${target.number}（${gh.dir} のリポジトリ）`
  const timeout = Math.min(GH_TIMEOUT_MS, budget.deadline - Date.now())
  if (timeout <= 0) return askMerge(label, ['pull request の情報を読めない（1 つのコマンドで gh に使える時間を使い切った）'])
  const read = readPullRequest(target, gh.dir, deps, timeout)
  if ('error' in read) return askMerge(label, [`pull request の情報を読めない（${read.error}）`])
  const problems = mergeProblems(read.pullRequest, headCommit)
  return problems.length === 0 ? null : askMerge(label, problems)
}

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

/** git か gh を語として含むか（git.exe、gh.exe、フルパスを含む。digit のような語の一部は含まない） */
const GIT_OR_GH_WORD = /\b(git|gh)\b/i

/** hook が判定する操作の語を、語として含むか（大文字と小文字を区別しない） */
const OPERATION_WORD = /\b(push|commit|tag|merge|api|release|hookspath)\b/i

/**
 * PowerShell のツールのコマンドを判定する。git か gh と、hook が判定する操作の語の両方を含めば拒否する。
 * PowerShell の書き方は読まず、語を含むかだけを見る（判定は Bash の 1 か所に置き、読むだけのコマンドは通すため）。
 *
 * @param {string} command コマンドの全文
 * @returns {Decision | null} 判定。通すときは null
 */
function judgePowerShell(command) {
  if (!GIT_OR_GH_WORD.test(command) || !OPERATION_WORD.test(command)) return null
  return deny(
    'PowerShell のツールでは、git・gh の push、commit、tag、merge、api、release、hooksPath を含むコマンドを実行しない。' +
      '同じコマンドを Bash のツールで実行し直す（Bash では、Claude Code の hook がコマンドの全文を読んで判定する）。',
  )
}

/**
 * hook の入力（JSON の文字列）を判定し、標準出力に出す文字列を返す。
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
 * 判定を、標準出力に出す文字列にする。
 *
 * @param {Decision | null} decision 判定。通すときは null
 * @returns {string} 出力する JSON。通すときは空文字
 */
function output(decision) {
  if (decision === null) return ''
  return JSON.stringify({ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: decision.decision, permissionDecisionReason: decision.reason } })
}

/**
 * 標準入力を読み、判定を標準出力に出す。
 */
async function main() {
  process.stdin.setEncoding('utf8')
  let text = ''
  for await (const chunk of process.stdin) text += chunk
  const output = handleInput(text)
  if (output !== '') process.stdout.write(output)
}

if (isMain(import.meta.url)) {
  // 標準入力を読めないなどの失敗では、判定できるコマンドがないので通す（終了コードを 0 以外にしない）
  main().catch(() => {
    process.exitCode = 0
  })
}
