// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { addToCart } from '@/actions/cart'
import type { Variant } from '@/api/types'
import { AddToCartForm, AddToCartFormView } from '@/features/catalog/components/AddToCartForm'
import type { ActionResult } from '@/lib/actionResult'
import { VARIANT_ID } from '@/test/fixtures'
import { renderInBrowser, renderStatic } from '@/test/render'

// カートに入れる操作はサーバーアクションを通るので、結果の表示だけを、差し替えた結果で確認する（テストの書き方 第 1 節）
vi.mock('@/actions/cart', () => ({ addToCart: vi.fn(async () => ({ ok: true })) }))

/** 買えるバリアント */
const VARIANT: Variant = { id: VARIANT_ID, axisValues: [], inStock: true, salesSuspended: false, imageUrl: null }

/** 表示だけを確認するので、送る関数は何もしない */
function renderForm(result: ActionResult | null) {
  return renderStatic(<AddToCartFormView formAction={() => {}} pending={false} result={result} variant={VARIANT} />)
}

describe('カートに入れる操作の失敗', () => {
  test('AC-193-1、AC-193-2 DC-135-10 失敗の文は、送る前からある状態の知らせ（status）の領域の中に出る。領域は入れ子にしない', () => {
    // 失敗の前も、領域はあり、文はない
    expect(renderForm(null).getByRole('status')).toBeEmptyDOMElement()

    const after = renderForm({ ok: false, message: '入力内容に誤りがあります。', errorCode: 'INVALID_REQUEST' })

    // 領域は 1 つだけで、文はその中にある
    expect(after.getAllByRole('status')).toHaveLength(1)
    expect(after.getByRole('status')).toHaveTextContent('入力内容に誤りがあります。')
    // 文の要素は、自分では status の役割を持たない
    expect(after.getByText('入力内容に誤りがあります。')).not.toHaveAttribute('role')
  })

  test('AC-193-2 成功の知らせも同じ領域に出る', () => {
    const view = renderForm({ ok: true })

    const statusRegion = view.getByRole('status')
    expect(view.getAllByRole('status')).toHaveLength(1)
    expect(statusRegion).toContainElement(view.getByRole('link', { name: 'カートを見る' }))
  })

  test('AC-193-3 失敗の後に成功したとき、同じ領域が残り、前の失敗の文は消えて成功の知らせが入る', async () => {
    vi.mocked(addToCart).mockResolvedValueOnce({
      ok: false,
      message: '在庫が足りません。',
      errorCode: 'OUT_OF_STOCK',
    })
    const { user } = renderInBrowser(<AddToCartForm variant={VARIANT} />)
    const statusRegion = screen.getByRole('status')

    await user.click(screen.getByRole('button', { name: 'カートに入れる' }))
    expect(await screen.findByText('在庫が足りません。')).toBeInTheDocument()
    expect(screen.getByRole('status')).toBe(statusRegion)

    // 2 回目は、モックの既定の成功を返す
    await user.click(screen.getByRole('button', { name: 'カートに入れる' }))
    const link = await screen.findByRole('link', { name: 'カートを見る' })

    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(statusRegion)
    expect(statusRegion).not.toHaveTextContent('在庫が足りません。')
    expect(statusRegion).toContainElement(link)
  })
})

describe('結果を受ける前の初期表示', () => {
  test('AddToCartForm は、表示と同じ初期表示を出す', () => {
    const view = renderStatic(<AddToCartForm variant={VARIANT} />)
    const initial = renderForm(null)

    expect(view.queryAllByText(/./).map((element) => element.textContent)).toEqual(
      initial.queryAllByText(/./).map((element) => element.textContent),
    )
    expect(view.getByRole('button', { name: 'カートに入れる' })).toBeEnabled()
    expect(view.getByDisplayValue(VARIANT_ID)).toHaveAttribute('name', 'variantId')
  })
})
