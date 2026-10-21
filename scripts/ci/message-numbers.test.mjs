import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findNumbers, findStaleExclusions, parseProperties } from './message-numbers.mjs'

describe('エラーの文の数字の検査', () => {
  test('AC-266 差し込みを除いた文に数字（半角・全角）があれば見つける', () => {
    const text = [
      'ecsite.order.invalid_postal_code=郵便番号は数字 7 桁で入力してください。',
      'ecsite.order.invalid_phone_number=電話番号は数字 １０ 桁で入力してください。',
      'ecsite.order.invalid_address=住所を {max} 文字以内で入力してください。',
    ].join('\n')
    assert.deepEqual(
      findNumbers(text, {}).map((hit) => `${hit.line} ${hit.key}`),
      ['1 ecsite.order.invalid_postal_code', '2 ecsite.order.invalid_phone_number'],
    )
  })

  test('例外の一覧のキーと、メールの文（ecsite.notification.*）は見ない', () => {
    const text = [
      'ecsite.catalog.single_variant_only=バリアントを 1 つだけ登録できます。',
      'ecsite.notification.shipment.body=1 点を出荷しました。',
    ].join('\n')
    assert.deepEqual(findNumbers(text, { 'ecsite.catalog.single_variant_only': '数え方の言葉' }), [])
  })

  test('注釈と空行を飛ばし、行末の \\ で続く行をつなぐ', () => {
    const text = ['# 注釈 1', '', 'ecsite.a=前半 \\', '    後半 2', 'ecsite.b=数字なし'].join('\n')
    assert.deepEqual(parseProperties(text), [
      { key: 'ecsite.a', value: '前半 後半 2', line: 3 },
      { key: 'ecsite.b', value: '数字なし', line: 5 },
    ])
  })

  test('ファイルにないキーと、数字のなくなった文のキーの例外は、不要な例外として見つける', () => {
    const text = 'ecsite.a={max} 文字以内\necsite.b=1 つだけ'
    assert.deepEqual(findStaleExclusions(text, { 'ecsite.a': '理由', 'ecsite.b': '理由', 'ecsite.c': '理由' }), [
      'ecsite.a',
      'ecsite.c',
    ])
  })
})
