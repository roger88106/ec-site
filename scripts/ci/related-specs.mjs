// 設計の文書（概要の表に「関連 spec」の行を持つ文書）を変えた spec が、その行にあることを確認する（skill docs 第 2 節）。
// spec は、文書を変えた commit のメッセージの 1 行目のタスク番号（T011-01-01、T7-01-01 の形）から読む。古い形式のタスク番号（T3101 など）は
// spec を一意に決められないので見ない。文書の移動は git log --follow で追う。
// 設計を変えない作業（言い回しの修正、文書の移動など）の段階は、related-specs-exclusions.json に、タスク番号の前置き（T018-05）と理由を書く。
// 当たるタスク番号の commit がない除外は、CI（環境変数 CI=true）でだけ失敗にし、手元では警告にする（新しい除外を加える最初の commit は、履歴にまだ当たる commit がないため）。
// 使い方：node scripts/ci/related-specs.mjs（リポジトリのルートで。commit の履歴が必要なので、shallow clone では失敗にする）
// 次の commit の照合：node scripts/ci/related-specs.mjs --staged <メッセージのファイル>（commit-msg の hook から呼ぶ。ステージした文書とメッセージの 1 行目を照合する）
import { execFileSync, spawnSync } from 'node:child_process'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { readFileSync, statSync } from 'node:fs'
import { posix } from 'node:path'
import { describeExclusionProblems, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { isMain } from './lib/is-main.mjs'
import { exitIfShallow } from './lib/shallow-clone.mjs'

/** 概要の表の「関連 spec」の行 */
const RELATED_ROW = /^\|\s*関連 spec\s*\|(.*)\|\s*$/m

/** spec のディレクトリのパス（リポジトリのルートから）。進行中は docs/08-spec/<番号>-<名前>、完了したものは docs/08-spec/archive/<まとまり>/<番号>-<名前> */
const SPEC_DIRECTORY = /^docs\/08-spec\/(?:archive\/[^/]+\/)?\d+-[^/]+$/

/** 新しい形式のタスク番号。spec の番号は文字列のまま使い、0 で始まる 3 桁（T011-）は MVP、0 で始めない番号（T7-）は 1→10 の spec として区別する */
const TASK_ID = /(?<![A-Za-z0-9])T(\d+)-\d{2}-\d{2}/g

/**
 * 概要の表の「関連 spec」の行から、spec の番号を読む。
 *
 * @param {string} markdown 設計の文書
 * @returns {string[] | null} spec の番号（書いた順）。行がなければ null
 */
export function parseRelatedSpecs(markdown) {
  const row = markdown.match(RELATED_ROW)
  return row ? [...row[1].matchAll(/\[(\d+)\]\(/g)].map((match) => match[1]) : null
}

/**
 * 概要の表の「関連 spec」の行のリンクが、spec のディレクトリを指すかを確認する。
 * 正しい形は、機能仕様の文書（docs/03-機能仕様/）からは `[番号](../08-spec/<番号>-<名前>)` か `[番号](../08-spec/archive/<まとまり>/<番号>-<名前>)`（spec.md などのファイルや、移動前の古いパスは不可）。
 *
 * @param {string} file 文書のパス（リポジトリのルートから）
 * @param {string} markdown 文書
 * @param {(path: string) => boolean} isDirectory ディレクトリがあるかを返す関数
 * @returns {string[]} 誤りの説明
 */
export function findBadRelatedLinks(file, markdown, isDirectory) {
  const row = markdown.match(RELATED_ROW)
  if (!row) return []
  return [...row[1].matchAll(/\[([^\]]*)\]\(([^)\s]*)\)/g)].flatMap(([, text, link]) => {
    const target = posix.normalize(posix.join(posix.dirname(file), link))
    if (!SPEC_DIRECTORY.test(target)) return [`${file}：関連 spec のリンク ${link} が、docs/08-spec/ の下の spec のディレクトリを指していない`]
    if (!isDirectory(target)) return [`${file}：関連 spec のリンク ${link} が、spec のディレクトリを指していない`]
    if (!posix.basename(target).startsWith(`${text}-`)) return [`${file}：関連 spec のリンク ${link} の番号が、リンクの文字 ${text} と合わない`]
    return []
  })
}

