// ============================================================================
// Claude Code の PreToolUse の hook（.claude/settings.json から呼ぶ）。
// 同じディレクトリの pre-commit・commit-msg は git の hook で、これとは別のもの（git でなく Claude Code が実行する）。
//
// エージェントが Bash で実行しようとするコマンドの全文を読み、例外のない規則に反するものを拒否する。
// - push 済みの履歴の書き換え：git push の force（-f、--force、--force-with-lease、--force-if-includes、+ で始まる refspec）
// - commit の前の検査の省略：git commit・git push の --no-verify、git commit の -n、git -c core.hooksPath=…
// - pre-commit hook を置いていないリポジトリでの git commit（git rev-parse --git-path hooks/pre-commit のファイルが、
//   scripts/dev/hooks/pre-commit を呼んでいなければ拒否する。作業ツリーでは共通の .git の hooks を見る）
// permission の規則はコマンドの先頭の語でしか判定できず、フルパスの git や、&& でつないだ後ろのコマンドに効かないので、hook で全文を読む。
//
// 入力：標準入力の JSON（tool_name、tool_input.command、cwd など）。Bash 以外のツールは何もせずに通す。
// 出力：拒否するときは hookSpecificOutput の permissionDecision を標準出力に出す。通すときは何も出さない。終了コードは常に 0。
// 対象のディレクトリは、入力の cwd を起点に、コマンドの中の cd と git -C で決める
// （$CLAUDE_PROJECT_DIR は、作業ツリーのセッションでもメインの作業ツリーを指すことがあるので使わない）。
// 判定の中の例外では、git を含むコマンドは拒否し、含まないコマンドは通す
// （判定の不具合で force push などを通さず、かつ git と関係のない Bash まで止めないため）。
// ============================================================================
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import path from 'node:path'
import { isMain } from '../../ci/lib/is-main.mjs'

/** git の呼び出しの時間切れ（ミリ秒）。hook 全体の時間切れより短くする */
const GIT_TIMEOUT_MS = 5000

/** 置いた pre-commit hook が呼ぶ script（開発環境の起動 第 2 節の置き方） */
const PRE_COMMIT_SCRIPT = 'scripts/dev/hooks/pre-commit'

/** コマンドの前に付けても、実行するコマンドが変わらない語 */
const PREFIX_WORDS = new Set(['command', 'env', 'exec', 'nohup', 'time', 'builtin'])

/** force push の長い選択肢 */
const FORCE_OPTIONS = ['force', 'force-with-lease', 'force-if-includes']

/** 検査を省略する長い選択肢 */
const NO_VERIFY_OPTIONS = ['no-verify']

/** git push の選択肢のうち、値を次の語に取るもの */
const PUSH_OPTIONS = { valueShort: 'o', optionalShort: '', valueLong: ['repo', 'receive-pack', 'exec', 'push-option'] }

/** git commit の選択肢のうち、値を次の語（または同じ語の続き）に取るもの */
const COMMIT_OPTIONS = {
  valueShort: 'mFCct',
  // -u と -S は、値を同じ語の続きにだけ取る
  optionalShort: 'uS',
  valueLong: ['message', 'file', 'reuse-message', 'reedit-message', 'fixup', 'squash', 'template', 'author', 'date', 'cleanup', 'trailer', 'pathspec-from-file'],
}

/** force push を拒否する理由と代わりの手順 */
const FORCE_REASON =
  'push 済みの履歴を書き換えないため、force push（-f、--force、--force-with-lease、--force-if-includes、+ で始まる refspec）は使えない（AGENTS.md の Git の表）。' +
  'push 済みの commit を直すときは、修正を新しい commit にして、force を付けずに push する。'

/** 検査の省略を拒否する理由と代わりの手順 */
const NO_VERIFY_REASON =
  'commit の前の検査を省略しないため、--no-verify（git commit の -n を含む）は使えない（docs/guides/setup.md 第 2 節の hook）。' +
  'hook で止まったときは、表示された問題を直して、--no-verify を付けずに実行し直す。'

/** hook の置き場所の変更を拒否する理由と代わりの手順 */
const HOOKS_PATH_REASON =
  'commit の前の検査を実行させるため、git -c core.hooksPath=…（--config-env を含む）で hook の置き場所を替えることはできない。' +
  'core.hooksPath を指定せずに実行する。'

/**
 * @typedef {object} Decision hook の判定
 * @property {'deny'} decision 拒否する
 * @property {string} reason エージェントに返す、理由と代わりの手順
 */

/**
 * @typedef {object} Deps 差し替えられる外部の処理
 * @property {(args: string[], cwd: string) => { status: number | null, stdout: string }} runGit git を実行する
 * @property {(file: string) => string | null} readText ファイルを読む。なければ null
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
 *
 * @param {string} command コマンドの全文
 * @returns {string[][]} コマンドごとの語（引用符と \ を外したもの）
 */
