import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { pathToFileURL } from 'node:url'
import { removeGitEnv } from './git-env.mjs'
import { isShallowRepository, SHALLOW_CLONE_MESSAGE } from './shallow-clone.mjs'

// Git の hook の中で実行されても別のリポジトリを見ないよう、Git を呼ぶ前に GIT_ の環境変数を外す
removeGitEnv()
const git = (cwd, ...args) => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })

const root = mkdtempSync(path.join(tmpdir(), 'shallow-clone-'))
after(() => rmSync(root, { recursive: true, force: true }))

const source = path.join(root, 'source')
git(root, 'init', '-q', source)
for (const name of ['a', 'b']) {
  writeFileSync(path.join(source, name), name)
  git(source, 'add', name)
  git(source, '-c', 'user.name=t', '-c', 'user.email=t@example.com', '-c', 'commit.gpgsign=false', 'commit', '-q', '-m', name)
}
const shallow = path.join(root, 'shallow')
git(root, 'clone', '-q', '--depth', '1', pathToFileURL(source).href, shallow)

describe('shallow clone の確認', () => {
  test('--depth で clone したリポジトリは、shallow と判定する', () => {
    assert.equal(isShallowRepository(shallow), true)
  })

  test('履歴が全てあるリポジトリは、shallow ではない', () => {
    assert.equal(isShallowRepository(source), false)
  })

  test('失敗の文は、fetch-depth: 0 を付けることを示す', () => {
    assert.match(SHALLOW_CLONE_MESSAGE, /fetch-depth: 0/)
  })
})
