import { describe, expect, test, vi } from 'vitest'
import { OrderForm } from '@/features/checkout/components/OrderForm'
import { PREFECTURES } from '@/features/checkout/prefectures'
import type { ActionResult } from '@/lib/actionResult'
import { renderMarkup } from '@/test/render'

/** 注文のサーバーアクションの結果として、フォームに渡す値（null は送る前） */
let actionResult: ActionResult | null = null

// 注文の送信はサーバーアクションを通るので、送った結果の表示だけを、結果を差し替えて HTML で確かめる（テストの書き方 第 1 節）
vi.mock('react', async (importOriginal) => {
  const react = await importOriginal<typeof import('react')>()
  return { ...react, useActionState: () => [actionResult, () => {}, false] }
})

function renderForm(result: ActionResult | null = null): string {
  actionResult = result
  return renderMarkup(<OrderForm checkoutId="checkout-1" email="member@example.com" expectedTotal={3200} />)
}

describe('お届け先の入力と注文', () => {
  test('AC-127 会員のメールアドレスを示し、お届け先の欄と、総額を添えた注文のボタンを出す', () => {
    const html = renderForm()

    expect(html).toContain('member@example.com')
    expect(html).toContain('ご注文の連絡は、会員のメールアドレスにお送りします。')
    expect(html).toMatch(/<input [^>]*name="checkoutId"[^>]*value="checkout-1"/)
    expect(html).toMatch(/<input [^>]*name="expectedTotal"[^>]*value="3200"/)
    for (const name of ['name', 'postalCode', 'address', 'phone']) {
      expect(html).toContain(`name="${name}"`)
    }
    expect(html).toContain('>注文する（3,200 円）</button>')
    expect(html).not.toContain('text-destructive')
  })

  test('都道府県は 47 の中から選び、初めは選んでいない', () => {
    const html = renderForm()

    expect(PREFECTURES).toHaveLength(47)
    expect(PREFECTURES[0]).toBe('北海道')
    expect(PREFECTURES.at(-1)).toBe('沖縄県')
    expect(html).toMatch(/<option[^>]* disabled="" value="" selected="">選んでください<\/option>/)
    expect(html).toContain('>沖縄県</option>')
  })

  test('AC-86 入力の誤りは、その欄の下に出す', () => {
    const html = renderForm({
      ok: false,
      message: '郵便番号は 7 桁で入力してください。',
      errorCode: 'INVALID_POSTAL_CODE',
    })

    expect(html).toMatch(
      /name="postalCode"[^>]*\/>.*<p class="text-destructive text-sm">郵便番号は 7 桁で入力してください。<\/p>/,
    )
    expect(html).not.toContain('カートを見る')
  })

  test('AC-87 在庫が足りなければ、フォームの下にメッセージとカートへのリンクを出す', () => {
    const html = renderForm({ ok: false, message: '在庫が足りない商品があります。', errorCode: 'OUT_OF_STOCK' })

    expect(html).toContain('在庫が足りない商品があります。')
    expect(html).toContain('href="/cart">カートを見る</a>')
  })
})
