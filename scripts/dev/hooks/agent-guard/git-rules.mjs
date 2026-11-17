// git の呼び出しの規則。規則の一覧は GIT_JUDGES。
import path from 'node:path'
import { resolveDir } from './commands.mjs'
import { TAG_REASON, ask, deny } from './decision.mjs'

/** @typedef {import('./decision.mjs').Decision} Decision */
/** @typedef {import('./decision.mjs').Deps} Deps */
/** @typedef {import('./commands.mjs').PathOptions} PathOptions */

/** 置いた pre-commit hook が呼ぶ script（開発環境の起動の「起動」の置き方） */
const PRE_COMMIT_SCRIPT = 'scripts/dev/hooks/pre-commit'

/** git の前のオプションのうち、値を次の語に取るもの */
export const GIT_VALUE_OPTIONS = ['-C', '-c', '--config-env', '--git-dir', '--work-tree', '--namespace', '--super-prefix']

/** force push の長いオプション（--mirror は、リモートの参照を force で更新し、リモートにしかない参照を削除する） */
const FORCE_OPTIONS = ['force', 'force-with-lease', 'force-if-includes', 'mirror']

/** 検査を省略する長いオプション */
const NO_VERIFY_OPTIONS = ['no-verify']

/** git push のオプションのうち、値を次の語に取るもの */
const PUSH_OPTIONS = { valueShort: 'o', optionalShort: '', valueLong: ['repo', 'receive-pack', 'exec', 'push-option'] }

/**
 * git tag のオプションのうち、値を次の語（または同じ語の続き）に取るもの。
 *
 * -n は値を同じ語の続きにだけ取るが、一覧の形かを見分けるためにオプションとして数えたいので、optionalShort に入れない（続きの数字もオプションの文字として読むが、判定に使わない）。
 */
const TAG_OPTIONS = {
  valueShort: 'mFu',
  optionalShort: '',
  valueLong: ['message', 'file', 'local-user', 'contains', 'no-contains', 'points-at', 'merged', 'no-merged', 'sort', 'format', 'cleanup', 'trailer'],
}

/** git tag を、作成でなく一覧・削除・検証にする短いオプション */
const TAG_LIST_SHORTS = ['l', 'd', 'v', 'n']

/** git tag を、作成でなく一覧・削除・検証にする長いオプション（--contains などは一覧の形になる） */
const TAG_LIST_LONGS = ['list', 'delete', 'verify', 'contains', 'no-contains', 'points-at', 'merged', 'no-merged']

/** git push で tag を送る長いオプション */
const TAG_PUSH_OPTIONS = ['tags', 'follow-tags']

/** git commit のオプションのうち、値を次の語（または同じ語の続き）に取るもの */
const COMMIT_OPTIONS = {
  valueShort: 'mFCct',
  // -u と -S は、値を同じ語の続きにだけ取る
  optionalShort: 'uS',
  valueLong: ['message', 'file', 'reuse-message', 'reedit-message', 'fixup', 'squash', 'template', 'author', 'date', 'cleanup', 'trailer', 'pathspec-from-file'],
}

/** force push を通す、push の先のブランチの接頭辞（作業ブランチ） */
const FORCE_ALLOWED_PREFIX = 'agent/'

/** push の先を 1 つに決められない、全てのブランチや参照を送る長いオプション */
const PUSH_ALL_OPTIONS = ['all', 'branches', 'mirror']

/** force push を拒否する理由と代わりの手順 */
const FORCE_REASON =
  'agent/ で始まる作業ブランチ以外（develop、main、release/**、hotfix/** など）の push 済みの履歴は書き換えないため、' +
  'force push（-f、--force、--force-with-lease、--force-if-includes、+ で始まる refspec）は、push の先が全て agent/ で始まるときだけ使える。--mirror・--all・--branches の force は使えない（AGENTS.md の Git の表）。' +
  'push の先を決められないときも拒否する（refspec がなく、今のブランチを読めないか、push.default が matching か、remote.<名前>.push を設定してあるとき）。' +
  'agent/ のブランチなら refspec で先を書いて（例：git push --force-with-lease origin HEAD:agent/<名前>）実行し直す。それ以外のブランチの commit を直すときは、修正を新しい commit にして、force を付けずに push する。'

/** 検査の省略を拒否する理由と代わりの手順 */
const NO_VERIFY_REASON =
  'commit の前の検査を省略しないため、git commit の --no-verify と -n は使えない（開発環境の起動の「起動」の hook。docs/06-手順書/setup.md）。' +
  'hook で止まったときは、表示された問題を直して、--no-verify を付けずに実行し直す。'

