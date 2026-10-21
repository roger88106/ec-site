// 設計の文書（概要の表に「関連 spec」の行を持つ文書）を変えた spec が、その行にあることを確認する（skill docs 第 2 節）。
// spec は、文書を変えた commit のメッセージの 1 行目のタスク番号（T011-01-01 の形）から読む。古い形式のタスク番号（T3101 など）は
// spec を一意に決められないので見ない。文書の移動は git log --follow で追う。
// 設計を変えない作業（言い回しの修正、文書の移動など）の段階は、related-specs-exclusions.json に、タスク番号の前置き（T018-05）と理由を書く。
// 使い方：node scripts/ci/related-specs.mjs（リポジトリのルートで。commit の履歴が必要なので、shallow clone では失敗にする）
// 次の commit の照合：node scripts/ci/related-specs.mjs --staged <メッセージのファイル>（commit-msg の hook から呼ぶ。ステージした文書とメッセージの 1 行目を照合する）
import { execFileSync, execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** 概要の表の「関連 spec」の行 */
const RELATED_ROW = /^\|\s*関連 spec\s*\|(.*)\|\s*$/m

/** 新しい形式のタスク番号 */
const TASK_ID = /T(\d{3,})-\d{2}-\d{2}/g

/**
 * 概要の表の「関連 spec」の行から、spec の番号を読む。
 *
 * @param {string} markdown 設計の文書
 * @returns {string[] | null} spec の番号（書いた順）。行がなければ null
 */
export function parseRelatedSpecs(markdown) {
  const row = markdown.match(RELATED_ROW)
  return row ? [...row[1].matchAll(/\[(\d{3,})\]/g)].map((match) => match[1]) : null
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

/** 次の commit（ステージした内容とメッセージ）を照合する。commit-msg の hook から呼ぶ */
function checkStaged(messageFile, excludedPrefixes) {
  const subject = readFileSync(messageFile, 'utf8').split(/\r?\n/)[0]
  const docs = execFileSync('git', ['diff', '--cached', '--name-only', '--diff-filter=AMR', '--', 'docs/*.md'], { encoding: 'utf8' })
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

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const exclusionsFile = path.join(path.dirname(fileURLToPath(import.meta.url)), 'related-specs-exclusions.json')
  const excludedPrefixes = Object.keys(JSON.parse(readFileSync(exclusionsFile, 'utf8')))
  if (process.argv[2] === '--staged') checkStaged(process.argv[3], excludedPrefixes)
  if (execSync('git rev-parse --is-shallow-repository', { encoding: 'utf8' }).trim() === 'true') {
    console.log('commit の履歴が足りない（shallow clone）。CI では checkout に fetch-depth: 0 を付ける')
    process.exit(1)
  }
  const files = execSync('git ls-files "docs/*.md"', { encoding: 'utf8' }).split('\n').filter(Boolean)
  let count = 0
  for (const file of files) {
    const related = parseRelatedSpecs(readFileSync(file, 'utf8'))
    if (!related) continue
    const subjects = execFileSync('git', ['log', '--follow', '--format=%s', '--', file], { encoding: 'utf8' })
      .split('\n')
      .filter(Boolean)
    for (const spec of findMissingSpecs(related, subjects, excludedPrefixes)) {
      console.log(`${file}：spec ${spec} がこの文書を変えたが、関連 spec にない`)
      count += 1
    }
  }
  console.log(`関連 spec の漏れ：${count}`)
  process.exit(count ? 1 : 0)
}
