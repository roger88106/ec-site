import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { removeGitEnv } from './git-env.mjs'
import { mergeBaseWith, resolveDiffBase } from './merge-base.mjs'

// Git の hook の中で実行されても別のリポジトリを見ないよう、Git を呼ぶ前に GIT_ の環境変数を外す
removeGitEnv()

const repos = []
after(() => repos.forEach((repo) => rmSync(repo, { recursive: true, force: true })))

/** 試すリポジトリを作る。main に 1 つ目の commit を作り、git を実行する関数と commit する関数を返す */
function createRepo() {
  const repo = mkdtempSync(path.join(tmpdir(), 'merge-base-'))
  repos.push(repo)
  const git = (...args) => execFileSync('git', args, { cwd: repo, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  git('init', '-q', '-b', 'main')
  git('config', 'user.email', 'test@example.com')
  git('config', 'user.name', 'test')
  git('config', 'commit.gpgsign', 'false')
  let count = 0
  const commit = () => {
    count += 1
    writeFileSync(path.join(repo, `f${count}`), String(count))
    git('add', '-A')
    git('commit', '-q', '-m', `c${count}`)
    return git('rev-parse', 'HEAD')
  }
  return { repo, git, commit }
}

describe('差分の基準（develop との分岐点）', () => {
  test('作業ブランチでは、develop との merge-base を基準にする', () => {
    const r = createRepo()
    const fork = r.commit()
    r.git('branch', 'develop')
    r.git('checkout', '-q', '-b', 'feature')
    r.commit()
    assert.deepEqual(resolveDiffBase(r.repo), { revision: fork, base: 'develop' })
  })

  test('origin/develop があれば、develop より先に見る（CI の checkout）', () => {
    const r = createRepo()
    const first = r.commit()
    r.git('update-ref', 'refs/remotes/origin/develop', first)
    const second = r.commit()
    r.git('branch', 'develop')
    r.git('checkout', '-q', '-b', 'feature')
    r.commit()
    assert.deepEqual(resolveDiffBase(r.repo), { revision: first, base: 'origin/develop' })
    assert.notEqual(first, second)
  })

  test('merge-base が HEAD 自身（develop への push）なら、HEAD の 1 つ目の親を基準にする', () => {
    const r = createRepo()
    const parent = r.commit()
    r.commit()
    r.git('branch', 'develop')
    r.git('update-ref', 'refs/remotes/origin/develop', 'HEAD')
    assert.deepEqual(resolveDiffBase(r.repo), { revision: parent, base: 'origin/develop' })
  })

  test('develop がないときは、HEAD の 1 つ目の親を基準にし、使った ref は null にする', () => {
    const r = createRepo()
    const parent = r.commit()
    r.commit()
    assert.deepEqual(resolveDiffBase(r.repo), { revision: parent, base: null })
  })

  test('初めの commit で develop もないときは、基準がない（null）', () => {
    const r = createRepo()
    r.commit()
    assert.deepEqual(resolveDiffBase(r.repo), { revision: null, base: null })
  })

  test('ブランチごとの merge-base は、origin/ の方を先に見て、どちらもなければ null を返す', () => {
    const r = createRepo()
    const first = r.commit()
    r.git('update-ref', 'refs/remotes/origin/main', first)
    r.commit()
    assert.deepEqual(mergeBaseWith('main', r.repo), { base: 'origin/main', revision: first })
    assert.equal(mergeBaseWith('develop', r.repo), null)
  })
})