/** hook の置き場所の変更を拒否する理由と代わりの手順 */
const HOOKS_PATH_REASON =
  'commit の前の検査を実行させるため、git -c core.hooksPath=…（--config-env を含む）で hook の置き場所を替えることはできない。' +
  'core.hooksPath を指定せずに実行する。'

/**
 * @typedef {object} GitInvocation git の呼び出し
 * @property {string} dir git -C を当てた後の、実行するディレクトリ
 * @property {string[]} configs -c・--config-env で渡した設定
 * @property {string[]} pathOptions --git-dir・--work-tree のオプション（そのまま git に渡せる形）
 * @property {string | null} subcommand サブコマンド
 * @property {string[]} args サブコマンドの後の語
 * @property {Record<string, string>} env 前に付けた、対象のリポジトリを替える環境変数（GIT_DIR・GIT_WORK_TREE）
 */

/**
 * コマンドが git の呼び出しなら、git の前のオプションとサブコマンドを読む。
 *
 * フルパスと git.exe の形も git として読む。
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
  // git の前のオプションを、サブコマンドまで読む
  for (; i < words.length && words[i].startsWith('-'); i++) {
    const word = words[i]
    if (word === '-C') {
      // 先が変数かコマンドの置き換えを含むときは、cd と同じく値を読めないので、今のディレクトリのままにする
      const target = words[++i] ?? ''
      if (!/[$`]/.test(target)) current = resolveDir(current, target, options)
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
    } else if (GIT_VALUE_OPTIONS.includes(word)) {
      // 値を次の語に取る、他のオプション（--namespace、--super-prefix）
      i++
    }
  }
  return { dir: current, configs, pathOptions, subcommand: words[i] ?? null, args: words.slice(i + 1), env }
}

/**
 * サブコマンドの後の語を、長いオプション・短いオプション・位置引数に分ける。
 *
 * オプションの値は、どれにも含めない。
 *
 * @param {string[]} args サブコマンドの後の語
 * @param {{ valueShort: string, optionalShort: string, valueLong: string[] }} spec 値を取るオプション
 * @returns {{ longs: string[], shorts: string[], positionals: string[] }} 長いオプションの名前（-- と = の後を除く）、短いオプションの文字、位置引数
 */
