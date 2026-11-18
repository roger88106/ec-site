import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { isInGeneratedFolder } from './generated-folders.mjs'

describe('生成物のフォルダーの判定', () => {
  test('パスの区切りの単位で、先頭と途中のフォルダー名に当たる', () => {
    assert.equal(isInGeneratedFolder('build/libs/a.jar'), true)
    assert.equal(isInGeneratedFolder('backend/ecsite/build/libs/a.jar'), true)
    assert.equal(isInGeneratedFolder('frontend/store/node_modules/a/index.js'), true)
    assert.equal(isInGeneratedFolder('frontend/store/.next/a.js'), true)
    assert.equal(isInGeneratedFolder('frontend/store/dist/a.js'), true)
    assert.equal(isInGeneratedFolder('backend/src/main/generated/A.java'), true)
  })

  test('フォルダー自身のパスは、末尾の / があってもなくても当たる', () => {
    assert.equal(isInGeneratedFolder('backend/ecsite/build'), true)
    assert.equal(isInGeneratedFolder('backend/ecsite/build/'), true)
  })

  test('名前の一部だけが同じフォルダー（rebuild、mybuild、.nextjs）には当たらない', () => {
    assert.equal(isInGeneratedFolder('docs/rebuild/a.md'), false)
    assert.equal(isInGeneratedFolder('docs/mybuild/a.md'), false)
    assert.equal(isInGeneratedFolder('docs/.nextjs/a.md'), false)
    assert.equal(isInGeneratedFolder('docs/a/builds/a.md'), false)
  })
})
