import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { afterEach, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { removeGitEnv, withoutGitEnv } from './git-env.mjs'
import { stripComments } from '../source/strip-comments.mjs'

/** scripts/ のディレクトリ */
const SCRIPTS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

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

  test('テストが git を直接呼ぶ所は、同じ行に env: を渡す（ファイルが removeGitEnv を使う場合を除く）', () => {
    const gitCall = /\b(?:execFileSync|spawnSync|execFile|spawn)\(\s*['"`]git['"`]|\bexecSync\(\s*['"`]git\b/
    const missing = []
    const tests = readdirSync(SCRIPTS_DIR, { recursive: true })
      .map((file) => String(file).replaceAll(path.sep, '/'))
      .filter((name) => name.endsWith('.test.mjs') && !name.includes('node_modules/'))
    for (const name of tests) {
      const code = stripComments(readFileSync(path.join(SCRIPTS_DIR, name), 'utf8'), { keepRegex: true })
      if (/\bremoveGitEnv\(\)/.test(code)) continue
      code.split('\n').forEach((line, index) => {
        if (gitCall.test(line) && !/\benv\b/.test(line)) missing.push(`${name}:${index + 1}`)
      })
    }
    assert.deepEqual(missing, [])
  })
})
