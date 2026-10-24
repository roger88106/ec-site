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
  test('DC-135-10 失敗の文の要素は、自分では読み上げの領域にしない（領域を入れ子にしない）', () => {
    // 失敗の前は、文はない
    expect(renderForm(null).queryByText('入力内容に誤りがあります。')).toBeNull()

    const after = renderForm({ ok: false, message: '入力内容に誤りがあります。', errorCode: 'INVALID_REQUEST' })

    // 文の要素は、自分では読み上げの領域にしない（領域は親にある）
    expect(after.getByText('入力内容に誤りがあります。')).not.toHaveAttribute('aria-live')
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
