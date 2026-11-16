// gh の呼び出しの読み方と、gh api の規則。gh pr merge の条件は merge-rules.mjs。
import { APPROVE_ONLY_IF_ALLOWED, TAG_REASON, ask, deny } from './decision.mjs'

/** @typedef {import('./decision.mjs').Decision} Decision */

/**
 * gh のオプションの読み方。
 *
 * shortNames は短いオプションを長い名前にする対応、valueLong は値を取る長いオプション（短いオプションも長い名前で書く）。
 *
 * 値を取らないオプションは、値を "true" として読む。
 */
export const GH_PR_OPTIONS = {
  shortNames: { b: 'body', F: 'body-file', t: 'subject', A: 'author-email', R: 'repo', d: 'delete-branch', m: 'merge', r: 'rebase', s: 'squash' },
  valueLong: ['body', 'body-file', 'subject', 'author-email', 'match-head-commit', 'repo'],
}

/** gh release のオプション */
export const GH_RELEASE_OPTIONS = {
  shortNames: { R: 'repo', n: 'notes', F: 'notes-file', t: 'title', d: 'draft', p: 'prerelease' },
  valueLong: ['repo', 'notes', 'notes-file', 'title', 'target', 'discussion-category', 'notes-start-tag', 'latest', 'fail-on-no-commits'],
}

/** gh api のオプション */
const GH_API_OPTIONS = {
  shortNames: { X: 'method', H: 'header', f: 'raw-field', F: 'field', q: 'jq', t: 'template', p: 'preview', i: 'include' },
  valueLong: ['method', 'header', 'raw-field', 'field', 'input', 'jq', 'template', 'cache', 'preview', 'hostname'],
}

/** gh api で、-X がなくてもリクエストを POST にするオプション（本文を送る） */
const GH_API_BODY_OPTIONS = ['raw-field', 'field', 'input']

/** gh api のパスのうち、pull request をマージするもの */
const API_MERGE_PATH = /(^|\/)pulls\/[^/]+\/merge\/?(\?|$)/

/** gh api のパスのうち、Release と tag の参照を作成・変更・削除しうるもの（git/refs は refs/heads/ も含むが、見分けずに確認を求める） */
const API_TAG_PATHS = [/(^|\/)releases(\/|\?|$)/, /(^|\/)git\/(refs|tags)(\/|\?|$)/]

/**
 * GraphQL のエンドポイント。
 *
 * gh は `graphql`、`/graphql`、`https://<ホスト>/graphql`（`api/` を含む形も）を同じ GraphQL の API に送る
 */
const GRAPHQL_ENDPOINT = /^(?:https?:\/\/[^/]+\/(?:api\/)?|\/)?graphql\/?$/i

/** GraphQL の、pull request をマージする mutation */
const GRAPHQL_MERGE = /\bmergePullRequest\b/

/** gh api のパスのうち、ruleset とブランチの保護を変えうるもの */
const API_RULESET_PATHS = [/(^|\/)rulesets(\/|\?|$)/, /(^|\/)branches\/.+\/protection(\/|\?|$)/]

/** gh api のマージを拒否する理由と代わりの手順 */
const API_MERGE_REASON =
  'マージは gh api（REST の …/pulls/<番号>/merge、GraphQL の mergePullRequest）でなく、gh pr merge <番号> --match-head-commit <SHA> で行う（AGENTS.md の Git の表）。' +
  '承認を求めたときの head の commit を指定して実行し直す。'

/** ruleset の変更で確認を求める理由 */
const RULESET_REASON = `ruleset（ブランチの保護を含む）の変更は、利用者の許可が必要（ブランチとリリースの「緊急の修正」。docs/06-手順書/branches.md）。${APPROVE_ONLY_IF_ALLOWED}`

/**
 * @typedef {object} GhInvocation gh の呼び出し
 * @property {string} dir 実行するディレクトリ
 * @property {string | null} ghRepo 前に付けた GH_REPO
 * @property {string[]} args gh の後の語
 */

/**
 * コマンドが gh の呼び出しなら、gh の後の語を返す。
 *
 * フルパスと gh.exe の形も gh として読む。
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
 * gh のオプションと位置引数を読む。
 *
 * 長いオプションの略記は読まない（gh は略記を受け付けない）。
 *
 * @param {string[]} args 読む語
 * @param {{ shortNames: Record<string, string>, valueLong: string[] }} spec オプションの読み方
 * @returns {{ positionals: string[], options: { name: string, value: string }[] }} 位置引数と、オプション（値を取らないものは値が "true"）
 */
export function parseGhArgs(args, { shortNames, valueLong }) {
  const positionals = []
  const options = []
  let rest = false
  // 値を取るオプションは値と一緒に読み、残りを位置引数にする
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
      // -ds のような短いオプションの組み合わせ。値を取る文字の後ろは値
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
 * オプションの最後の値を返す。
 *
 * @param {{ name: string, value: string }[]} options オプション
 * @param {string} name 長い名前
 * @returns {string | null} 値。なければ null
 */
export function optionValue(options, name) {
  return options.findLast((option) => option.name === name)?.value ?? null
}

/**
 * 値を取らないオプションが有効か（`--auto=false` のような形は無効）。
 *
 * @param {{ name: string, value: string }[]} options オプション
 * @param {string} name 長い名前
 * @returns {boolean} 有効なら true
 */
export function flagEnabled(options, name) {
  const value = optionValue(options, name)
  return value !== null && value !== 'false'
}

/**
 * gh api の判定。
 *
 * GraphQL は、語のどれかが mergePullRequest を含めば拒否する（ファイルや標準入力から読む本文と、シェルの展開の後は見ない）。
 *
 * REST は GET 以外のリクエストのうち、マージのパスへのものは拒否し、Release と tag の参照、ruleset とブランチの保護へのものは確認を求める。
 *
 * @param {string[]} args api の後の語
 * @returns {Decision | null} 判定
 */
export function judgeGhApi(args) {
  const { positionals, options } = parseGhArgs(args, GH_API_OPTIONS)
  const endpoint = positionals[0] ?? ''
  if (GRAPHQL_ENDPOINT.test(endpoint)) return args.some((arg) => GRAPHQL_MERGE.test(arg)) ? deny(API_MERGE_REASON) : null
  // gh api は、-X がなくても本文を送るオプションがあれば POST にする
  const method = (optionValue(options, 'method') ?? (options.some((option) => GH_API_BODY_OPTIONS.includes(option.name)) ? 'POST' : 'GET')).toUpperCase()
  if (method === 'GET') return null
  if (API_MERGE_PATH.test(endpoint)) return deny(API_MERGE_REASON)
  if (API_TAG_PATHS.some((pattern) => pattern.test(endpoint))) return ask(TAG_REASON)
  return API_RULESET_PATHS.some((pattern) => pattern.test(endpoint)) ? ask(RULESET_REASON) : null
}
