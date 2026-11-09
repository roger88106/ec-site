// reword.mjs（push していない commit のメッセージを書き換える script）のテスト（node --test scripts/dev/reword.test.mjs）
// os.tmpdir() の下の一時のリポジトリの中だけで行い、テストの後に消す
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, beforeEach, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../ci/lib/git-env.mjs'
import { editTodo } from './reword.mjs'

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'reword.mjs')

let root
let counter = 0

// commit の hook（pre-commit）の中では GIT_DIR などが commit 中のリポジトリを指すので、外した環境で git を実行する
const env = withoutGitEnv()

before(() => {
  root = mkdtempSync(path.join(tmpdir(), 'reword-'))
})

after(() => {
  rmSync(root, { recursive: true, force: true })
})

function git(dir, ...args) {
  const result = spawnSync('git', args, { cwd: dir, encoding: 'utf8', env })
  assert.equal(result.status, 0, `git ${args.join(' ')}\n${result.stdout}${result.stderr}`)
  return result.stdout.trim()
}

/** 一時のリポジトリを作り、ファイル 1 つを変える commit を messages の数だけ積む。各 commit の SHA を古い順に返す */
function makeRepo(messages) {
  const dir = path.join(root, `repo-${counter++}`)
  mkdirSync(dir)
  git(dir, 'init', '-q', '-b', 'main')
  git(dir, 'config', 'user.name', 'tester')
  git(dir, 'config', 'user.email', 'tester@example.com')
  git(dir, 'config', 'core.autocrlf', 'false')
  messages.forEach((message, index) => {
    writeFileSync(path.join(dir, 'a.txt'), `${index}\n`)
    git(dir, 'add', 'a.txt')
    git(dir, 'commit', '-q', '-m', message)
  })
  const shas = git(dir, 'rev-list', '--reverse', 'HEAD').split('\n')
  return { dir, shas }
}

/** 対応表とメッセージのファイルを書いて script を実行する。entries は { commit, message（本文） } */
function run(dir, entries, name = 'map') {
  const work = path.join(root, `work-${counter++}`)
  mkdirSync(work)
  const map = entries.map((entry, index) => {
    const file = `msg-${name}-${index}.txt`
    writeFileSync(path.join(work, file), entry.message)
    return { commit: entry.commit, message: file }
  })
  const mapFile = path.join(work, `${name}.json`)
  writeFileSync(mapFile, JSON.stringify(map))
  return spawnSync('node', [script, mapFile], { cwd: dir, encoding: 'utf8', env })
}

const subject = (dir, rev) => git(dir, 'log', '-1', '--format=%s', rev)
const trees = (dir) => git(dir, 'log', '--reverse', '--format=%T')
const subjects = (dir) => git(dir, 'log', '--reverse', '--format=%s')

describe('todo の編集', () => {
  test('対象の commit の pick の行の後に exec を入れ、対象でない行は変えない', () => {
    const a = 'a'.repeat(40)
    const b = 'b'.repeat(40)
    const c = 'c'.repeat(40)
    const todo = `pick ${a} one\npick ${b} two\npick ${c} three\n\n# comment\n`
    const out = editTodo(todo, [{ commit: b, message: 'C:/tmp/m.txt' }])
    assert.equal(
      out,
      `pick ${a} one\npick ${b} two\nexec git commit --amend -F "C:/tmp/m.txt"\npick ${c} three\n\n# comment\n`,
    )
  })

  test('前方が同じ 2 つの対象は、それぞれ自分のメッセージを受ける', () => {
    const first = `abcdef1${'0'.repeat(33)}`
    const second = `abcdef1${'2'.repeat(33)}`
    const todo = `pick ${first} one\npick ${second} two\n`
    const out = editTodo(todo, [
      { commit: first, message: 'C:/tmp/first.txt' },
      { commit: second, message: 'C:/tmp/second.txt' },
    ])
    assert.equal(
      out,
      `pick ${first} one\nexec git commit --amend -F "C:/tmp/first.txt"\npick ${second} two\nexec git commit --amend -F "C:/tmp/second.txt"\n`,
    )
  })

  test('短縮形の SHA の行は、前方一致で対象にしない（完全な SHA の一致だけ）', () => {
    const first = `abcdef1${'0'.repeat(33)}`
    const second = `abcdef1${'2'.repeat(33)}`
    const todo = 'pick abcdef1 one\npick abcdef1 two\n'
    assert.equal(
      editTodo(todo, [
        { commit: first, message: 'C:/tmp/first.txt' },
        { commit: second, message: 'C:/tmp/second.txt' },
      ]),
      todo,
    )
  })
})

