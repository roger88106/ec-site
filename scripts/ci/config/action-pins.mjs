// workflow と複合の action の uses が、commit の SHA に固定されていることを確認する。
// タグやブランチの指定は、指す先が後から変わり、実行する action が知らない間に入れ替わる（サプライチェーンの攻撃の経路になる）ため。
// 許可する形は 2 つだけで、それ以外（タグ、ブランチ、短い SHA、大文字の 16 進、docker://、@ のない指定、式、文字列でない値）は全て失敗にする。
//   - ローカルの action：./ で始まる
//   - <owner>/<repo>[/<パス>]@<40 桁の小文字の 16 進>
//
// 対象は Git の対象のファイルのうち、.github/workflows/ 直下の *.yml・*.yaml と、.github/actions/ の下（全ての深さ）の action.yml・action.yaml。
// 読む場所は、jobs.<id>.steps[].uses、jobs.<id>.uses（再利用の workflow）、runs.steps[].uses。YAML として読むので、run の文字列とコメントは見ない。
// 読めない YAML と、対象のファイルが 0 件のときは失敗にする。SHA の後のバージョンのコメントは照合しない。
//
// 失敗したとき：uses を、<owner>/<repo>@<40 桁の小文字の 16 進> の形にする（SHA はタグが指す commit を調べて書く）。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/action-pins.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { load } from 'js-yaml'
import { emptyListReason, listGitFiles } from '../../shared/git/git-files.mjs'

/** 許可する形。ローカルの action（./ で始まる）か、<owner>/<repo>[/<パス>]@<40 桁の小文字の 16 進> */
const ALLOWED_USES = /^(?:\.\/.*|[^\s/@]+\/[^\s/@]+(?:\/[^\s@]+)?@[0-9a-f]{40})$/s

/** 失敗の出力に示す、固定の形への修正の方法 */
const FIX_HINT = '<owner>/<repo>@<40 桁の小文字の 16 進> の形（ローカルの action は ./ で始まる形）にする'

/** workflow の直下の yml と、.github/actions/ の下の全ての深さの action.yml・action.yaml */
const TARGET_PATH = /^\.github\/(?:workflows\/[^/]+\.ya?ml|actions\/(?:.+\/)?action\.ya?ml)$/

/**
 * 検査の対象のファイルか。
 *
 * @param {string} path リポジトリのルートからの相対パス（/ 区切り）
 * @returns {boolean} 対象なら true
 */
export function isActionPinTarget(path) {
  return TARGET_PATH.test(path)
}

/**
 * uses の値が許可する形か。
 *
 * @param {unknown} value uses の値
 * @returns {boolean} 許可する形なら true
 */
const isAllowedUses = (value) => typeof value === 'string' && ALLOWED_USES.test(value)

/**
 * YAML を読んだ値のうち、steps の配列から、uses を持つ場所と値を集める。
 *
 * @param {unknown} steps steps の値
 * @param {string} where steps の場所
 * @returns {{ where: string, value: unknown }[]} uses の場所と値
 */
function collectStepUses(steps, where) {
  if (!Array.isArray(steps)) return []
  return steps.flatMap((step, index) =>
    step !== null && typeof step === 'object' && Object.hasOwn(step, 'uses') ? [{ where: `${where}[${index}].uses`, value: step.uses }] : [],
  )
}

/**
 * YAML を読んだ値から、検査する uses の場所と値を集める。
 *
 * @param {unknown} doc 読んだ YAML
 * @returns {{ where: string, value: unknown }[]} uses の場所と値
 */
function collectUses(doc) {
  const found = []
  if (doc === null || typeof doc !== 'object') return found
  const jobs = doc.jobs
  if (jobs !== null && typeof jobs === 'object') {
    for (const [id, job] of Object.entries(jobs)) {
      if (job === null || typeof job !== 'object') continue
      if (Object.hasOwn(job, 'uses')) found.push({ where: `jobs.${id}.uses`, value: job.uses })
      found.push(...collectStepUses(job.steps, `jobs.${id}.steps`))
    }
  }
  if (doc.runs !== null && typeof doc.runs === 'object') {
    found.push(...collectStepUses(doc.runs.steps, 'runs.steps'))
  }
  return found
}

/**
 * 値を、失敗の出力に示す形にする（文字列は引用符で囲み、空白の違いを見えるようにする）。
 *
 * @param {unknown} value uses の値
 * @returns {string} 示す文字列
 */
function describeValue(value) {
  return typeof value === 'string' ? JSON.stringify(value) : `${JSON.stringify(value) ?? String(value)}（文字列でない）`
}

/**
 * workflow と複合の action の yml の、uses が固定の形かを調べる。
 *
 * @param {{ path: string, text: string }[]} files yml のパスと内容
 * @returns {string[]} 問題の説明（ファイル、場所、値、修正の方法つき。なければ空）
 */
export function findActionPinProblems(files) {
  const problems = []
  for (const { path, text } of files) {
    let doc
    try {
      doc = load(text)
    } catch (e) {
      problems.push(`${path} の YAML を読めない（判定できない）：${e.reason ?? e.message}`)
      continue
    }
    for (const { where, value } of collectUses(doc)) {
      if (!isAllowedUses(value)) {
        problems.push(`${path} の ${where}：${describeValue(value)} は固定の形でない。${FIX_HINT}`)
      }
    }
  }
  return problems
}

/**
 * 検査を実行して、結果を出力する。
 *
 * @param {string[]} paths 検査の対象のファイル
 * @param {(path: string) => string} read ファイルの内容を読む関数
 * @param {(line: string) => void} [log] 1 行を出力する関数
 * @returns {number} 終了コード（0：通る、1：固定の形でない uses がある、2：対象のファイルが 1 件もない）
 */
export function runActionPins(paths, read, log = console.log) {
  const reason = emptyListReason(paths, 'workflow と action の yml のファイル')
  if (reason) {
    log(reason)
    return 2
  }
  const problems = findActionPinProblems(paths.map((file) => ({ path: file, text: read(file) })))
  for (const problem of problems) log(problem)
  log(`action の固定：読んだファイル ${paths.length} 件、問題 ${problems.length} 件`)
  return problems.length ? 1 : 0
}

if (import.meta.main) {
  const targets = listGitFiles(['.github/workflows', '.github/actions']).filter(isActionPinTarget)
  process.exit(runActionPins(targets, (file) => readFileSync(file, 'utf8')))
}
