import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { listGitFiles } from './git-files.mjs'

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
  // pre-commit の hook の中では GIT_DIR などが設定され、試すリポジトリでなく commit 中のリポジトリを指すので、外す
  for (const name of Object.keys(process.env)) {
    if (name.startsWith('GIT_')) delete process.env[name]
  }
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