describe('書き換え', () => {
  test('メッセージだけが変わり、tree は同じで、対象外の commit のメッセージは同じ', () => {
    const { dir, shas } = makeRepo(['first', 'second', 'third'])
    const beforeTrees = trees(dir)
    const result = run(dir, [{ commit: shas[1].slice(0, 8), message: 'spec: 二つ目を直す\n\n本文\n' }])
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.equal(subjects(dir), 'first\nspec: 二つ目を直す\nthird')
    assert.equal(trees(dir), beforeTrees)
    assert.match(result.stdout, /spec: 二つ目を直す/)
  })

  test('最初の commit も書き換えられる', () => {
    const { dir, shas } = makeRepo(['first', 'second'])
    const result = run(dir, [{ commit: shas[0], message: 'root: 直した\n' }])
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.equal(subjects(dir), 'root: 直した\nsecond')
  })

  test('複数の commit を一度に書き換えられる', () => {
    const { dir, shas } = makeRepo(['one', 'two', 'three', 'four'])
    const result = run(dir, [
      { commit: shas[3], message: 'four2\n' },
      { commit: shas[1], message: 'two2\n' },
    ])
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.equal(subjects(dir), 'one\ntwo2\nthree\nfour2')
  })

  test('push 済みの commit より新しい commit は書き換えられる', () => {
    const { dir, shas } = makeRepo(['one', 'two', 'three'])
    const remote = path.join(root, `remote-${counter++}.git`)
    git(root, 'init', '-q', '--bare', remote)
    git(dir, 'remote', 'add', 'origin', remote)
    git(dir, 'push', '-q', 'origin', `${shas[0]}:refs/heads/main`)
    git(dir, 'fetch', '-q', 'origin')
    const result = run(dir, [{ commit: shas[2], message: 'three2\n' }])
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.equal(subjects(dir), 'one\ntwo\nthree2')
  })
})

