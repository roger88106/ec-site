import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { removeGitEnv, withoutGitEnv } from './git-env.mjs'
import { emptyListReason, listGitFiles, listIgnoredTrackedFiles, listTrackedFiles, listUntrackedFiles, splitNulList } from './git-files.mjs'

/** 試す Git のリポジトリ */
const repo = mkdtempSync(path.join(tmpdir(), 'git-files-'))
after(() => rmSync(repo, { recursive: true, force: true }))

/** リポジトリにファイルを書く */
function write(file, content = 'x') {
  mkdirSync(path.dirname(path.join(repo, file)), { recursive: true })
  writeFileSync(path.join(repo, file), content)
}

const git = (...args) => execFileSync('git', args, { cwd: repo, stdio: 'ignore' })

before(() => {
  removeGitEnv()
  git('init', '-q')
  write('.gitignore', 'ignored.txt\n')
  write('tracked.txt')
  write('docs/a.md')
  write('frontend/app.ts')
  write('removed.txt')
  git('add', '.')
  rmSync(path.join(repo, 'removed.txt'))
  write('untracked.txt')
  write('ignored.txt')
})

describe('-z の出力の分割', () => {
  test('NUL で分け、空の要素は捨て、ASCII でない文字を含むパスはそのまま返す', () => {
    assert.deepEqual(splitNulList('docs/日本語.md\0a b.txt\0\0'), ['docs/日本語.md', 'a b.txt'])
  })

  test('空の出力は空の配列', () => {
    assert.deepEqual(splitNulList(''), [])
  })
})

describe('Git の対象のファイルの一覧', () => {
  test('commit の前に新しいファイルも検査するため、追跡していない無視されないファイルも、追跡しているファイルと一緒に名前の順に返す', () => {
    assert.deepEqual(listGitFiles([], repo), ['.gitignore', 'docs/a.md', 'frontend/app.ts', 'tracked.txt', 'untracked.txt'])
  })

  test('無視されるファイルと、消えたファイルは返さない', () => {
    const files = listGitFiles([], repo)
    assert.equal(files.includes('ignored.txt'), false)
    assert.equal(files.includes('removed.txt'), false)
  })

  test('パスの指定があれば、その下だけを返す', () => {
    assert.deepEqual(listGitFiles(['frontend', 'docs'], repo), ['docs/a.md', 'frontend/app.ts'])
  })

  test('追跡していないファイルだけの一覧は、無視されるファイルと追跡しているファイルを返さない', () => {
    assert.deepEqual(listUntrackedFiles([], repo), ['untracked.txt'])
  })
})

describe('追跡しているファイルの一覧', () => {
  test('追跡しているファイルだけを返す（作業ツリーから消えたものを含み、追跡していないものと無視されるものは含めない）', () => {
    assert.deepEqual(listTrackedFiles(repo).sort(), ['.gitignore', 'docs/a.md', 'frontend/app.ts', 'removed.txt', 'tracked.txt'])
  })

  test('リポジトリの外では git が失敗して例外を投げる', () => {
    const outside = mkdtempSync(path.join(tmpdir(), 'git-files-outside-'))
    after(() => rmSync(outside, { recursive: true, force: true }))
    const saved = process.env.GIT_CEILING_DIRECTORIES
    process.env.GIT_CEILING_DIRECTORIES = path.dirname(outside)
    try {
      assert.throws(() => listTrackedFiles(outside))
      assert.throws(() => listIgnoredTrackedFiles(outside))
    } finally {
      if (saved === undefined) delete process.env.GIT_CEILING_DIRECTORIES
      else process.env.GIT_CEILING_DIRECTORIES = saved
    }
  })
})

describe('.gitignore で除外の対象になる追跡しているファイルの一覧', () => {
  /** 除外のファイル（a.log）を `git add -f` で追跡した、一時のリポジトリ */
  function makeIgnoredRepo() {
    const dir = mkdtempSync(path.join(tmpdir(), 'git-files-ignored-'))
    after(() => rmSync(dir, { recursive: true, force: true }))
    const run = (...args) => execFileSync('git', args, { cwd: dir, env: withoutGitEnv(), stdio: 'ignore' })
    run('init', '-q')
    writeFileSync(path.join(dir, '.gitignore'), '*.log\n')
    writeFileSync(path.join(dir, 'a.log'), 'x')
    writeFileSync(path.join(dir, 'b.txt'), 'x')
    run('add', '-f', '.gitignore', 'a.log', 'b.txt')
    return { dir, run }
  }

  test('.gitignore が除外するファイルだけを返す', () => {
    const { dir } = makeIgnoredRepo()
    assert.deepEqual(listIgnoredTrackedFiles(dir), ['a.log'])
  })

  test('.git/info/exclude は読まない（手元と CI で同じ結果にする）', () => {
    const { dir } = makeIgnoredRepo()
    writeFileSync(path.join(dir, '.git', 'info', 'exclude'), 'b.txt\n')
    assert.deepEqual(listIgnoredTrackedFiles(dir), ['a.log'])
  })

  test('大文字と小文字は、core.ignorecase が true でも区別する', () => {
    const { dir, run } = makeIgnoredRepo()
    writeFileSync(path.join(dir, '.gitignore'), 'B.TXT\n')
    run('config', 'core.ignorecase', 'true')
    assert.deepEqual(listIgnoredTrackedFiles(dir), [])
  })
})

describe('対象が 0 件のときの失敗', () => {
  test('0 件なら理由を返し、あるときは null', () => {
    assert.match(emptyListReason([], 'テストのファイル') ?? '', /テストのファイル.*1 件も見つからない/)
    assert.equal(emptyListReason(['a.txt'], 'テストのファイル'), null)
  })

  // Git の一覧を読む検査は、対象が 0 件のとき（検査の対象の置き場所が変わった、除外の条件が広すぎる）に、何も見ずに成功してしまわない
  // 一覧は、exitIfNoFiles を呼ぶ検査を走査して作る（検査を追加したとき、一覧への追加を忘れない）
  const ciDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../ci')
  const checkFiles = ['docs', 'spec', 'ja', 'java', 'config', 'frontend']
    .flatMap((group) => readdirSync(path.join(ciDir, group)).map((name) => path.join(group, name)))
    .filter((name) => name.endsWith('.mjs') && !name.endsWith('.test.mjs') && /\bexitIfNoFiles\(/.test(readFileSync(path.join(ciDir, name), 'utf8')))
  const checks = checkFiles.map((name) => path.basename(name, '.mjs'))

  test('0 件で失敗にするかを試す検査の走査が、空にならない', () => {
    assert.ok(checks.length >= 1, `${checks.length} 件`)
    assert.ok(checks.includes('frontend-rules'), 'frontend-rules')
  })

  const env = withoutGitEnv()
  const empty = mkdtempSync(path.join(tmpdir(), 'empty-targets-'))
  after(() => rmSync(empty, { recursive: true, force: true }))
  execFileSync('git', ['init', '-q'], { cwd: empty, env })
  writeFileSync(path.join(empty, 'unrelated.txt'), 'x')

  for (const file of checkFiles) {
    const name = path.basename(file, '.mjs')
    test(`${name} は、対象のファイルが 0 件のとき終了コード 2 で失敗にする`, () => {
      const script = path.join(ciDir, file)
      const result = spawnSync('node', [script], { cwd: empty, env, encoding: 'utf8' })
      assert.equal(result.status, 2, result.stdout + result.stderr)
      assert.match(result.stdout, /1 件も見つからない/)
    })
  }
})
