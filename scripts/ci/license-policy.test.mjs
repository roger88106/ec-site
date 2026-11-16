import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { checkDocument, renderTable, replaceGenerated } from './license-policy.mjs'

const policy = { allowed: ['MIT', 'ISC'], conditional: { licenses: ['MPL-2.0'] }, forbidden: ['GPL-3.0-only'] }

const doc = (body) => ['## 依存のライセンスの方針', '', '<!-- generated:license-policy -->', body, '<!-- /generated:license-policy -->', '', '## 次'].join('\n')

describe('方針の表の生成', () => {
  test('区分ごとに、policy.json のライセンスを書いた順に「、」でつなぎ、補足と扱いを続ける', () => {
    const lines = renderTable(policy).trimEnd().split('\n')
    assert.deepEqual(lines.slice(0, 2), ['| 区分 | ライセンス | 扱い |', '|------|------------|------|'])
    assert.match(lines[2], /^\| 許可 \| MIT、ISC。他に、.+ \| .+ \|$/)
    assert.match(lines[3], /^\| 条件付き \| MPL-2\.0。他に、.+ \| .+ \|$/)
    assert.match(lines[4], /^\| 禁止 \| GPL-3\.0-only。他に、.+ \| 使わない \|$/)
  })
})

describe('文書の照合', () => {
  test('生成の範囲が作った表と同じなら問題はない（範囲の前後の空行と CRLF は比べない）', () => {
    const table = renderTable(policy)
    assert.deepEqual(checkDocument(doc(table), table), [])
    assert.deepEqual(checkDocument(doc(`\n${table}`).replaceAll('\n', '\r\n'), table), [])
  })

  test('policy.json にだけあるライセンスがあれば、表が違うとして失敗にする', () => {
    const table = renderTable(policy)
    const added = renderTable({ ...policy, allowed: [...policy.allowed, 'Zlib'] })
    assert.equal(checkDocument(doc(table), added).length, 1)
  })

  test('生成の範囲がない、閉じが前にある、2 組あるときは失敗にする', () => {
    const table = renderTable(policy)
    assert.equal(checkDocument('## 依存のライセンスの方針\n', table).length, 1)
    assert.equal(checkDocument('<!-- /generated:license-policy -->\n<!-- generated:license-policy -->\n', table).length, 1)
    assert.equal(checkDocument(`${doc(table)}\n${doc(table)}`, table).length, 1)
  })
})

describe('文書の再生成', () => {
  test('生成の範囲だけを作った表に置き換え、範囲の外は変えない', () => {
    const table = renderTable(policy)
    const written = replaceGenerated(doc('| 古い表 |'), table)
    assert.equal(written, doc(table.trimEnd()))
    assert.deepEqual(checkDocument(written, table), [])
  })

  test('リポジトリの文書の表は、policy.json から作った表と同じ', () => {
    const repoPolicy = JSON.parse(readFileSync(new URL('./licenses/policy.json', import.meta.url), 'utf8'))
    const document = readFileSync(new URL('../../docs/05-開発規則/licenses.md', import.meta.url), 'utf8')
    assert.deepEqual(checkDocument(document, renderTable(repoPolicy)), [])
  })
})
