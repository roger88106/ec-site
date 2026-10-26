import assert from 'node:assert/strict'
import { afterEach, describe, test } from 'node:test'
import { removeGitEnv, withoutGitEnv } from './git-env.mjs'

describe('GIT_ の環境変数を外す補助', () => {
  const saved = { ...process.env }
  afterEach(() => {
    for (const name of Object.keys(process.env)) delete process.env[name]
    Object.assign(process.env, saved)
  })

  test('GIT_ で始まる変数だけを外し、ほかは残す', () => {
    const env = { GIT_DIR: '/repo/.git', GIT_INDEX_FILE: 'index', PATH: '/bin', GITHUB_TOKEN: 'x', HOME: '/home' }
    assert.deepEqual(withoutGitEnv(env), { PATH: '/bin', GITHUB_TOKEN: 'x', HOME: '/home' })
  })

  test('元の環境は変えない', () => {
    const env = { GIT_DIR: '/repo/.git', PATH: '/bin' }
    withoutGitEnv(env)
    assert.deepEqual(env, { GIT_DIR: '/repo/.git', PATH: '/bin' })
  })

  test('環境を渡さなければ、process.env から外した写しを返す', () => {
    process.env.GIT_WORK_TREE = '/repo'
    const env = withoutGitEnv()
    assert.equal('GIT_WORK_TREE' in env, false)
    assert.equal(process.env.GIT_WORK_TREE, '/repo')
  })

  test('removeGitEnv は process.env から GIT_ で始まる変数を消し、ほかは残す', () => {
    process.env.GIT_DIR = '/repo/.git'
    process.env.GIT_OTHER_THING = '1'
    process.env.KEEP_ME = 'yes'
    removeGitEnv()
    assert.equal(Object.keys(process.env).some((name) => /^GIT_/.test(name)), false)
    assert.equal(process.env.KEEP_ME, 'yes')
  })
})
