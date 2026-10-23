// spec の進捗が、README の進捗の表、MVP の完成までの一覧、tasks のチェックボックスで一致することを確認する（AGENTS.md「完了の基準」）。
// - tasks がすべて済んだ spec は、2 つの表で「完了」。「完了」の spec は、tasks がすべて済んでいる
// - 2 つの表の両方にある spec は、状態が同じ
// - docs/specs/archive/1to10/ の spec（番号を 0 で埋めない）は、tasks がすべて済んでいる。進行中の spec（docs/specs/ の直下）は見ない
// 表の照合の対象は MVP の spec 011 以降（0 埋めの 3 桁。それより前の spec は、tasks の書き方がそろっていないため）。状態は、表の最後の列の最初の句点まで
// 使い方：node scripts/ci/progress.mjs（リポジトリのルートで）
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { findMvpList, listSpecDirs } from './lib/spec-dirs.mjs'

/** 対象の最初の spec の番号 */
const FIRST_SPEC = 11

/** MVP の spec の番号（0 で始まる 3 桁。123-xxx のような 1→10 の spec は含めない） */
const MVP_SPEC = /^0\d{2}$/

/** 表の 1 行目のセルの spec の番号（リンクの文字） */
const SPEC_CELL = /^\[(0\d{2})\]/

/** 1→10 の spec をアーカイブしたディレクトリ（docs/specs/ から） */
const ARCHIVE_1TO10 = 'archive/1to10'

/** tasks のタスクの行（字下げした補足の箇条書きは数えない） */
const TASK_LINE = /^- \[( |x)\] \*\*T/

/**
 * 進捗の表から、spec の番号と状態を読む。
 *
 * @param {string} markdown 表を含む文書
 * @returns {Map<string, string>} spec の番号 → 状態（最後の列の最初の句点まで）
 */
export function parseStatusTable(markdown) {
  const statuses = new Map()
  for (const line of markdown.split(/\r?\n/)) {
    const cells = line.split('|').slice(1, -1).map((cell) => cell.trim())
    const match = cells[0]?.match(SPEC_CELL)
    if (!match || cells.length < 2) continue
    statuses.set(match[1], cells.at(-1).split('。')[0])
  }
  return statuses
}

/**
 * tasks のファイルのチェックボックスを数える。
 *
 * @param {string[]} texts tasks のファイルの中身
 * @returns {{ total: number, done: number }} タスクの数と、済んだタスクの数
 */
export function countTasks(texts) {
  const lines = texts.flatMap((text) => text.split(/\r?\n/)).filter((line) => TASK_LINE.test(line))
  return { total: lines.length, done: lines.filter((line) => line.startsWith('- [x]')).length }
}

/**
 * 進捗の食い違いを探す。
 *
 * @param {Map<string, string>} readme README の進捗の表
 * @param {Map<string, string>} mvp MVP の完成までの一覧
 * @param {Map<string, { total: number, done: number }>} tasks spec の番号 → タスクの数
 * @param {number} firstSpec 対象の最初の spec の番号
 * @returns {string[]} 食い違いの説明（spec の番号の順）
 */
export function checkProgress(readme, mvp, tasks, firstSpec) {
  const tables = [
    ['README の', readme],
    ['MVP の完成までの一覧の', mvp],
  ]
  const specs = [...new Set([...readme.keys(), ...mvp.keys(), ...tasks.keys()])]
    .filter((spec) => MVP_SPEC.test(spec) && Number(spec) >= firstSpec)
    .sort()
  const problems = []
  for (const spec of specs) {
    const count = tasks.get(spec)
    const hasTasks = Boolean(count) && count.total > 0
    const allDone = hasTasks && count.done === count.total
    const completed = tables.filter(([, table]) => table.get(spec) === '完了')
    for (const [name, table] of tables) {
      if (allDone && table.has(spec) && table.get(spec) !== '完了') {
        problems.push(`spec ${spec}：tasks はすべて済んだが、${name}状態が「${table.get(spec)}」`)
      }
    }
    if (hasTasks && !allDone) {
      for (const [name] of completed) {
        problems.push(`spec ${spec}：${name}状態が「完了」だが、tasks に残りが ${count.total - count.done} ある`)
      }
    }
    if (readme.has(spec) && mvp.has(spec) && readme.get(spec) !== mvp.get(spec)) {
      problems.push(
        `spec ${spec}：README の状態「${readme.get(spec)}」と、MVP の完成までの一覧の状態「${mvp.get(spec)}」が食い違う`,
      )
    }
    if (!hasTasks) {
      for (const [name] of completed) {
        problems.push(`spec ${spec}：${name}状態が「完了」だが、tasks がない`)
      }
    }
  }
  return problems
}

