import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { findRuleViolations, listScanScripts } from './scan-rules.mjs'

/** 走査する script を置く場所（scripts/ に見立てる） */
const root = mkdtempSync(path.join(tmpdir(), 'scan-rules-'))
after(() => rmSync(root, { recursive: true, force: true }))

/** root の下にファイルを書く */
function write(file, content) {
  mkdirSync(path.dirname(path.join(root, file)), { recursive: true })
  writeFileSync(path.join(root, file), content)
}

write('ci/a.mjs', 'export const a = 1 // 禁止語\n')
write('ci/a.test.mjs', 'ここにも 禁止語\n')
write('ci/b.mjs', 'export const b = 禁止語()\n')
write('ci/lib/c.mjs', '/* 禁止語 */\nexport const c = 禁止語()\n')
write('ci/lib/c.test.mjs', '禁止語\n')
write('dev/d.mjs', '禁止語\n')
write('dev/node_modules/e.mjs', '禁止語\n')
write('ci/data.json', '禁止語\n')

const names = (sources) => sources.map(({ name }) => name)

describe('走査する script の一覧', () => {
  test('ci は、ci/ の直下のテストでない .mjs だけを、名前の順に返す', () => {
    assert.deepEqual(names(listScanScripts('ci', root)), ['ci/a.mjs', 'ci/b.mjs'])
  })

  test('ci+lib は、ci/ の直下と ci/lib/ のテストでない .mjs を返す', () => {
    assert.deepEqual(names(listScanScripts('ci+lib', root)), ['ci/a.mjs', 'ci/b.mjs', 'ci/lib/c.mjs'])
  })

  test('all は、下の全ての .mjs（テストも含む）を返し、node_modules は見ない', () => {
    assert.deepEqual(names(listScanScripts('all', root)), [
      'ci/a.mjs',
      'ci/a.test.mjs',
      'ci/b.mjs',
      'ci/lib/c.mjs',
      'ci/lib/c.test.mjs',
      'dev/d.mjs',
    ])
  })

  test('原文と、コメントを除いたコードの両方を持つ', () => {
    const [a] = listScanScripts('ci', root)
    assert.match(a.raw, /\/\/ 禁止語/)
    assert.doesNotMatch(a.code, /禁止語/)
  })

  test('知らない範囲の名前は例外にする', () => {
    assert.throws(() => listScanScripts('x', root), /x/)
  })
})

describe('禁じる書き方の照合', () => {
  const forbidden = /禁止語\(/

  test('禁じる書き方を含む script を、守る関数と一緒に返す', () => {
    const rules = [{ guard: 'guard()', forbidden, exceptions: {} }]
    assert.deepEqual(findRuleViolations(listScanScripts('ci+lib', root), rules), [
      { guard: 'guard()', name: 'ci/b.mjs' },
      { guard: 'guard()', name: 'ci/lib/c.mjs' },
    ])
  })

  test('例外の script は返さない', () => {
    const rules = [{ guard: 'guard()', forbidden, exceptions: { 'ci/lib/c.mjs': '守る関数を定義する' } }]
    assert.deepEqual(findRuleViolations(listScanScripts('ci+lib', root), rules), [{ guard: 'guard()', name: 'ci/b.mjs' }])
  })

  test('既定ではコメントを除いたコードを見て、コメントの中の書き方は違反にしない', () => {
    const rules = [{ guard: 'g', forbidden: /禁止語/, exceptions: {} }]
    assert.deepEqual(
      findRuleViolations(listScanScripts('ci', root), rules).map(({ name }) => name),
      ['ci/b.mjs'],
    )
  })

  test('target を raw にすると、コメントも含めた原文を見る', () => {
    const rules = [{ guard: 'g', forbidden: /禁止語/, target: 'raw', exceptions: {} }]
    assert.deepEqual(
      findRuleViolations(listScanScripts('ci', root), rules).map(({ name }) => name),
      ['ci/a.mjs', 'ci/b.mjs'],
    )
  })

  test('守る関数ごとの表の行を、全て照合する', () => {
    const rules = [
      { guard: 'g1', forbidden: /export const a/, exceptions: {} },
      { guard: 'g2', forbidden: /export const b/, exceptions: {} },
    ]
    assert.deepEqual(findRuleViolations(listScanScripts('ci', root), rules), [
      { guard: 'g1', name: 'ci/a.mjs' },
      { guard: 'g2', name: 'ci/b.mjs' },
    ])
  })

  test('違反がなければ空を返す', () => {
    assert.deepEqual(findRuleViolations(listScanScripts('ci', root), [{ guard: 'g', forbidden: /存在しない/, exceptions: {} }]), [])
  })

  test('走査する script が 0 件のときは、何も見ずに成功せず例外にする', () => {
    assert.throws(() => findRuleViolations([], [{ guard: 'g', forbidden, exceptions: {} }]), /0 件/)
  })

  test('target が code・raw 以外（綴りの誤り）の行は、行の guard を添えて例外にする', () => {
    const rules = [{ guard: 'guard-x', forbidden, target: 'cod', exceptions: {} }]
    assert.throws(() => findRuleViolations(listScanScripts('ci', root), rules), /guard-x/)
  })

  test('forbidden が正規表現でない行は、行の guard を添えて例外にする', () => {
    const rules = [{ guard: 'guard-y', forbidden: '禁止語', exceptions: {} }]
    assert.throws(() => findRuleViolations(listScanScripts('ci', root), rules), /guard-y/)
  })

  test('forbidden に g・y のフラグがある行は（lastIndex が持ち越されるので）、行の guard を添えて例外にする', () => {
    for (const flag of ['g', 'y']) {
      const rules = [{ guard: `guard-${flag}`, forbidden: new RegExp('禁止語', flag), exceptions: {} }]
      assert.throws(() => findRuleViolations(listScanScripts('ci', root), rules), new RegExp(`guard-${flag}`))
    }
  })

  test('どの script にも当たらない例外（存在しない script の例外）は例外にする', () => {
    const rules = [{ guard: 'g', forbidden, exceptions: { 'ci/none.mjs': '理由' } }]
    assert.throws(() => findRuleViolations(listScanScripts('ci+lib', root), rules), /ci\/none\.mjs/)
  })

  test('存在しても禁じる書き方を含まない script の例外（使われなくなった例外）は、guard と script の名前を添えて例外にする', () => {
    const rules = [{ guard: 'guard-z', forbidden, exceptions: { 'ci/a.mjs': '理由' } }]
    assert.throws(() => findRuleViolations(listScanScripts('ci+lib', root), rules), /guard-z.*ci\/a\.mjs/)
  })

  test('使われなくなった例外かどうかも、行の target で見る（code ではコメントの中だけの書き方は含まない）', () => {
    const code = [{ guard: 'guard-c', forbidden: /禁止語/, exceptions: { 'ci/a.mjs': '理由' } }]
    assert.throws(() => findRuleViolations(listScanScripts('ci', root), code), /guard-c.*ci\/a\.mjs/)
    const raw = [{ guard: 'guard-c', forbidden: /禁止語/, target: 'raw', exceptions: { 'ci/a.mjs': '理由' } }]
    assert.deepEqual(findRuleViolations(listScanScripts('ci', root), raw), [{ guard: 'guard-c', name: 'ci/b.mjs' }])
  })
})
