import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { checkMainSource, isAllowedMainSource } from './main-source.mjs'

const SCRIPT = fileURLToPath(new URL('./main-source.mjs', import.meta.url))

describe('main に入れられる元のブランチ', () => {
  test('release- と hotfix- で始まる名前は通す', () => {
    assert.equal(isAllowedMainSource('release-1.2.0'), true)
    assert.equal(isAllowedMainSource('hotfix-123-fix'), true)
  })

  test('release- と hotfix- で始まらない名前は通さない', () => {
    for (const name of ['develop', 'main', '158-ci-checks', 'release', 'hotfix', 'feature/release-x', 'my-release-1', 'Release-1', 'hotfix']) {
      assert.equal(isAllowedMainSource(name), false, name)
    }
  })

  test('空の名前と名前でない値は通さない', () => {
    assert.equal(isAllowedMainSource(''), false)
    assert.equal(isAllowedMainSource(undefined), false)
    assert.equal(isAllowedMainSource(null), false)
  })
})

describe('判定の結果の文', () => {
  test('通すときは入れられると伝える', () => {
    assert.deepEqual(checkMainSource('release-1.0'), {
      ok: true,
      message: '元のブランチ release-1.0 は main に入れられる。',
    })
  })

  test('通さないときは、入れられるブランチと元のブランチの名前を伝える', () => {
    assert.deepEqual(checkMainSource('develop'), {
      ok: false,
      message: '::error::main に入れられるのは release- か hotfix- で始まるブランチだけ。元のブランチ「develop」は入れられない。',
    })
  })
})

describe('入口', () => {
  test('通すブランチは終了コード 0', () => {
    const result = spawnSync(process.execPath, [SCRIPT, 'hotfix-9-x'], { encoding: 'utf8' })
    assert.equal(result.status, 0)
    assert.match(result.stdout, /main に入れられる/)
  })

  test('通さないブランチは終了コード 1', () => {
    const result = spawnSync(process.execPath, [SCRIPT, 'feature/release-x'], { encoding: 'utf8' })
    assert.equal(result.status, 1)
    assert.match(result.stdout, /^::error::/)
  })

  test('引数がないときは通さない', () => {
    const result = spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8' })
    assert.equal(result.status, 1)
  })
})
