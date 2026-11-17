// push していない commit のメッセージを、対応表のとおりに非対話の rebase で書き換える。
// 手で rebase -i を操作すると、途中で止まったまま残ったり、内容を変えてしまったりする。
// この script は、条件を確認してから書き換え、tree（内容）が元と同じことを確認し、失敗したら元に戻す。
//
// 条件（進行中の操作がない、未 commit の変更がない、対象が HEAD の祖先で push 済みでない、範囲にマージの commit がない）は reword が確認する。
// 各 commit の書き換えは commit の hook（commit-msg など）を通す。hook が拒否して rebase が止まったら、元の HEAD に戻す。
//
// 使い方：node scripts/dev/tools/reword.mjs <対応表の JSON>
//   JSON は [{ "commit": "<SHA か短い SHA>", "message": "<新しいメッセージのファイル>" }]。
//   メッセージのファイルの相対パスは、対応表のファイルのある場所から解決する。
//
// 出力：書き換えた commit（元の短い SHA → 新しい短い SHA と 1 行目）、件数、tree が一致したこと。
//
// 終了コード：0 成功、1 書き換えなかった（条件に合わない、途中で失敗して戻した）、2 使い方の誤り。
//
// 失敗したときの見方：標準エラー出力の最初の行に理由が出る。途中で止まったときは git の出力も続く。
// 元に戻せなかったときは、その旨と元の HEAD の SHA が出るので、git reset --hard <SHA> で手で戻す。
import { spawnSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const self = fileURLToPath(import.meta.url)

/**
 * 条件に合わないときの例外。
 *
 * メッセージをそのまま利用者に見せる
 */
class Refusal extends Error {}

/**
 * rebase の todo を書き換える。
 *
 * 対象の commit の pick の行の後に、メッセージを差し替える exec の行を入れる。
 * todo の SHA は、rebase を core.abbrev=no で実行して完全な形にしてあるので、完全な SHA の一致で照合する
 * （短縮形の前方一致だと、前方が同じ 2 つの対象で、最初の対象のメッセージが両方に当たる）
 *
 * @param {string} todo rebase の todo の中身
 * @param {{ commit: string, message: string }[]} entries 完全な SHA と、メッセージのファイルの絶対パス
 * @returns {string} 書き換えた todo
 */
export function editTodo(todo, entries) {
  const lines = todo.split('\n')
  const out = []
  for (const line of lines) {
    out.push(line)
    const match = /^(?:pick|p)\s+([0-9a-f]+)(?:\s|$)/.exec(line)
    if (!match) continue
    const entry = entries.find((e) => e.commit === match[1])
    if (entry) out.push(`exec git commit --amend -F "${entry.message.replaceAll('\\', '/')}"`)
  }
  return out.join('\n')
}

/**
 * git を実行し、終了コードと出力を返す。
 *
 * @param {string} cwd 実行するディレクトリ
 * @param {string[]} args git の引数
 * @param {import('node:child_process').SpawnSyncOptions} [options] spawnSync に加える指定
 * @returns {{ status: number | null, stdout: string, stderr: string }} 終了コードと、前後の空白を除いた出力
 */
function git(cwd, args, options = {}) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', ...options })
  return { status: result.status, stdout: (result.stdout ?? '').trim(), stderr: (result.stderr ?? '').trim() }
}

/**
 * git を実行し、標準出力を返す。
 *
 * 失敗したら、git の標準エラー出力を付けた Refusal を投げる。
 *
 * @param {string} cwd 実行するディレクトリ
 * @param {string[]} args git の引数
 * @returns {string} 前後の空白を除いた標準出力
 */
function gitOrThrow(cwd, args) {
  const result = git(cwd, args)
  if (result.status !== 0) throw new Refusal(`git ${args.join(' ')} が失敗した：${result.stderr}`)
  return result.stdout
}

/**
 * 対応表を読み、commit を完全な SHA に解決し、メッセージのファイルを読む。
 *
 * 誤りがあれば Refusal を投げる。
 *
 * @param {string} mapPath 対応表の JSON のパス
 * @param {string} cwd リポジトリのディレクトリ
 * @returns {{ commit: string, message: string }[]} 完全な SHA と、メッセージのファイルの絶対パス
 */
