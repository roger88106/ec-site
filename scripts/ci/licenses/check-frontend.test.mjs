// check-frontend.mjs のテスト（node --test "scripts/ci/**/*.test.mjs"）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { check, evaluate } from './check-frontend.mjs'

/** テストの方針（policy.json の形の一部） */
const policy = {
  allowed: ['MIT', 'Apache-2.0', 'ISC'],
  conditional: {
    licenses: ['MPL-2.0', 'LGPL-3.0-or-later', 'OFL-1.1'],
    components: { 'npm:lightningcss*': '理由', 'npm:@img/sharp-*': '理由' },
  },
  forbidden: ['GPL-3.0-only', 'AGPL-3.0-only', 'UNKNOWN'],
  normalize: {},
}

describe('1 つの部品のライセンスの判定', () => {
  test('許可のライセンスは通る', () => {
    assert.deepEqual(evaluate('MIT', 'react', policy), { ok: true })
  })

  test('条件付きのライセンスは、一覧にある部品なら通る', () => {
    assert.deepEqual(evaluate('MPL-2.0', 'lightningcss-win32-x64-msvc', policy), { ok: true })
  })

  test('条件付きのライセンスは、一覧にない部品なら、一覧にない部品として失敗する', () => {
    const result = evaluate('MPL-2.0', 'some-package', policy)
    assert.equal(result.ok, false)
    assert.match(result.reason, /条件付きのライセンス（MPL-2\.0）で、一覧にない部品/)
  })

  test('禁止のライセンスは、禁止として失敗する', () => {
    const result = evaluate('GPL-3.0-only', 'some-package', policy)
    assert.equal(result.ok, false)
    assert.match(result.reason, /禁止か分からないライセンス（GPL-3\.0-only）/)
  })

  test('方針にないライセンスは、方針にないものとして失敗する', () => {
    const result = evaluate('WTFPL', 'some-package', policy)
    assert.equal(result.ok, false)
    assert.match(result.reason, /方針にないライセンス（WTFPL）/)
  })

  test('分からないライセンス（Unknown）は、禁止か分からないものとして失敗する', () => {
    assert.match(evaluate('Unknown', 'some-package', policy).reason, /禁止か分からないライセンス（Unknown）/)
  })

  test('OR は、どれか 1 つが通れば通る', () => {
    assert.equal(evaluate('MIT OR GPL-3.0-only', 'some-package', policy).ok, true)
    assert.equal(evaluate('(GPL-3.0-only OR MIT)', 'some-package', policy).ok, true)
    assert.match(evaluate('GPL-3.0-only OR AGPL-3.0-only', 'some-package', policy).reason, /禁止か分からないライセンス/)
  })

  test('AND は、すべてが通るときだけ通る', () => {
    assert.equal(evaluate('Apache-2.0 AND LGPL-3.0-or-later', '@img/sharp-win32-x64', policy).ok, true)
    assert.match(evaluate('Apache-2.0 AND LGPL-3.0-or-later', 'other-binary', policy).reason, /条件付きのライセンス（LGPL-3\.0-or-later）で、一覧にない部品/)
    assert.match(evaluate('MIT AND GPL-3.0-only', 'some-package', policy).reason, /禁止か分からないライセンス（GPL-3\.0-only）/)
  })

  test('表記の揺れは、正規化してから判定する', () => {
    const withNormalize = { ...policy, normalize: { 'MIT License': 'MIT' } }
    assert.equal(evaluate('MIT License', 'some-package', withNormalize).ok, true)
  })
})

describe('pnpm licenses list --json の結果の判定', () => {
  test('方針に合わない部品を、名前とバージョンとライセンスで返す', () => {
    const report = {
      MIT: [{ name: 'react', versions: ['19.3.0'] }],
      'GPL-3.0-only': [{ name: 'bad', versions: ['1.0.0', '1.1.0'] }],
      'MPL-2.0': [{ name: 'lightningcss', versions: ['1.30.0'] }],
    }

    const failures = check(report, policy)

    assert.deepEqual(
      failures.map(({ name, versions, license }) => ({ name, versions, license })),
      [{ name: 'bad', versions: ['1.0.0', '1.1.0'], license: 'GPL-3.0-only' }],
    )
  })
})

