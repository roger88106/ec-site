import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { removeGitEnv, withoutGitEnv } from './git-env.mjs'
import { emptyListReason, listGitFiles, splitNulList } from './git-files.mjs'

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
  test('追跡しているファイルと、追跡していない無視されないファイルを、名前の順に返す', () => {
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
})

describe('対象が 0 件のときの失敗', () => {
  test('0 件なら理由を返し、あるときは null', () => {
    assert.match(emptyListReason([], 'テストのファイル') ?? '', /テストのファイル.*1 件も見つからない/)
    assert.equal(emptyListReason(['a.txt'], 'テストのファイル'), null)
  })

  // Git の一覧を読む検査は、対象が 0 件のとき（検査の対象の置き場所が変わった、除外の条件が広すぎる）に、何も見ずに成功してしまわない
  const checks = [
    'ac-check',
    'ci-docs',
    'dependabot-actions',
    'doc-refs',
    'doc-terms',
    'env-defaults',
    'frontend-rules',
    'glossary-ids',
    'gradle-plugins',
    'hardcoded-text',
    'image-versions',
    'input-constraints',
    'java-rules',
    'message-comments',
    'related-specs',
    'runtime-versions',
    'spec-numbers',
    'test-names',
    'time-waits',
  ]
  const env = withoutGitEnv()
  const empty = mkdtempSync(path.join(tmpdir(), 'empty-targets-'))
  after(() => rmSync(empty, { recursive: true, force: true }))
  execFileSync('git', ['init', '-q'], { cwd: empty, env })
  writeFileSync(path.join(empty, 'unrelated.txt'), 'x')

  for (const name of checks) {
    test(`${name} は、対象のファイルが 0 件のとき失敗にする`, () => {
      const script = fileURLToPath(new URL(`../${name}.mjs`, import.meta.url))
      const result = spawnSync('node', [script], { cwd: empty, env, encoding: 'utf8' })
      assert.equal(result.status, 1, result.stdout + result.stderr)
      assert.match(result.stdout, /1 件も見つからない/)
    })
  }
})