function loadPlan(mapPath, cwd) {
  let map
  try {
    map = JSON.parse(readFileSync(mapPath, 'utf8'))
  } catch (e) {
    throw new Refusal(`対応表が読めない：${mapPath}（${e.message}）`)
  }
  if (!Array.isArray(map) || map.length === 0) throw new Refusal('対応表は、1 件以上の配列にする')
  const baseDir = path.dirname(path.resolve(mapPath))
  const seen = new Set()
  return map.map((item) => {
    if (typeof item?.commit !== 'string' || typeof item?.message !== 'string') {
      throw new Refusal('対応表の各要素には、文字列の commit と message が要る')
    }
    const resolved = git(cwd, ['rev-parse', '--verify', '--quiet', `${item.commit}^{commit}`])
    if (resolved.status !== 0 || !resolved.stdout) throw new Refusal(`commit を解決できない：${item.commit}`)
    const commit = resolved.stdout
    if (seen.has(commit)) throw new Refusal(`commit が重複している：${item.commit}`)
    seen.add(commit)
    const message = path.resolve(baseDir, item.message)
    let text
    try {
      text = readFileSync(message, 'utf8')
    } catch (e) {
      throw new Refusal(`メッセージのファイルが読めない：${message}（${e.message}）`)
    }
    if (text.trim() === '') throw new Refusal(`メッセージのファイルが空：${message}`)
    return { commit, message }
  })
}

/**
 * 範囲の commit と tree を、古い順に返す。
 *
 * @param {string} cwd リポジトリのディレクトリ
 * @param {string | null} base 範囲の起点（含まない）。null なら全ての commit
 * @returns {string[][]} commit と tree の SHA の組
 */
function listCommits(cwd, base) {
  const range = base ? [`${base}..HEAD`] : ['HEAD']
  const out = gitOrThrow(cwd, ['log', '--reverse', '--format=%H %T', ...range])
  return out === '' ? [] : out.split('\n').map((line) => line.split(' '))
}

/** 進行中の操作の名前と、git のディレクトリの中でその印になるファイルかディレクトリ */
const OPERATION_MARKERS = [
  ['rebase', 'rebase-merge'],
  ['rebase', 'rebase-apply'],
  ['merge', 'MERGE_HEAD'],
  ['cherry-pick', 'CHERRY_PICK_HEAD'],
  ['revert', 'REVERT_HEAD'],
  ['bisect', 'BISECT_LOG'],
]

/**
 * rebase・merge・cherry-pick などが進行中なら、その名前を返す。
 *
 * 利用者が先に始めた操作を、自分の失敗の巻き戻しで中止しないために見る。
 *
 * @param {string} cwd リポジトリのディレクトリ
 * @returns {string | null} 進行中の操作の名前。なければ null
 */
function operationInProgress(cwd) {
  for (const [name, marker] of OPERATION_MARKERS) {
    const found = git(cwd, ['rev-parse', '--git-path', marker])
    if (found.status === 0 && existsSync(path.resolve(cwd, found.stdout))) return name
  }
  return null
}

/**
 * 書き換えを実行する。
 *
 * 条件に合わなければ Refusal を投げる。
 *
 * @param {string} mapPath 対応表の JSON のパス
 * @param {string} [cwd] リポジトリのディレクトリ
 * @returns {{ lines: string[] }} 出力する行
 */
