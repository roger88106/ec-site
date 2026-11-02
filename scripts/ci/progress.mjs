// docs/08-spec/archive/1to10/ の spec（番号を 0 で埋めない）の tasks が、全て済んでいることを確認する（AGENTS.md「完了の基準」）。進行中の spec（docs/08-spec/ の直下）と MVP の spec は見ない。
// - tasks のディレクトリがあればその中のタスクを数える。なければ（規模 M）spec.md の中のタスクの行を数える
// - タスクが 1 つもない spec、済んでいないタスクがある spec は、食い違いとして報告する
// - docs/08-spec/archive/1to10/ の下の spec が 0 件なら、食い違いとして報告する（何も見ずに通らないため）
// - docs/08-spec/archive/ の下に mvp と 1to10 以外のディレクトリがあれば、食い違いとして報告する。
//   1→10 の spec を別の場所に移すと、この検査が読まずに通るため
// - docs/08-spec/archive/mvp/ の下のディレクトリの名前が、MVP の形（0 で始まる 3 桁の番号）でなければ、食い違いとして報告する。
//   1→10 の spec を誤って mvp に移すと、この検査が読まずに通るため
// MVP の進捗（README の進捗の表、MVP の完成までの一覧、MVP の spec の tasks の照合）は見ない。MVP の spec は完了してアーカイブに移した。
// 失敗したとき：アーカイブに移すタスクを tasks の最後にし、その commit で全てのタスクのチェックボックスを [x] にする。
// アーカイブの後、マージの前に追加するタスクは、アーカイブのタスクの後に、済ませる commit で [x] の行として追加する（開発の進め方の「規模」）。
// 除外：なし。
// 使い方：node scripts/ci/progress.mjs（リポジトリのルートで）
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { ARCHIVE, listSpecDirs } from './lib/spec-dirs.mjs'
import { isMain } from './lib/is-main.mjs'

/** 1→10 の spec をアーカイブしたディレクトリ（docs/08-spec/ から） */
const ARCHIVE_1TO10 = `${ARCHIVE}/1to10`

/** アーカイブの下に置いてよいまとまり（MVP と 1→10） */
const ARCHIVE_GROUPS = ['mvp', '1to10']

/** tasks のタスクの行（字下げした補足の箇条書きは数えない） */
const TASK_LINE = /^- \[( |x)\] \*\*T/

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
 * @param {string} specsDir docs/08-spec のパス
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

/** MVP の spec のディレクトリの名前（0 で始まる 3 桁の番号。1→10 の spec は 0 で埋めない） */
const MVP_SPEC_DIR = /^0\d{2}-/

/**
 * アーカイブの下のまとまり（ディレクトリ）が、mvp と 1to10 だけかを確認し、mvp の下に MVP の形でない spec がないかを確認する。
 * 理由：1→10 の spec を別のまとまりや mvp に移すと、この検査が読まずに通るため。
 *
 * @param {string} specsDir docs/08-spec のパス
 * @returns {string[]} 知らないまとまりと、mvp の下の MVP の形でない spec の説明（名前の順）
 */
export function checkArchiveGroups(specsDir) {
  const archiveDir = path.join(specsDir, ARCHIVE)
  if (!existsSync(archiveDir)) return []
  const unknown = readdirSync(archiveDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && !ARCHIVE_GROUPS.includes(entry.name))
    .map((entry) => entry.name)
    .sort()
    .map((name) => `${ARCHIVE}/${name}：アーカイブの下のまとまりは ${ARCHIVE_GROUPS.join('、')} だけにする（1→10 の spec は ${ARCHIVE_1TO10}/ に移す）`)
  const mvpDir = path.join(archiveDir, 'mvp')
  const misplaced = existsSync(mvpDir)
    ? readdirSync(mvpDir, { withFileTypes: true })
        .filter((entry) => entry.isDirectory() && !MVP_SPEC_DIR.test(entry.name))
        .map((entry) => entry.name)
        .sort()
        .map((name) => `${ARCHIVE}/mvp/${name}：mvp の下の spec は、0 で始まる 3 桁の番号の名前だけにする（1→10 の spec を誤って mvp/ に移していないか。${ARCHIVE_1TO10}/ に移す）`)
    : []
  return [...unknown, ...misplaced]
}

/**
 * 1→10 の spec の tasks に、済んでいないタスクがないか確認する。spec が 0 件なら、何も見ずに通らないよう食い違いにする。
 *
 * @param {Map<string, { total: number, done: number }>} tasks spec のディレクトリの名前 → タスクの数
 * @returns {string[]} 食い違いの説明（名前の順）
 */
export function checkArchivedTasks(tasks) {
  if (tasks.size === 0) return [`${ARCHIVE_1TO10}/ の下に spec が 1 件もない（置き場所が変わっていないか確認する）`]
  return [...tasks.keys()]
    .sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
    .filter((name) => tasks.get(name).total === 0 || tasks.get(name).done < tasks.get(name).total)
    .map((name) =>
      tasks.get(name).total === 0
        ? `1→10 の spec ${name}：tasks がない`
        : `1→10 の spec ${name}：tasks に残りが ${tasks.get(name).total - tasks.get(name).done} ある（アーカイブに移すタスクを tasks の最後にし、その commit で全てのタスクを \`[x]\` にする。アーカイブの後に追加するタスクは \`[x]\` の行で追加する。開発の進め方の「規模」）`,
    )
}

if (isMain(import.meta.url)) {
  const problems = [...checkArchiveGroups('docs/08-spec'), ...checkArchivedTasks(readArchivedTasks('docs/08-spec'))]
  for (const problem of problems) console.log(problem)
  console.log(`進捗の食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