describe('括弧と AND・OR を組み合わせた式の判定', () => {
  const ok = (expression, name = 'some-package') => evaluate(expression, name, policy).ok

  test('AND は OR より強く結ぶ', () => {
    assert.equal(ok('MIT OR GPL-3.0-only AND AGPL-3.0-only'), true)
    assert.equal(ok('GPL-3.0-only AND AGPL-3.0-only OR MIT'), true)
    assert.equal(ok('GPL-3.0-only OR MIT AND AGPL-3.0-only'), false)
  })

  test('(A OR B) AND C は、C も通るときだけ通る', () => {
    assert.equal(ok('(MIT OR GPL-3.0-only) AND ISC'), true)
    assert.equal(ok('(MIT OR GPL-3.0-only) AND AGPL-3.0-only'), false)
    assert.equal(ok('(GPL-3.0-only OR AGPL-3.0-only) AND (MIT)'), false)
  })

  test('A AND (B OR C) は、A が通り B か C が通るときだけ通る', () => {
    assert.equal(ok('MIT AND (GPL-3.0-only OR ISC)'), true)
    assert.equal(ok('GPL-3.0-only AND (MIT OR ISC)'), false)
    assert.equal(ok('MIT AND (GPL-3.0-only OR AGPL-3.0-only)'), false)
  })

  test('入れ子と余分な空白を扱う', () => {
    assert.equal(ok('  ( (MIT)  OR ( GPL-3.0-only AND ISC ) )  '), true)
    assert.equal(ok('((GPL-3.0-only OR AGPL-3.0-only) AND MIT) OR GPL-3.0-only'), false)
    assert.equal(ok('((GPL-3.0-only OR ISC) AND MIT) OR GPL-3.0-only'), true)
  })

  test('単独の識別子と、括弧付きの OR・AND の式を、方針の区分どおりに判定する', () => {
    assert.equal(ok('MIT'), true)
    assert.equal(ok('(MIT OR Apache-2.0)'), true)
    assert.equal(ok('Apache-2.0 AND MIT'), true)
    assert.equal(ok('(MIT AND Zlib)'), false)
    assert.equal(ok('MPL-2.0 OR MIT'), true)
  })

  test('WITH を含む識別子は、1 つのライセンスとして扱う', () => {
    const withException = { ...policy, allowed: [...policy.allowed, 'GPL-2.0 WITH Classpath-exception-2.0'] }
    assert.equal(evaluate('(GPL-2.0 WITH Classpath-exception-2.0 OR GPL-3.0-only)', 'x', withException).ok, true)
    assert.equal(evaluate('GPL-2.0 WITH Classpath-exception-2.0', 'x', policy).ok, false)
  })
})

describe('解析できない式', () => {
  const invalid = [
    ['(MIT', /括弧が閉じていない/],
    ['MIT)', /\) の位置が正しくない/],
    ['((MIT)', /括弧が閉じていない/],
    ['MIT OR', /式が途中で終わっている/],
    ['OR MIT', /OR の位置が正しくない/],
    ['MIT AND AND ISC', /AND の位置が正しくない/],
    ['MIT OR OR ISC', /OR の位置が正しくない/],
    ['MIT AND OR ISC', /OR の位置が正しくない/],
    ['()', /\) の位置が正しくない/],
    ['MIT ()', /\( の位置が正しくない/],
    ['(MIT) (ISC)', /\( の位置が正しくない/],
    ['   ', /式が空/],
    ['', /式が空/],
  ]
  for (const [expression, message] of invalid) {
    test(`「${expression}」は、解析できない式として、理由つきで失敗にする`, () => {
      const result = evaluate(expression, 'some-package', policy)
      assert.equal(result.ok, false)
      assert.match(result.reason, /^解析できないライセンスの式（/)
      assert.match(result.reason, message)
    })
  }
})
