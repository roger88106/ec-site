// push していない commit のメッセージを、対応表のとおりに非対話の rebase で書き換える。
// 手で rebase -i を操作すると、途中で止まったまま残ったり、内容を変えてしまったりする。
// この script は、条件を確認してから書き換え、tree（内容）が元と同じことを確認し、失敗したら元に戻す。
//
// 使い方：node scripts/dev/reword.mjs <対応表の JSON>
//   JSON は [{ "commit": "<SHA か短い SHA>", "message": "<新しいメッセージのファイル>" }]。
//   メッセージのファイルの相対パスは、対応表のファイルのある場所から解決する。
// 出力：書き換えた commit（元の短い SHA → 新しい短い SHA と 1 行目）、件数、tree が一致したこと。
// 終了コード：0 成功、1 書き換えなかった（条件に合わない、途中で失敗して戻した）、2 使い方の誤り。
//
// 書き換える前に確認すること：追跡しているファイルに未 commit の変更がない、対応表が読める、
// 対象が HEAD の祖先である、重複がない、push 済み（refs/remotes/ のどれかから届く）でない、
// 範囲（最も古い対象の親から HEAD まで）にマージの commit がない（rebase で平らになるため）。
// どれかに当たれば、状態を変えずに 1 で終わる。
// 各 commit の書き換えは commit の hook（commit-msg など）を通す。hook が拒否すると rebase が途中で止まるので、
// git rebase --abort で戻し、HEAD が元の commit であることを確認する。
//
// 失敗したときの見方：標準エラー出力の最初の行に理由が出る。途中で止まったときは git の出力も続く。
// 元に戻せなかったときは、その旨と元の HEAD の SHA が出るので、git reset --hard <SHA> で手で戻す。
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { isMain } from '../ci/lib/is-main.mjs'

const self = fileURLToPath(import.meta.url)

/** 条件に合わないときの例外。メッセージをそのまま利用者に見せる */
class Refusal extends Error {}

/**
 * rebase の todo を書き換える。対象の commit の pick の行の後に、メッセージを差し替える exec の行を入れる。
 * entries は { commit: 完全な SHA, message: メッセージのファイルの絶対パス }。todo の SHA は短縮形なので前方一致で照合する
 */
export function editTodo(todo, entries) {
  const lines = todo.split('\n')
  const out = []
  for (const line of lines) {
    out.push(line)
    const match = /^(?:pick|p)\s+([0-9a-f]+)(?:\s|$)/.exec(line)
    if (!match) continue
    const entry = entries.find((e) => e.commit.startsWith(match[1]))
    if (entry) out.push(`exec git commit --amend -F "${entry.message.replaceAll('\\', '/')}"`)
  }
  return out.join('\n')
}

function git(cwd, args, options = {}) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', ...options })
  return { status: result.status, stdout: (result.stdout ?? '').trim(), stderr: (result.stderr ?? '').trim() }
}

function gitOrThrow(cwd, args) {
  const result = git(cwd, args)
  if (result.status !== 0) throw new Refusal(`git ${args.join(' ')} が失敗した：${result.stderr}`)
  return result.stdout
}

/** 対応表を読み、commit を完全な SHA に解決し、メッセージのファイルを読む。誤りがあれば Refusal */
function loadPlan(mapPath, cwd) {
  let map
  try {
    map = JSON.parse(readFileSync(mapPath, 'utf8'))
  } catch (error) {
    throw new Refusal(`対応表が読めない：${mapPath}（${error.message}）`)
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
    } catch (error) {
      throw new Refusal(`メッセージのファイルが読めない：${message}（${error.message}）`)
    }
    if (text.trim() === '') throw new Refusal(`メッセージのファイルが空：${message}`)
    return { commit, message }
  })
}

/** 範囲の commit と tree を、古い順に返す。base が null なら全ての commit */
function listCommits(cwd, base) {
  const range = base ? [`${base}..HEAD`] : ['HEAD']
  const out = gitOrThrow(cwd, ['log', '--reverse', '--format=%H %T', ...range])
  return out === '' ? [] : out.split('\n').map((line) => line.split(' '))
}

/** 書き換えを実行する。成功なら { lines }、条件に合わなければ Refusal を投げる */
export function reword(mapPath, cwd = process.cwd()) {
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
    const rebase = git(cwd, ['-c', `sequence.editor=${editor}`, 'rebase', '-i', ...(isRoot ? ['--root'] : [base])], {
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

/** rebase を中止し、HEAD を元の commit に戻す。戻せなければ Refusal で SHA を知らせる */
function restore(cwd, head) {
  git(cwd, ['rebase', '--abort'])
  if (gitOrThrowSafe(cwd, ['rev-parse', 'HEAD']) !== head) {
    git(cwd, ['reset', '--hard', head])
  }
  if (gitOrThrowSafe(cwd, ['rev-parse', 'HEAD']) !== head) {
    throw new Refusal(`元に戻せなかった。git reset --hard ${head} で手で戻す`)
  }
}

function gitOrThrowSafe(cwd, args) {
  return git(cwd, args).stdout
}

/** todo の編集の役。git が sequence.editor として、todo のファイルの絶対パスを最後に付けて呼ぶ */
function runEditTodo(planFile, todoFile) {
  const plan = JSON.parse(readFileSync(planFile, 'utf8'))
  writeFileSync(todoFile, editTodo(readFileSync(todoFile, 'utf8'), plan))
}

function main(argv) {
  const [first, planFile, todoFile] = argv
  if (first === '--edit-todo') {
    if (argv.length !== 3) return 2
    runEditTodo(planFile, todoFile)
    return 0
  }
  if (argv.length !== 1) {
    process.stderr.write('使い方：node scripts/dev/reword.mjs <対応表の JSON>\n')
    return 2
  }
  try {
    const { lines } = reword(argv[0])
    process.stdout.write(`${lines.join('\n')}\n`)
    return 0
  } catch (error) {
    if (!(error instanceof Refusal)) throw error
    process.stderr.write(`${error.message}\n`)
    return 1
  }
}

if (isMain(import.meta.url)) {
  process.exit(main(process.argv.slice(2)))
}