function scanOptions(args, { valueShort, optionalShort, valueLong }) {
  const longs = []
  const shorts = []
  const positionals = []
  let rest = false
  // オプションの値を飛ばしながら、オプションと位置引数に分ける
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
      // -an のような短いオプションの組み合わせ。値を取る文字の後ろは値なので読まない
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
 * 長いオプションの略記として当てる、最も短い文字数。
 *
 * 2 文字以下の略記は当てない（既知の限り。--fo のような短い略記を git が受け付けるかは見分けない）。
 */
const MIN_ABBREVIATION = 3

/**
 * 長いオプションの名前が、対象のオプションのどれかか。
 *
 * git は一意な前方の一致の略記も受け付けるので、MIN_ABBREVIATION 文字以上の略記も当てる。
 *
 * @param {string} name オプションの名前
 * @param {string[]} targets 対象のオプションの名前
 * @returns {boolean} 当たれば true
 */
function matchesLong(name, targets) {
  return targets.some((target) => name === target || (name.length >= MIN_ABBREVIATION && target.startsWith(name)))
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
 * git push の位置引数のうち、refspec を返す。
 *
 * 最初の位置引数はリモート（--repo で指定したときは全てが refspec）。
 *
 * @param {string[]} longs 長いオプションの名前
 * @param {string[]} positionals 位置引数
 * @returns {string[]} refspec
 */
function pushRefspecs(longs, positionals) {
  // git は --rep のような略記も --repo として受け付ける。略記を見落とすと、最初の refspec をリモートとみなして読み飛ばす
  return longs.some((name) => matchesLong(name, ['repo'])) ? positionals : positionals.slice(1)
}

/**
 * 対象のリポジトリの今のブランチと、push の先に関わる設定を読む。
 *
 * @param {GitInvocation} git git の呼び出し
 * @param {Deps} deps 外部の処理
 * @returns {{ branch: string | null, config: Map<string, string[]> | null }} 今のブランチ（読めないか detached なら null）と、
 *   push.default・remote.*.push・branch.*.merge の設定（読めなければ null）
 */
function readPushState(git, deps) {
  const head = deps.runGit([...git.pathOptions, 'symbolic-ref', '--quiet', '--short', 'HEAD'], git.dir, git.env)
  const branch = head?.status === 0 ? head.stdout.trim() || null : null
  const result = deps.runGit([...git.pathOptions, 'config', '--get-regexp', '^(push\\.default|remote\\..+\\.push|branch\\..+\\.merge)$'], git.dir, git.env)
  // 終了コード 1 は、当たる設定がないこと
  if (result?.status !== 0 && result?.status !== 1) return { branch, config: null }
  const config = new Map()
  for (const line of (result.status === 0 ? result.stdout : '').split('\n')) {
    const match = /^(\S+)\s+(.*)$/.exec(line.trim())
    if (match) config.set(match[1], [...(config.get(match[1]) ?? []), match[2]])
  }
  return { branch, config }
}

/**
 * ブランチの名前か参照が、force push を通すブランチ（agent/ で始まる）を指すか。
 *
 * @param {string} ref ブランチの名前か refs/heads/ の参照
 * @returns {boolean} 通すブランチなら true
 */
function isForceAllowedBranch(ref) {
  const name = ref.startsWith('refs/heads/') ? ref.slice('refs/heads/'.length) : ref
  return !name.startsWith('refs/') && name.startsWith(FORCE_ALLOWED_PREFIX)
}

/**
 * force push の先のブランチが、全て agent/ で始まるかを調べる。
 *
 * refspec があれば、それぞれの先（: の後。: がなければ前の名前で、HEAD は今のブランチ）を見る。
 * refspec がなければ、今のブランチとその上流（branch.<名前>.merge）を見る。
 * push.default が matching か、remote.<名前>.push を設定してあるときは、送る先が設定で変わるので、決められないとする。
 *
 * @param {GitInvocation} git git の呼び出し
 * @param {string[]} refspecs refspec
 * @param {Deps} deps 外部の処理
 * @returns {boolean} 先を全て決められ、どれも agent/ で始まれば true
 */
function forceTargetsAllowed(git, refspecs, deps) {
  const { branch, config } = readPushState(git, deps)
  if (config === null) return false
  const pushDefault = config.get('push.default')?.at(-1)?.toLowerCase()
  if (pushDefault === 'matching' || [...config.keys()].some((key) => /^remote\..+\.push$/i.test(key))) return false
  // 上流を設定したブランチは、push.default が upstream のとき上流へ送られるので、上流も agent/ で始まることを求める
  const upstreamAllowed = (name) => (config.get(`branch.${name}.merge`) ?? []).every(isForceAllowedBranch)
  if (refspecs.length === 0) return branch !== null && isForceAllowedBranch(branch) && upstreamAllowed(branch)
  return refspecs.every((refspec) => {
    const [source, destination] = refspec.replace(/^\+/, '').split(':')
    if (destination !== undefined) return destination === 'HEAD' ? branch !== null && isForceAllowedBranch(branch) : isForceAllowedBranch(destination)
    const name = source === 'HEAD' ? branch : source
    return name !== null && isForceAllowedBranch(name) && upstreamAllowed(name.replace(/^refs\/heads\//, ''))
  })
}

/**
 * git push の force を、push の先が全て agent/ で始まるとき以外は拒否する。
 *
 * dry-run（-n、--dry-run）も、ほかの push と同じに判定する。
 *
 * @param {GitInvocation} git git の呼び出し
 * @param {Deps} deps 外部の処理
 * @returns {Decision | null} 判定
 */
function judgePush(git, deps) {
  if (git.subcommand !== 'push') return null
  const { longs, shorts, positionals } = scanOptions(git.args, PUSH_OPTIONS)
  const forced = longs.some((name) => matchesLong(name, FORCE_OPTIONS)) || shorts.includes('f') || positionals.some((p) => p.startsWith('+'))
  if (!forced) return null
  if (longs.some((name) => matchesLong(name, PUSH_ALL_OPTIONS))) return deny(FORCE_REASON)
  return forceTargetsAllowed(git, pushRefspecs(longs, positionals), deps) ? null : deny(FORCE_REASON)
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
 *
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
      `開発環境の起動の「起動」（docs/06-手順書/setup.md。hook の置き方）のとおり、このファイルに ${PRE_COMMIT_SCRIPT} を呼ぶ 1 行を書いてから commit する。`,
  )
}

/**
 * git tag の、tag の作成で確認を求める。
 *
 * 一覧（引数なし、-l、--contains など）、削除（-d）、検証（-v）は通す
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
 * refspec の片側が tag を指すか。
 *
 * refs/tags/ を含めば tag、refs/ で始まらない名前は、対象のリポジトリにその名前の tag があれば tag とする。
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
  // 終了コード 1 はその tag がないこと。null（タイムアウト）は調べられない
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
  const refspecs = pushRefspecs(longs, positionals)
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
export const GIT_JUDGES = [judgeHooksPath, judgePush, judgeCommitOptions, judgePreCommitHook, judgeTagCreate, judgeTagPush]