function reword(mapPath, cwd = process.cwd()) {
  const inProgress = operationInProgress(cwd)
  if (inProgress !== null) {
    throw new Refusal(`${inProgress}が進行中なので書き換えない（状態は変えていない）。終えるか中止してから、もう一度流す`)
  }
  if (gitOrThrow(cwd, ['status', '--porcelain', '--untracked-files=no']) !== '') {
    throw new Refusal('追跡しているファイルに未 commit の変更がある（ステージを含む）')
  }
  const plan = loadPlan(mapPath, cwd)
  const head = gitOrThrow(cwd, ['rev-parse', 'HEAD'])

  for (const entry of plan) {
    if (git(cwd, ['merge-base', '--is-ancestor', entry.commit, 'HEAD']).status !== 0) {
      throw new Refusal(`HEAD の祖先でない commit：${entry.commit}`)
    }
    const remotes = gitOrThrow(cwd, ['for-each-ref', '--contains', entry.commit, '--format=%(refname)', 'refs/remotes/'])
    if (remotes !== '') throw new Refusal(`push 済みの commit：${entry.commit}（${remotes.split('\n')[0]} から届く）`)
  }

  // 最も古い対象を、HEAD までの全ての commit の並びから探す
  const all = listCommits(cwd, null)
  const oldestIndex = Math.min(...plan.map((entry) => all.findIndex(([sha]) => sha === entry.commit)))
  const isRoot = oldestIndex === 0
  const base = isRoot ? null : all[oldestIndex - 1][0]
  const range = base ? [`${base}..HEAD`] : ['HEAD']
  if (gitOrThrow(cwd, ['rev-list', '--merges', ...range]) !== '') {
    throw new Refusal('範囲にマージの commit がある（rebase で平らになるため書き換えない）')
  }
  // 最も古い対象が最初の commit でないとき、その親より後の commit を並べる。--root のときは全て
  const original = listCommits(cwd, base)
  const targets = new Set(plan.map((entry) => entry.commit))

  const work = mkdtempSync(path.join(tmpdir(), 'reword-run-'))
  try {
    const planFile = path.join(work, 'plan.json')
    writeFileSync(planFile, JSON.stringify(plan))
    const q = (p) => `"${p.replaceAll('\\', '/')}"`
    const editor = `${q(process.execPath)} ${q(self)} --edit-todo ${q(planFile)}`
    const rebase = git(cwd, ['-c', 'core.abbrev=no', '-c', `sequence.editor=${editor}`, 'rebase', '-i', ...(isRoot ? ['--root'] : [base])], {
      env: { ...process.env, GIT_EDITOR: 'true' },
    })
    if (rebase.status !== 0) {
      restore(cwd, head)
      throw new Refusal(`rebase が途中で失敗したので元に戻した：${rebase.stdout}\n${rebase.stderr}`)
    }

    const rewritten = listCommits(cwd, base)
    const same =
      rewritten.length === original.length && rewritten.every(([, tree], i) => tree === original[i][1])
    if (!same) {
      restore(cwd, head)
      throw new Refusal('書き換えの後の commit の数か tree が元と違うので元に戻した')
    }

    const lines = []
    original.forEach(([oldSha], i) => {
      if (!targets.has(oldSha)) return
      const newSha = rewritten[i][0]
      const first = gitOrThrow(cwd, ['log', '-1', '--format=%s', newSha])
      lines.push(`${oldSha.slice(0, 7)} → ${newSha.slice(0, 7)} ${first}`)
    })
    lines.push(`${lines.length} 件を書き換えた。${rewritten.length} 件の commit の tree は元と一致した`)
    return { lines }
  } finally {
    rmSync(work, { recursive: true, force: true })
  }
}

/**
 * rebase を中止し、HEAD を元の commit に戻す。
 *
 * 戻せなければ、元の SHA を書いた Refusal を投げる。
 *
 * @param {string} cwd リポジトリのディレクトリ
 * @param {string} head 元の HEAD の SHA
 */
function restore(cwd, head) {
  git(cwd, ['rebase', '--abort'])
  if (gitOutput(cwd, ['rev-parse', 'HEAD']) !== head) {
    git(cwd, ['reset', '--hard', head])
  }
  if (gitOutput(cwd, ['rev-parse', 'HEAD']) !== head) {
    throw new Refusal(`元に戻せなかった。git reset --hard ${head} で手で戻す`)
  }
}

/**
 * git を実行し、成否によらず標準出力を返す。
 *
 * @param {string} cwd 実行するディレクトリ
 * @param {string[]} args git の引数
 * @returns {string} 前後の空白を除いた標準出力
 */
function gitOutput(cwd, args) {
  return git(cwd, args).stdout
}

/**
 * todo の編集の役。
 *
 * git が sequence.editor として、todo のファイルの絶対パスを最後に付けて呼ぶ。
 *
 * @param {string} planFile 解決した対応表の JSON のパス
 * @param {string} todoFile rebase の todo のファイル
 */
function runEditTodo(planFile, todoFile) {
  const plan = JSON.parse(readFileSync(planFile, 'utf8'))
  writeFileSync(todoFile, editTodo(readFileSync(todoFile, 'utf8'), plan))
}

/**
 * 引数を読み、書き換えか todo の編集を実行する。
 *
 * @param {string[]} argv コマンドの引数
 * @returns {number} 終了コード
 */
function main(argv) {
  const [first, planFile, todoFile] = argv
  if (first === '--edit-todo') {
    if (argv.length !== 3) return 2
    runEditTodo(planFile, todoFile)
    return 0
  }
  if (argv.length !== 1) {
    process.stderr.write('使い方：node scripts/dev/tools/reword.mjs <対応表の JSON>\n')
    return 2
  }
  try {
    const { lines } = reword(argv[0])
    process.stdout.write(`${lines.join('\n')}\n`)
    return 0
  } catch (e) {
    if (!(e instanceof Refusal)) throw e
    process.stderr.write(`${e.message}\n`)
    return 1
  }
}

if (import.meta.main) {
  process.exit(main(process.argv.slice(2)))
}
