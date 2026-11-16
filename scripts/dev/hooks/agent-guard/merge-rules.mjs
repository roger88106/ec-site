// gh pr merge の規則。pull request を gh で読み、利用者の許可なしにマージしてよい条件（マージと後片付けの「マージの許可と後片付け」）を調べる。
//
// 条件を満たさないときと、pull request を読めないときは、確認を求める。未解決の指摘が 0 かは読めないので見ない（文書の規則のまま守る）。
// 向き先が agent/ で始まる pull request は、開いていることと head の commit だけを見て通す。
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { applyDependabotTitle, isDependabot, judgeRisk, parseRiskConfig, readBodyRisk } from '../../../ci/config/risk.mjs'
import { APPROVE_ONLY_IF_ALLOWED, ask, deny } from './decision.mjs'
import { GH_PR_OPTIONS, flagEnabled, optionValue, parseGhArgs } from './gh-rules.mjs'

/** @typedef {import('./decision.mjs').Decision} Decision */
/** @typedef {import('./decision.mjs').CommandResult} CommandResult */
/** @typedef {import('./decision.mjs').Deps} Deps */
/** @typedef {import('./gh-rules.mjs').GhInvocation} GhInvocation */

/**
 * gh の 1 回の呼び出しのタイムアウト（ミリ秒）。
 *
 * pull request を読む GraphQL の 1 回のリクエストは、ふだん 1〜3 秒で終わるので、余裕を見て 10 秒にする。
 */
export const GH_TIMEOUT_MS = 10000

/**
 * 1 つのコマンドを判定するとき、gh に使える時間の合計（ミリ秒）。
 *
 * hook 全体のタイムアウト（.claude/settings.json の timeout）から、
 * node の起動と git の呼び出しの分（約 10 秒）を残す。使い切った後のマージは、読まずに確認を求める。
 */
export const GH_BUDGET_MS = 20000

/** 危険度が高い経路の一覧（Dependabot の pull request の危険度の判定に使う） */
const RISK_CONFIG_FILE = new URL('../../../ci/config/risk.json', import.meta.url)

/** 利用者の許可なしにマージしてよい向き先のブランチ（CI と危険度の条件を満たすとき） */
const MERGE_BASE = 'develop'

/** 向き先がこの接頭辞で始まる pull request（作業ブランチどうしのマージ）は、CI と危険度を見ずに、利用者の許可なしにマージしてよい */
const AGENT_BASE_PREFIX = 'agent/'

/** pull request の本文の危険度を、本文の判定と照合する CI の検査の名前 */
const RISK_CHECK = '危険度の照合'

/** 機能の spec の issue に付けるラベル */
const FEATURE_LABEL = '種類:機能'

/**
 * 通ったとみなす check run の結論。
 *
 * neutral は、GitHub が必須の検査で成功と同じに扱う結論で、
 * Dependabot の pull request の CodeQL が警告なしで返すので、成功に含める。
 */
const PASSING_CONCLUSIONS = ['SUCCESS', 'SKIPPED', 'NEUTRAL']

/** 終わっていないとみなす commit status の状態 */
const PENDING_STATES = ['PENDING', 'EXPECTED']

/** GraphQL の一覧の 1 ページの件数（GitHub の上限） */
const PAGE_SIZE = 100

/**
 * マージの条件を読む GraphQL の問い合わせ。
 *
 * 1 回のリクエストで、pull request、変更のファイル、閉じる issue のラベル、head の commit の検査を読む。
 *
 * 一覧はどれも 1 ページ（PAGE_SIZE 件）だけ読み、続きがあれば条件を確認できないとする。
 */