/**
 * commit のメッセージの 1 行目から、spec の番号を読む。
 *
 * @param {string} subject メッセージの 1 行目
 * @param {string[]} excludedPrefixes 数えないタスク番号の前置き（T018-05 など）
 * @returns {string[]} spec の番号（重ねない）。除外のタスク番号を 1 つでも含む commit は、空（テストのタスクと一緒の commit を含めて除くため）
 */
export function specsOfCommit(subject, excludedPrefixes) {
  const matches = [...subject.matchAll(TASK_ID)]
  if (matches.some((match) => excludedPrefixes.some((prefix) => match[0].startsWith(prefix)))) return []
  return [...new Set(matches.map((match) => match[1]))]
}

/**
 * 除外のタスク番号の前置きが、どれかの commit のタスク番号に当たるかを返す（当たらない除外は不要）。
 *
 * @param {string} prefix 数えないタスク番号の前置き（T018-05 など）
 * @param {string[]} subjects commit のメッセージの 1 行目
 * @returns {boolean} 前置きで始まるタスク番号を持つ commit があれば true
 */
export function isPrefixUsed(prefix, subjects) {
  return subjects.some((subject) => [...subject.matchAll(TASK_ID)].some((match) => match[0].startsWith(prefix)))
}

/**
 * 除外の要否を見る commit の履歴を読む Git の引数を返す。
 * merge の途中は、除外の一覧が相手の側の内容になるが HEAD の履歴にはまだ相手の commit がないので、相手の履歴も読む。
 *
 * @param {boolean} merging merge の途中（MERGE_HEAD がある）なら true
 * @returns {string[]} git に渡す引数
 */
export function historyLogArgs(merging) {
  return merging ? ['log', '--format=%s', 'HEAD', 'MERGE_HEAD'] : ['log', '--format=%s']
}

/**
 * 文書を変えた commit の spec のうち、関連 spec にないものを返す。
 *
 * @param {string[]} related 関連 spec の番号
 * @param {string[]} subjects 文書を変えた commit のメッセージの 1 行目
 * @param {string[]} excludedPrefixes 数えないタスク番号の前置き
 * @returns {string[]} 足りない spec の番号（番号の順）
 */
export function findMissingSpecs(related, subjects, excludedPrefixes) {
  const specs = new Set(subjects.flatMap((subject) => specsOfCommit(subject, excludedPrefixes)))
  return [...specs].filter((spec) => !related.includes(spec)).sort()
}

/**
 * ステージした設計の文書のうち、次の commit の spec が関連 spec にないものを返す。
 *
 * @param {{ file: string, markdown: string }[]} docs ステージした文書とその中身
 * @param {string} subject 次の commit のメッセージの 1 行目
 * @param {string[]} excludedPrefixes 数えないタスク番号の前置き
 * @returns {{ file: string, spec: string }[]} 足りない文書と spec の番号
 */
export function findMissingInStaged(docs, subject, excludedPrefixes) {
  return docs.flatMap(({ file, markdown }) => {
    const related = parseRelatedSpecs(markdown)
    if (!related) return []
    return findMissingSpecs(related, [subject], excludedPrefixes).map((spec) => ({ file, spec }))
  })
}

/**
 * 除外の一覧の誤りを、失敗と警告に分ける。理由のない除外は常に失敗。当たる commit がない除外は、CI でだけ失敗にし、手元では警告にする。
 * 理由：新しい段階の除外を加える最初の commit では、履歴にまだそのタスク番号の commit がないので、手元（hook を含む）で失敗にすると commit できない。
 *
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @param {(prefix: string) => boolean} isNeeded その前置きに当たる commit が履歴にあるか
 * @param {boolean} strict CI なら true
 * @returns {{ failures: string[], warnings: string[] }} 失敗にする文と、警告として表示する文（「警告：」で始まる）
 */
