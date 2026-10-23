import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
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

  test('シンボリックリンク経由の argv[1] も、同じファイルなら直接実行と判定する', (t) => {
    const dir = mkdtempSync(path.join(tmpdir(), 'is-main-'))
    try {
      const file = path.join(dir, 'real.mjs')
      const link = path.join(dir, 'link.mjs')
      writeFileSync(file, '')
      try {
        symlinkSync(file, link)
      } catch {
        // Windows で権限がないなど、シンボリックリンクを作れない環境では確認できない
        t.skip('シンボリックリンクを作れない環境')
        return
      }
      assert.equal(isMain(pathToFileURL(file).href, link), true)
      assert.equal(isMain(pathToFileURL(link).href, file), true)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
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