describe('拒否', () => {
  function assertUntouched(dir, head, before) {
    assert.equal(git(dir, 'rev-parse', 'HEAD'), head)
    assert.equal(subjects(dir), before)
    assert.equal(git(dir, 'status', '--porcelain', '--untracked-files=no'), '')
  }

  test('push 済みの commit は書き換えず、終了コード 1 で止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two', 'three'])
    const remote = path.join(root, `remote-${counter++}.git`)
    git(root, 'init', '-q', '--bare', remote)
    git(dir, 'remote', 'add', 'origin', remote)
    git(dir, 'push', '-q', 'origin', `${shas[1]}:refs/heads/main`)
    git(dir, 'fetch', '-q', 'origin')
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [{ commit: shas[1], message: 'two2\n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stderr, /push 済み/)
    assertUntouched(dir, head, 'one\ntwo\nthree')
  })

  test('追跡しているファイルに未 commit の変更があると、終了コード 1 で止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two'])
    writeFileSync(path.join(dir, 'a.txt'), 'dirty\n')
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [{ commit: shas[0], message: 'one2\n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stderr, /未 commit/)
    assert.equal(git(dir, 'rev-parse', 'HEAD'), head)
    assert.equal(subjects(dir), 'one\ntwo')
  })

  test('ステージだけの変更でも止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two'])
    writeFileSync(path.join(dir, 'a.txt'), 'staged\n')
    git(dir, 'add', 'a.txt')
    const result = run(dir, [{ commit: shas[0], message: 'one2\n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
  })

  test('解決できない commit は、終了コード 1 で止まる', () => {
    const { dir } = makeRepo(['one', 'two'])
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [{ commit: 'deadbeefdeadbeef', message: 'x\n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assertUntouched(dir, head, 'one\ntwo')
  })

  test('メッセージのファイルがないと、終了コード 1 で止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two'])
    const head = git(dir, 'rev-parse', 'HEAD')
    const work = path.join(root, `work-${counter++}`)
    mkdirSync(work)
    const mapFile = path.join(work, 'map.json')
    writeFileSync(mapFile, JSON.stringify([{ commit: shas[0], message: 'no-such.txt' }]))
    const result = spawnSync('node', [script, mapFile], { cwd: dir, encoding: 'utf8', env })
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assertUntouched(dir, head, 'one\ntwo')
  })

  test('メッセージのファイルが空だと、終了コード 1 で止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two'])
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [{ commit: shas[0], message: '  \n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assertUntouched(dir, head, 'one\ntwo')
  })

  test('同じ commit が重複していると、終了コード 1 で止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two'])
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [
      { commit: shas[0], message: 'a\n' },
      { commit: shas[0].slice(0, 10), message: 'b\n' },
    ])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assertUntouched(dir, head, 'one\ntwo')
  })

  test('HEAD の祖先でない commit は、終了コード 1 で止まる', () => {
    const { dir } = makeRepo(['one', 'two'])
    git(dir, 'checkout', '-q', '-b', 'other', 'HEAD~1')
    writeFileSync(path.join(dir, 'b.txt'), 'b\n')
    git(dir, 'add', 'b.txt')
    git(dir, 'commit', '-q', '-m', 'other')
    const otherSha = git(dir, 'rev-parse', 'HEAD')
    git(dir, 'checkout', '-q', 'main')
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [{ commit: otherSha, message: 'x\n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assertUntouched(dir, head, 'one\ntwo')
  })

  test('範囲にマージの commit があると、終了コード 1 で止まる', () => {
    const { dir, shas } = makeRepo(['one', 'two'])
    git(dir, 'checkout', '-q', '-b', 'side')
    writeFileSync(path.join(dir, 'b.txt'), 'b\n')
    git(dir, 'add', 'b.txt')
    git(dir, 'commit', '-q', '-m', 'side')
    git(dir, 'checkout', '-q', 'main')
    writeFileSync(path.join(dir, 'c.txt'), 'c\n')
    git(dir, 'add', 'c.txt')
    git(dir, 'commit', '-q', '-m', 'main-extra')
    git(dir, 'merge', '-q', '--no-ff', '-m', 'merge', 'side')
    const head = git(dir, 'rev-parse', 'HEAD')
    const before = subjects(dir)
    const result = run(dir, [{ commit: shas[1], message: 'two2\n' }])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stderr, /マージ/)
    assertUntouched(dir, head, before)
  })

  test('rebase が進行中なら、状態を変えず、進行中の rebase を残して終了コード 1 で止まる', () => {
    const { dir } = makeRepo(['one', 'two', 'three'])
    // 最初の commit を edit にして止める
    const editor = path.join(root, `seq-editor-${counter++}.mjs`)
    writeFileSync(
      editor,
      "import { readFileSync, writeFileSync } from 'node:fs'\n" +
        'const file = process.argv[2]\n' +
        "writeFileSync(file, readFileSync(file, 'utf8').replace(/^pick /, 'edit '))\n",
    )
    const rebaseEnv = { ...env, GIT_SEQUENCE_EDITOR: `node "${editor.replaceAll('\\', '/')}"` }
    const started = spawnSync('git', ['rebase', '-i', '--root'], { cwd: dir, encoding: 'utf8', env: rebaseEnv })
    assert.equal(started.status, 0, started.stdout + started.stderr)
    const rebaseDir = path.resolve(dir, git(dir, 'rev-parse', '--git-path', 'rebase-merge'))
    assert.equal(existsSync(rebaseDir), true)
    const head = git(dir, 'rev-parse', 'HEAD')

    const result = run(dir, [{ commit: head, message: 'one2\n' }])

    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stderr, /進行中/)
    assert.equal(existsSync(rebaseDir), true, '進行中の rebase を中止してはいけない')
    assert.equal(git(dir, 'rev-parse', 'HEAD'), head)
    assert.equal(git(dir, 'log', '-1', '--format=%s'), 'one')
  })

  test('使い方の誤り（引数なし）は終了コード 2', () => {
    const { dir } = makeRepo(['one'])
    const result = spawnSync('node', [script], { cwd: dir, encoding: 'utf8', env })
    assert.equal(result.status, 2, result.stdout + result.stderr)
  })
})

describe('途中の失敗', () => {
  test('commit-msg の hook が拒否すると、元の HEAD とメッセージに戻り、終了コード 1', () => {
    const { dir, shas } = makeRepo(['one', 'two', 'three'])
    const hook = path.join(dir, '.git', 'hooks', 'commit-msg')
    writeFileSync(hook, '#!/bin/sh\nif grep -q FORBIDDEN "$1"; then exit 1; fi\nexit 0\n')
    chmodSync(hook, 0o755)
    const head = git(dir, 'rev-parse', 'HEAD')
    const result = run(dir, [
      { commit: shas[0], message: 'one2\n' },
      { commit: shas[1], message: 'FORBIDDEN two\n' },
    ])
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.equal(git(dir, 'rev-parse', 'HEAD'), head)
    assert.equal(subjects(dir), 'one\ntwo\nthree')
    assert.equal(git(dir, 'status', '--porcelain', '--untracked-files=no'), '')
    assert.equal(git(dir, 'status').includes('rebase in progress'), false)
  })
})
