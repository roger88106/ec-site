import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { pathToFileURL } from 'node:url'
import { isMain, samePath } from './is-main.mjs'

describe('直接実行の判定', () => {
  test('argv[1] がこのファイルのパスなら、直接実行と判定する', () => {
    const file = path.resolve('scripts/ci/sample.mjs')
    assert.equal(isMain(pathToFileURL(file).href, file), true)
  })

  test('別のファイルなら、直接実行ではない', () => {
    const file = path.resolve('scripts/ci/sample.mjs')
    assert.equal(isMain(pathToFileURL(file).href, path.resolve('scripts/ci/other.mjs')), false)
  })

  test('argv[1] がない（node -e や REPL）と、直接実行ではない', () => {
    assert.equal(isMain(pathToFileURL(path.resolve('a.mjs')).href, undefined), false)
  })

  test('相対パスの argv[1] も、解決して比べる', () => {
    const file = path.resolve('scripts/ci/sample.mjs')
    assert.equal(isMain(pathToFileURL(file).href, path.relative(process.cwd(), file)), true)
  })

  test('Windows ではドライブ文字の大小と区切りを区別しない', () => {
    assert.equal(samePath('C:\\work\\a.mjs', 'c:\\work\\a.mjs', true), true)
    assert.equal(samePath('C:\\work\\a.mjs', 'C:/work/a.mjs', true), true)
    assert.equal(samePath('C:\\work\\a.mjs', 'C:\\work\\b.mjs', true), false)
  })

  test('Windows でなければ、大小を区別する', () => {
    assert.equal(samePath('/work/A.mjs', '/work/a.mjs', false), false)
    assert.equal(samePath('/work/a.mjs', '/work/a.mjs', false), true)
  })
})
