import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { checkExclusions, findEmptyReasons, findStaleExclusions, readExclusions } from './exclusions.mjs'

/** 除外の一覧の JSON を置く場所 */
const dir = mkdtempSync(path.join(tmpdir(), 'exclusions-'))
after(() => rmSync(dir, { recursive: true, force: true }))

/** JSON のファイルを書き、そのパスを返す */
function write(name, content) {
  const file = path.join(dir, name)
  writeFileSync(file, content)
  return file
}

describe('除外の一覧の読み込み', () => {
  test('{キー: 理由} の JSON を、書いた順のまま読む', () => {
    const file = write('ok.json', '{"b": "理由 b", "a": "理由 a"}')
    assert.deepEqual(Object.entries(readExclusions(file)), [
      ['b', '理由 b'],
      ['a', '理由 a'],
    ])
  })

  test('配列や null など、{キー: 理由} の形でない JSON は、ファイル名を添えて例外にする', () => {
    for (const name of ['array.json', 'null.json', 'text.json']) {
      const content = { 'array.json': '[]', 'null.json': 'null', 'text.json': '"x"' }[name]
      const file = write(name, content)
      assert.throws(() => readExclusions(file), (error) => error.message.includes(name))
    }
  })

  test('JSON として読めないファイルは、ファイル名を添えて例外にする', () => {
    const file = write('broken.json', '{')
    assert.throws(() => readExclusions(file), (error) => error.message.includes('broken.json'))
  })
})

describe('除外の理由', () => {
  test('理由が空、空白だけ、文字列でない除外のキーを、書いた順に返す', () => {
    assert.deepEqual(findEmptyReasons({ a: '理由', b: '', c: '  ', d: ['x'], e: null, f: '理由' }), ['b', 'c', 'd', 'e'])
  })

  test('全てに理由があれば空を返す', () => {
    assert.deepEqual(findEmptyReasons({ a: '理由' }), [])
  })
})

describe('不要になった除外', () => {
  test('まだ必要かの判定が false のキーを、書いた順に返す', () => {
    const needed = new Set(['a', 'c'])
    assert.deepEqual(findStaleExclusions({ a: 'x', b: 'x', c: 'x', d: 'x' }, (key) => needed.has(key)), ['b', 'd'])
  })

  test('全てが必要なら空を返す', () => {
    assert.deepEqual(findStaleExclusions({ a: 'x' }, () => true), [])
  })
})

describe('除外の一覧の検査', () => {
  test('理由のない除外と不要な除外を、ファイル名を添えた文にして返す', () => {
    const messages = checkExclusions('x-exclusions.json', { a: '', b: '理由', c: '理由' }, (key) => key !== 'c')
    assert.equal(messages.length, 2)
    assert.match(messages[0], /x-exclusions\.json.*a.*理由/)
    assert.match(messages[1], /x-exclusions\.json.*c.*不要/)
  })

  test('理由がなく、不要でもある除外は、2 つとも返す', () => {
    assert.equal(checkExclusions('x.json', { a: '' }, () => false).length, 2)
  })

  test('問題がなければ空を返す', () => {
    assert.deepEqual(checkExclusions('x.json', { a: '理由' }, () => true), [])
  })
})
