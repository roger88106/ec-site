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

  test('条件付きのライセンスは、一覧にない部品なら失敗する', () => {
    assert.equal(evaluate('MPL-2.0', 'some-package', policy).ok, false)
  })

  test('禁止のライセンスは失敗する', () => {
    assert.equal(evaluate('GPL-3.0-only', 'some-package', policy).ok, false)
  })

  test('方針にないライセンス・分からないライセンスは失敗する', () => {
    assert.equal(evaluate('WTFPL', 'some-package', policy).ok, false)
    assert.equal(evaluate('Unknown', 'some-package', policy).ok, false)
    assert.equal(evaluate('', 'some-package', policy).ok, false)
  })

  test('OR は、どれか 1 つが通れば通る', () => {
    assert.equal(evaluate('MIT OR GPL-3.0-only', 'some-package', policy).ok, true)
    assert.equal(evaluate('(GPL-3.0-only OR MIT)', 'some-package', policy).ok, true)
    assert.equal(evaluate('GPL-3.0-only OR AGPL-3.0-only', 'some-package', policy).ok, false)
  })

  test('AND は、すべてが通るときだけ通る', () => {
    assert.equal(evaluate('Apache-2.0 AND LGPL-3.0-or-later', '@img/sharp-win32-x64', policy).ok, true)
    assert.equal(evaluate('Apache-2.0 AND LGPL-3.0-or-later', 'other-binary', policy).ok, false)
    assert.equal(evaluate('MIT AND GPL-3.0-only', 'some-package', policy).ok, false)
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

  test('今の依存に多い式は、今と同じ結果になる', () => {
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
    '(MIT',
    'MIT)',
    '((MIT)',
    'MIT OR',
    'OR MIT',
    'MIT AND AND ISC',
    'MIT OR OR ISC',
    'MIT AND OR ISC',
    '()',
    'MIT ()',
    '(MIT) (ISC)',
    '   ',
    '',
  ]
  for (const expression of invalid) {
    test(`「${expression}」は理由つきで失敗にする`, () => {
      const result = evaluate(expression, 'some-package', policy)
      assert.equal(result.ok, false)
      assert.equal(typeof result.reason, 'string')
      assert.notEqual(result.reason, '')
    })
  }
})