export function checkRelatedExclusions(exclusions, isNeeded, strict) {
  const { emptyReasons: failures, stale } = describeExclusionProblems('related-specs-exclusions.json', exclusions, isNeeded)
  return strict ? { failures: [...failures, ...stale], warnings: [] } : { failures, warnings: stale.map((line) => `警告：${line}`) }
}

/** ディレクトリがあるか */
function isDirectory(path) {
  try {
    return statSync(path).isDirectory()
  } catch {
    return false
  }
}

/** 次の commit（ステージした内容とメッセージ）を照合する。commit-msg の hook から呼ぶ */
function checkStaged(messageFile, excludedPrefixes) {
  const subject = readFileSync(messageFile, 'utf8').split(/\r?\n/)[0]
  // 日本語を含むパスを、引用符と 8 進数の形にせずに読む（core.quotepath の既定のままでは git show に渡せないため）
  const docs = execFileSync('git', ['-c', 'core.quotepath=false', 'diff', '--cached', '--name-only', '--diff-filter=AMR', '--', 'docs/*.md'], { encoding: 'utf8' })
    .split('\n')
    .filter(Boolean)
    .map((file) => ({ file, markdown: execFileSync('git', ['show', `:${file}`], { encoding: 'utf8' }) }))
  const missing = findMissingInStaged(docs, subject, excludedPrefixes)
  for (const { file, spec } of missing) {
    console.log(`${file}：この commit（spec ${spec}）がこの文書を変えるが、関連 spec にない`)
  }
  console.log(`関連 spec の漏れ（次の commit）：${missing.length}`)
  process.exit(missing.length ? 1 : 0)
}

if (isMain(import.meta.url)) {
  const exclusions = readExclusions(exclusionsPath('related-specs-exclusions.json'))
  const excludedPrefixes = Object.keys(exclusions)
  if (process.argv[2] === '--staged') checkStaged(process.argv[3], excludedPrefixes)
  exitIfShallow()
  const files = listGitFiles(['docs/*.md'])
  exitIfNoFiles(files, '文書')
  // 除外が必要なのは、そのタスク番号の commit が履歴にあるものだけ（履歴が足りる場合に限って調べる）
  const merging = spawnSync('git', ['rev-parse', '-q', '--verify', 'MERGE_HEAD'], { encoding: 'utf8' }).status === 0
  const allSubjects = execFileSync('git', historyLogArgs(merging), { encoding: 'utf8', maxBuffer: 1 << 28 }).split('\n').filter(Boolean)
  const { failures: exclusionProblems, warnings } = checkRelatedExclusions(exclusions, (prefix) => isPrefixUsed(prefix, allSubjects), process.env.CI === 'true')
  for (const line of [...exclusionProblems, ...warnings]) console.log(line)
  let count = 0
  for (const file of files) {
    const markdown = readFileSync(file, 'utf8')
    for (const line of findBadRelatedLinks(file, markdown, isDirectory)) {
      console.log(line)
      count += 1
    }
    const related = parseRelatedSpecs(markdown)
    if (!related) continue
    const subjects = execFileSync('git', ['log', '--follow', '--format=%s', '--', file], { encoding: 'utf8' })
      .split('\n')
      .filter(Boolean)
    for (const spec of findMissingSpecs(related, subjects, excludedPrefixes)) {
      console.log(`${file}：spec ${spec} がこの文書を変えたが、関連 spec にない`)
      count += 1
    }
  }
  count += exclusionProblems.length
  console.log(`関連 spec の漏れ：${count}`)
  process.exit(count ? 1 : 0)
}