/**
 * spec（直下とアーカイブ）の tasks のチェックボックスを数える。
 *
 * @param {string} specsDir docs/specs のパス
 * @returns {Map<string, { total: number, done: number }>} spec の番号 → タスクの数
 */
export function readTasks(specsDir) {
  const tasks = new Map()
  for (const { name, dir } of listSpecDirs(specsDir)) {
    const number = name.match(/^(0\d{2})-/)?.[1]
    if (!number) continue
    const texts = readTaskTexts(dir)
    if (texts) tasks.set(number, countTasks(texts))
  }
  return tasks
}

/**
 * spec のディレクトリの tasks のファイルの中身を読む。
 *
 * @param {string} specDir spec のディレクトリ
 * @returns {string[] | null} tasks のファイルの中身。tasks のディレクトリがなければ null
 */
function readTaskTexts(specDir) {
  const tasksDir = path.join(specDir, 'tasks')
  if (!existsSync(tasksDir)) return null
  return readdirSync(tasksDir)
    .filter((file) => file.endsWith('.md'))
    .map((file) => readFileSync(path.join(tasksDir, file), 'utf8'))
}

/**
 * 1→10 の spec（アーカイブ）の tasks のチェックボックスを数える。
 *
 * @param {string} specsDir docs/specs のパス
 * @returns {Map<string, { total: number, done: number }>} spec のディレクトリの名前 → タスクの数。tasks のない spec は total が 0
 */
export function readArchivedTasks(specsDir) {
  const tasks = new Map()
  for (const { name, rel, dir: specDir } of listSpecDirs(specsDir)) {
    if (!rel.startsWith(`${ARCHIVE_1TO10}/`)) continue
    const texts = readTaskTexts(specDir)
    if (texts) {
      tasks.set(name, countTasks(texts))
      continue
    }
    // 規模 M は tasks のディレクトリがなく、spec.md の中にタスクの行がある
    const specFile = path.join(specDir, 'spec.md')
    // どちらにもなければ total が 0 のまま入れ、黙って飛ばさずに報告させる
    tasks.set(name, existsSync(specFile) ? countTasks([readFileSync(specFile, 'utf8')]) : { total: 0, done: 0 })
  }
  return tasks
}

/**
 * 1→10 の spec の tasks に、済んでいないタスクがないか確認する。
 *
 * @param {Map<string, { total: number, done: number }>} tasks spec のディレクトリの名前 → タスクの数
 * @returns {string[]} 食い違いの説明（名前の順）
 */
export function checkArchivedTasks(tasks) {
  return [...tasks.keys()]
    .sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
    .filter((name) => tasks.get(name).total === 0 || tasks.get(name).done < tasks.get(name).total)
    .map((name) =>
      tasks.get(name).total === 0
        ? `1→10 の spec ${name}：tasks がない`
        : `1→10 の spec ${name}：tasks に残りが ${tasks.get(name).total - tasks.get(name).done} ある（アーカイブに移すタスクを tasks の最後にし、その commit で全てのタスクを \`[x]\` にする。アーカイブの後に追加するタスクは \`[x]\` の行で追加する。開発の進め方 第 4.2 節）`,
    )
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const specsDir = 'docs/specs'
  const mvpList = findMvpList(specsDir)
  if (!mvpList) {
    console.log('MVP の完成までの一覧（mvp.md）が、docs/specs/ の直下にもアーカイブの下にもない')
    process.exit(1)
  }
  const problems = [
    ...checkProgress(
      parseStatusTable(readFileSync('README.md', 'utf8')),
      parseStatusTable(readFileSync(mvpList, 'utf8')),
      readTasks(specsDir),
      FIRST_SPEC,
    ),
    ...checkArchivedTasks(readArchivedTasks(specsDir)),
  ]
  for (const problem of problems) console.log(problem)
  console.log(`進捗の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
