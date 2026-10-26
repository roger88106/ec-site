import { describe, expect, test } from 'vitest'
import type { Variant } from '@/api/types'
import { AddToCartForm, AddToCartFormView } from '@/features/catalog/components/AddToCartForm'
import type { ActionResult } from '@/lib/actionResult'
import { VARIANT_ID } from '@/test/fixtures'
import { renderStatic } from '@/test/render'

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
    // 文の要素は、自分では読み上げの領域にしない
    expect(after.getByText('入力内容に誤りがあります。')).not.toHaveAttribute('aria-live')
  })

  test('AC-193-2、AC-193-3 成功の知らせも同じ領域に出て、前の失敗の文は出ない', () => {
    const view = renderForm({ ok: true })

    const status = view.getByRole('status')
    expect(view.getAllByRole('status')).toHaveLength(1)
    expect(status).toContainElement(view.getByRole('link', { name: 'カートを見る' }))
    expect(status).not.toHaveTextContent('入力内容に誤りがあります。')
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