export function splitCommands(command) {
  const commands = []
  let words = []
  let word = ''
  let inWord = false
  let dropNextWord = false
  let heredocs = []
  let i = 0
  const n = command.length
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
    if (c === "'") {
      const end = command.indexOf("'", i + 1)
      word += end < 0 ? command.slice(i + 1) : command.slice(i + 1, end)
      inWord = true
      i = end < 0 ? n : end + 1
    } else if (c === '"') {
      inWord = true
      i++
      while (i < n && command[i] !== '"') {
        // 二重引用符の中の \ は、" \ $ ` 改行の前でだけ働く
        if (command[i] === '\\' && i + 1 < n && '"\\$`\n'.includes(command[i + 1])) {
          if (command[i + 1] !== '\n') word += command[i + 1]
          i += 2
        } else {
          word += command[i]
          i++
        }
      }
      i++
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
      endCommand()
      i++
    } else {
      word += c
      inWord = true
      i++
    }
  }
  endCommand()
  return commands
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
 * コマンドの全文を、コマンドごとの語と、実行するディレクトリに分ける。
 * ディレクトリは入力の cwd から始め、cd・pushd の後のコマンドでは、その先にする。
 *
 * @param {string} command コマンドの全文
 * @param {string} cwd 入力の cwd
 * @param {PathOptions} [options] パスの解き方
 * @returns {{ words: string[], dir: string }[]} コマンドごとの語（前に付けた環境変数の設定などを除いたもの）とディレクトリ
 */
export function parseCommands(command, cwd, options = {}) {
  let dir = resolveDir(process.cwd(), cwd, options)
  const commands = []
  for (const raw of splitCommands(command)) {
    let start = 0
    while (start < raw.length && (/^[A-Za-z_][A-Za-z0-9_]*=/.test(raw[start]) || PREFIX_WORDS.has(raw[start]))) start++
    const words = raw.slice(start)
    if (words.length === 0) continue
    commands.push({ words, dir })
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
 */

/**
 * コマンドが git の呼び出しなら、git の前の選択肢とサブコマンドを読む。フルパスと git.exe の形も git として読む。
 *
 * @param {{ words: string[], dir: string }} command コマンドの語とディレクトリ
 * @param {PathOptions} [options] パスの解き方
 * @returns {GitInvocation | null} git の呼び出し。git でなければ null
 */
export function parseGitInvocation({ words, dir }, options = {}) {
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
  return { dir: current, configs, pathOptions, subcommand: words[i] ?? null, args: words.slice(i + 1) }
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
  const result = deps.runGit([...git.pathOptions, 'rev-parse', '--git-path', 'hooks/pre-commit'], git.dir)
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

/** git の呼び出しごとに当てる判定。拒否があれば、その時点で終える */
const GIT_JUDGES = [judgeHooksPath, judgePush, judgeCommitOptions, judgePreCommitHook]

/**
 * コマンドの全文を判定する。
 *
 * @param {string} command コマンドの全文
 * @param {string} cwd 入力の cwd
 * @param {Deps} deps 外部の処理
 * @param {PathOptions} [options] パスの解き方
 * @returns {Decision | null} 判定。通すときは null
 */
export function judgeCommand(command, cwd, deps, options = {}) {
  const decisions = []
  for (const parsed of parseCommands(command, cwd, options)) {
    const git = parseGitInvocation(parsed, options)
    if (git === null) continue
    for (const judge of GIT_JUDGES) {
      const decision = judge(git, deps)
      if (decision?.decision === 'deny') return decision
      if (decision) decisions.push(decision)
    }
  }
  return decisions[0] ?? null
}

/**
 * 実際の git とファイルを使う外部の処理を作る。
 *
 * @param {Record<string, string | undefined>} [env] git に渡す環境変数
 * @returns {Deps} 外部の処理
 */
export function createDeps(env = process.env) {
  return {
    runGit: (args, cwd) => spawnSync('git', args, { cwd, env, encoding: 'utf8', timeout: GIT_TIMEOUT_MS, windowsHide: true }),
    readText: (file) => {
      try {
        return readFileSync(file, 'utf8')
      } catch {
        return null
      }
    },
  }
}

/**
 * hook の入力（JSON の文字列）を判定し、標準出力に出す文字列を返す。
 * 読めない入力は通す（Claude Code は常に JSON を渡すので、読めないときに全てのツールを止めないため）。
 *
 * @param {string} text 標準入力の全文
 * @param {Deps} [deps] 外部の処理
 * @param {typeof judgeCommand} [judge] 判定の関数
 * @returns {string} 出力する JSON。通すときは空文字
 */
export function handleInput(text, deps = createDeps(), judge = judgeCommand) {
  let input
  try {
    input = JSON.parse(text)
  } catch {
    return ''
  }
  if (input?.tool_name !== 'Bash') return ''
  const command = input.tool_input?.command
  if (typeof command !== 'string' || command === '') return ''
  const cwd = typeof input.cwd === 'string' && input.cwd !== '' ? input.cwd : process.cwd()
  let decision
  try {
    decision = judge(command, cwd, deps)
  } catch (error) {
    decision = /\bgit\b/i.test(command)
      ? deny(
          `Claude Code の hook（scripts/dev/hooks/agent-guard.mjs）が、git を含むコマンドの判定で失敗した（${error instanceof Error ? error.message : String(error)}）。` +
            '判定できない git のコマンドは、force push などを通さないために拒否する。コマンドを単純な形に分けて実行し直し、通らなければ hook の不具合として報告する。',
        )
      : null
  }
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
