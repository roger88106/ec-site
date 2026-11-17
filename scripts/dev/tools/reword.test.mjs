// reword.mjs（push していない commit のメッセージを書き換える script）のテスト（node --test scripts/dev/tools/reword.test.mjs）
// os.tmpdir() の下の一時のリポジトリの中だけで行い、テストの後に消す
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, beforeEach, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../shared/git/git-env.mjs'
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
    assert.match(result.stderr, /未 commit/)
  })

  describe('対応表とメッセージの誤り（リポジトリを変えないので、1 つのリポジトリで試す）', () => {
    let repo
    let head

    before(() => {
      repo = makeRepo(['one', 'two'])
      head = git(repo.dir, 'rev-parse', 'HEAD')
    })

    /** 対応表のファイルに `content` をそのまま書いて script を実行する */
    function runRaw(content) {
      const work = path.join(root, `work-${counter++}`)
      mkdirSync(work)
      const mapFile = path.join(work, 'map.json')
      writeFileSync(mapFile, content)
      return spawnSync('node', [script, mapFile], { cwd: repo.dir, encoding: 'utf8', env })
    }

    test('解決できない commit は、終了コード 1 で止まる', () => {
      const result = run(repo.dir, [{ commit: 'deadbeefdeadbeef', message: 'x\n' }])
      assert.equal(result.status, 1, result.stdout + result.stderr)
      assert.match(result.stderr, /commit を解決できない/)
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('メッセージのファイルがないと、終了コード 1 で止まる', () => {
      const result = runRaw(JSON.stringify([{ commit: repo.shas[0], message: 'no-such.txt' }]))
      assert.equal(result.status, 1, result.stdout + result.stderr)
      assert.match(result.stderr, /メッセージのファイルが読めない/)
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('メッセージのファイルが空だと、終了コード 1 で止まる', () => {
      const result = run(repo.dir, [{ commit: repo.shas[0], message: '  \n' }])
      assert.equal(result.status, 1, result.stdout + result.stderr)
      assert.match(result.stderr, /メッセージのファイルが空/)
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('同じ commit が重複していると、終了コード 1 で止まる', () => {
      const result = run(repo.dir, [
        { commit: repo.shas[0], message: 'a\n' },
        { commit: repo.shas[0].slice(0, 10), message: 'b\n' },
      ])
      assert.equal(result.status, 1, result.stdout + result.stderr)
      assert.match(result.stderr, /重複/)
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('対応表が JSON として読めないと、終了コード 1 で止まる', () => {
      const result = runRaw('[{')
      assert.equal(result.status, 1, result.stdout + result.stderr)
      assert.match(result.stderr, /対応表が読めない/)
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('対応表が配列でないか空だと、終了コード 1 で止まる', () => {
      for (const content of ['{}', '[]']) {
        const result = runRaw(content)
        assert.equal(result.status, 1, `${content}：${result.stdout}${result.stderr}`)
        assert.match(result.stderr, /1 件以上の配列/, content)
      }
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('対応表の要素に文字列の commit と message がないと、終了コード 1 で止まる', () => {
      for (const item of [{ commit: repo.shas[0] }, { message: 'm.txt' }, { commit: 1, message: 'm.txt' }, null]) {
        const result = runRaw(JSON.stringify([item]))
        assert.equal(result.status, 1, `${JSON.stringify(item)}：${result.stdout}${result.stderr}`)
        assert.match(result.stderr, /文字列の commit と message/, JSON.stringify(item))
      }
      assertUntouched(repo.dir, head, 'one\ntwo')
    })

    test('使い方の誤り（引数なし）は終了コード 2', () => {
      const result = spawnSync('node', [script], { cwd: repo.dir, encoding: 'utf8', env })
      assert.equal(result.status, 2, result.stdout + result.stderr)
      assert.match(result.stderr, /使い方/)
    })
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
    assert.match(result.stderr, /HEAD の祖先でない/)
    assertUntouched(dir, head, 'one\ntwo')
  })

  test('範囲にマージの commit があると、終了コード 1 で止まる', () => {
    // 準備：main と side に 1 つずつ commit を積み、side を main にマージする
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

    // 操作：マージより前の commit を書き換える
    const result = run(dir, [{ commit: shas[1], message: 'two2\n' }])

    // 確認：書き換えずに止まり、リポジトリは変わらない
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stderr, /マージ/)
    assertUntouched(dir, head, before)
  })

  test('rebase が進行中なら、状態を変えず、進行中の rebase を残して終了コード 1 で止まる', () => {
    // 準備：最初の commit を edit にした rebase を始めて、途中で止める
    const { dir } = makeRepo(['one', 'two', 'three'])
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

    // 操作
    const result = run(dir, [{ commit: head, message: 'one2\n' }])

    // 確認：進行中の rebase と HEAD をそのまま残す
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stderr, /進行中/)
    assert.equal(existsSync(rebaseDir), true, '進行中の rebase を中止してはいけない')
    assert.equal(git(dir, 'rev-parse', 'HEAD'), head)
    assert.equal(git(dir, 'log', '-1', '--format=%s'), 'one')
  })

})

describe('途中の失敗', () => {
  test('commit-msg の hook が拒否すると、元の HEAD とメッセージに戻り、終了コード 1', () => {
    // 準備：FORBIDDEN を含むメッセージを拒否する commit-msg の hook を置く
    const { dir, shas } = makeRepo(['one', 'two', 'three'])
    const hook = path.join(dir, '.git', 'hooks', 'commit-msg')
    writeFileSync(hook, '#!/bin/sh\nif grep -q FORBIDDEN "$1"; then exit 1; fi\nexit 0\n')
    chmodSync(hook, 0o755)
    const head = git(dir, 'rev-parse', 'HEAD')

    // 操作：2 つ目の書き換えを hook が拒否する
    const result = run(dir, [
      { commit: shas[0], message: 'one2\n' },
      { commit: shas[1], message: 'FORBIDDEN two\n' },
    ])

    // 確認：1 つ目の書き換えも含めて元に戻り、rebase は残らない
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.equal(git(dir, 'rev-parse', 'HEAD'), head)
    assert.equal(subjects(dir), 'one\ntwo\nthree')
    assert.equal(git(dir, 'status', '--porcelain', '--untracked-files=no'), '')
    assert.equal(git(dir, 'status').includes('rebase in progress'), false)
  })
})