const PULL_REQUEST_QUERY = `query($owner: String!, $name: String!, $number: Int!) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      state baseRefName headRefOid title body changedFiles lastEditedAt
      author { __typename login }
      files(first: ${PAGE_SIZE}) { nodes { path } pageInfo { hasNextPage } }
      closingIssuesReferences(first: ${PAGE_SIZE}) {
        nodes { number labels(first: ${PAGE_SIZE}) { nodes { name } pageInfo { hasNextPage } } }
        pageInfo { hasNextPage }
      }
      commits(last: 1) {
        nodes { commit { statusCheckRollup { contexts(first: ${PAGE_SIZE}) {
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

/** gh pr merge のオプションのうち、自動のマージの解除と一緒に書いても解除だけになるもの */
const DISABLE_AUTO_OPTIONS = ['disable-auto', 'repo']

/** pull request の URL（github.com のもの） */
const PULL_REQUEST_URL = /^https?:\/\/github\.com\/([^/]+)\/([^/]+)\/pull\/(\d+)(?:[/?#].*)?$/i

/** --match-head-commit のないマージを拒否する理由と代わりの手順 */
const NO_MATCH_HEAD_REASON =
  'マージは、承認を求めたとき（CI を確認したとき）の head の commit を --match-head-commit で指定して行う（AGENTS.md の Git の表）。' +
  'gh pr merge <番号> --match-head-commit <SHA> で実行し直す。'

/**
 * `[HOST/]OWNER/REPO` の形のリポジトリを読む。
 *
 * github.com 以外のホストは読まない。
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
 * gh pr merge の位置引数と -R・GH_REPO から、マージする pull request を決める。
 *
 * @param {string | undefined} target 位置引数
 * @param {string | null} repoOption -R・--repo の値
 * @param {string | null} ghRepo 前に付けた GH_REPO
 * @returns {MergeTarget | { error: string }} pull request か、決められない理由
 */
function resolveMergeTarget(target, repoOption, ghRepo) {
  const noTarget = { error: 'pull request を、番号か github.com の URL で指定していない（番号のない形は今のブランチの pull request を指す）' }
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
 * @param {number} timeout 渡したタイムアウト（ミリ秒）
 * @returns {string | null} 失敗の理由。成功なら null
 */
function describeGhFailure(result, timeout) {
  if (result.error?.code === 'ETIMEDOUT') return `gh がタイムアウトした（${timeout / 1000} 秒）`
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
 * @param {number} timeout タイムアウト（ミリ秒）
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
  } catch (e) {
    return { error: `gh を実行できない：${e instanceof Error ? e.message : String(e)}` }
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
 *
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
  if (truncated) problems.push(`CI の検査の一覧を全ては読めない（${PAGE_SIZE} 件を超える）`)
  if (undated.length > 0) problems.push(`再実行で重なった CI の検査の時刻を読めず、最も新しい実行を決められない（${undated.join('、')}）`)
  if (pending.length > 0) problems.push(`CI の検査が終わっていない（${[...new Set(pending)].join('、')}）`)
  if (failed.length > 0) problems.push(`CI の検査が成功していない（${[...new Set(failed)].join('、')}）`)
  return problems
}

/**
 * 危険度が高い経路の一覧を読む。
 *
 * @returns {{ groups: { name: string, paths: string[] }[] }} 危険度が高い経路の一覧
 */
function readRiskConfig() {
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
    config = readRiskConfig()
  } catch (e) {
    return [`危険度が高い経路の一覧（scripts/ci/config/risk.json）を読めない：${e instanceof Error ? e.message : String(e)}`]
  }
  // Dependabot は spec.md を変えないので、spec の宣言を読む関数は常に null を返すものでよい
  const result = applyDependabotTitle(judgeRisk(files, config, () => null), pullRequest.title)
  if (result.level === '低') return []
  const reasons = [...result.hits.map((hit) => `${hit.name}：${hit.files.join('、')}`), result.dependabot]
  return [`Dependabot の更新の危険度が高い（${reasons.join('、')}）`]
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
  else if (bodyRisk.value !== '低') problems.push(`本文の危険度の判定が「低」でない（判定：${bodyRisk.value}）`)
  const riskChecks = checks.filter((node) => node.__typename === 'CheckRun' && node.name === RISK_CHECK)
  if (riskChecks.length === 0 || riskChecks.some((node) => node.status !== 'COMPLETED' || node.conclusion !== 'SUCCESS')) {
    problems.push(`検査「${RISK_CHECK}」が成功していない（ないか、成功以外）`)
  }
  // 照合は本文の編集でも再実行されるが、再実行が始まるまでは編集の前の本文で成功した実行だけが残るので、編集の後に始まった実行かを見る
  if (pullRequest.lastEditedAt) {
    const edited = Date.parse(pullRequest.lastEditedAt)
    const stale = riskChecks.some((node) => {
      const started = Date.parse(node.startedAt ?? '')
      return Number.isNaN(edited) || Number.isNaN(started) || started < edited
    })
    if (stale) problems.push(`検査「${RISK_CHECK}」の最も新しい実行が、本文の最後の編集（${pullRequest.lastEditedAt}）の後に始まっていない（編集した後の本文で照合していない）`)
  }
  const closing = pullRequest.closingIssuesReferences
  const issues = closing?.nodes ?? []
  if (closing?.pageInfo?.hasNextPage || issues.some((issue) => issue.labels?.pageInfo?.hasNextPage)) {
    problems.push(`閉じる issue かそのラベルの一覧を全ては読めない（${PAGE_SIZE} 件を超える）`)
  }
  if (issues.length === 0) problems.push('本文の Closes の issue がない（機能の spec でないことを確認できない）')
  const features = issues.filter((issue) => (issue.labels?.nodes ?? []).some((label) => label.name === FEATURE_LABEL))
  if (features.length > 0) problems.push(`閉じる issue にラベル「${FEATURE_LABEL}」がある（${features.map((issue) => `#${issue.number}`).join('、')}）`)
  return problems
}

/**
 * gh api graphql の作成者を、risk.mjs の作成者の形にする。
 *
 * GraphQL の Bot の login は `[bot]` を付けないので付ける。
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
 * 向き先が agent/ で始まるものは、開いていることと head の commit だけを見る（途中のブランチの履歴は、最後の pull request で利用者が確認するため）。
 *
 * @param {object} pullRequest pull request（GraphQL の形）
 * @param {string} headCommit --match-head-commit の値
 * @returns {string[]} 満たさない条件。空なら全て満たす
 */
function mergeProblems(pullRequest, headCommit) {
  const problems = []
  if (pullRequest.state !== 'OPEN') problems.push(`pull request が開いていない（${pullRequest.state}）`)
  if (String(pullRequest.headRefOid).toLowerCase() !== headCommit.toLowerCase()) {
    problems.push(`--match-head-commit の commit（${headCommit}）が、pull request の今の head の commit（${pullRequest.headRefOid}）と一致しない`)
  }
  if (String(pullRequest.baseRefName).startsWith(AGENT_BASE_PREFIX)) return problems
  if (pullRequest.baseRefName !== MERGE_BASE) problems.push(`向き先が ${MERGE_BASE} か ${AGENT_BASE_PREFIX} で始まるブランチでない（${pullRequest.baseRefName}）`)
  const checks = latestChecks(pullRequest)
  problems.push(...checkProblems(checks))
  const dependabot = isDependabot({ author: authorName(pullRequest.author) })
  problems.push(...(dependabot ? dependabotProblems(pullRequest) : authoredProblems(pullRequest, checks.nodes)))
  return problems
}

/**
 * gh pr merge の判定。
 *
 * --match-head-commit のない形は拒否し、pull request を読んで条件を全て満たせば通し、満たさないか読めなければ確認を求める。
 *
 * --admin と --auto は見ない。ruleset の除外の対象が空で、リポジトリの設定で自動のマージを無効にしてあるので、GitHub が受け付けないため。
 *
 * @param {string[]} args pr の後の語（merge を含む）
 * @param {GhInvocation} gh gh の呼び出し
 * @param {Deps} deps 外部の処理
 * @param {{ deadline: number }} budget gh に使える時間の期限（Date.now() の値）
 * @returns {Decision | null} 判定
 */
export function judgeMerge(args, gh, deps, budget) {
  const { positionals, options } = parseGhArgs(args, GH_PR_OPTIONS)
  // 自動のマージの解除だけ（マージのオプションを一緒に書かない形）は、マージしないので通す
  if (flagEnabled(options, 'disable-auto') && options.every((option) => DISABLE_AUTO_OPTIONS.includes(option.name))) return null
  const headCommit = optionValue(options, 'match-head-commit')
  if (headCommit === null || headCommit === '') return deny(NO_MATCH_HEAD_REASON)
  const askMerge = (label, problems) =>
    ask(`利用者の許可なしにマージしてよい条件（マージと後片付けの「マージの許可と後片付け」。docs/05-開発規則/merging.md）を満たさないので、確認を求める。pull request：${label}。満たさない条件：${problems.join('。')}。${APPROVE_ONLY_IF_ALLOWED}`)
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
