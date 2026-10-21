// spec の進捗が、README の進捗の表、MVP の完成までの一覧、tasks のチェックボックスで一致することを確認する（AGENTS.md「完了の基準」）。
// - tasks がすべて済んだ spec は、2 つの表で「完了」。「完了」の spec は、tasks がすべて済んでいる
// - 2 つの表の両方にある spec は、状態が同じ
// 対象は spec 011 以降（それより前の spec は、tasks の書き方がそろっていないため）。状態は、表の最後の列の最初の句点まで
// 使い方：node scripts/ci/progress.mjs（リポジトリのルートで）
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** 対象の最初の spec の番号 */
const FIRST_SPEC = 11

/** 表の 1 行目のセルの spec の番号（リンクの文字） */
const SPEC_CELL = /^\[(\d{3,})\]/

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
    .filter((spec) => Number(spec) >= firstSpec)
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

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const specsDir = 'docs/specs'
  const tasks = new Map()
  for (const dir of readdirSync(specsDir, { withFileTypes: true }).filter((entry) => entry.isDirectory())) {
    const match = dir.name.match(/^(\d{3,})-/)
    const tasksDir = path.join(specsDir, dir.name, 'tasks')
    if (!match || !existsSync(tasksDir)) continue
    const texts = readdirSync(tasksDir)
      .filter((file) => file.endsWith('.md'))
      .map((file) => readFileSync(path.join(tasksDir, file), 'utf8'))
    tasks.set(match[1], countTasks(texts))
  }
  const problems = checkProgress(
    parseStatusTable(readFileSync('README.md', 'utf8')),
    parseStatusTable(readFileSync(path.join(specsDir, 'mvp.md'), 'utf8')),
    tasks,
    FIRST_SPEC,
  )
  for (const problem of problems) console.log(problem)
  console.log(`進捗の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
