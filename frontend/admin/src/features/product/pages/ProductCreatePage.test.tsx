import { screen, within } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import type { components } from '@/api/generated/admin'
import { PRODUCT_ID, productDetail, taxCategory } from '@/test/fixtures'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

/** 商品の登録の要求の本文（送った内容を確認する） */
type CreateProductRequest = components['schemas']['CreateProductRequest']

/** 商品の登録の画面が使う API に応答する。登録の要求は requests に取っておく */
function serveCreate(reply: (body: CreateProductRequest) => Response) {
  const requests: CreateProductRequest[] = []
  server.use(
    http.get('/tax-categories', ({ response }) => response(200).json({ items: [taxCategory()] })),
    http.get('/categories', ({ response }) => response(200).json({ items: [] })),
    http.post('/products', async ({ request }) => {
      const body = await request.json()
      requests.push(body)
      return reply(body) as never
    }),
  )
  return requests
}

/**
 * 入力欄に文字を貼り付ける。1 文字ずつ打つと、打つたびにフォームを描き直して遅く、
 * 並べて流すと既定の時間切れを超えることがあるため（打った時点の変換を確認するテストだけは打つ）
 */
async function paste(user: UserEvent, input: HTMLElement, text: string) {
  await user.click(input)
  await user.paste(text)
}

/** 入力欄を囲む欄（ラベル・説明・エラーを含む） */
function fieldOf(input: HTMLElement): HTMLElement {
  const field = input.closest<HTMLElement>('[data-slot="field"]')
  if (!field) {
    throw new Error('入力欄を囲む欄がない')
  }
  return field
}

describe('商品の登録', () => {
  beforeEach(() => {
    server.use(
      // 登録の後に移る、商品の編集の画面
      http.get('/products/{productId}', ({ response }) => response(200).json(productDetail())),
    )
  })

  it('AC-15 品番の英小文字は、入力した時点で大文字になる', async () => {
    serveCreate(() => Response.json({ id: PRODUCT_ID }, { status: 201 }))
    const { user } = renderApp('/products/new')

    await user.type(await screen.findByLabelText('商品品番（登録後は変更できません）'), 'abc-001')

    expect(screen.getByLabelText('商品品番（登録後は変更できません）')).toHaveValue('ABC-001')
  })

  it('AC-15 登録すると、入れた内容（品番は大文字）で登録し、編集の画面に移る', async () => {
    const requests = serveCreate(() => Response.json({ id: PRODUCT_ID }, { status: 201 }))
    const { user, router } = renderApp('/products/new')

    await paste(user, await screen.findByLabelText('商品品番（登録後は変更できません）'), 'abc-001')
    await paste(user, screen.getByLabelText('商品名'), 'T シャツ')
    await user.click(screen.getByRole('combobox', { name: '税区分' }))
    await user.click(await screen.findByRole('option', { name: '標準（現時点の率：10%）' }))
    await paste(user, screen.getByLabelText('税抜価格（円）'), '1000')
    await user.click(screen.getByRole('button', { name: '軸を追加' }))
    await paste(user, screen.getByLabelText('軸 1'), '色')
    await user.click(screen.getByRole('button', { name: '登録' }))

    expect(await screen.findByText('商品を登録しました')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/products/${PRODUCT_ID}`)
    expect(requests).toEqual([
      {
        productCode: 'ABC-001',
        name: 'T シャツ',
        description: '',
        taxCategoryId: productDetail().taxCategoryId,
        priceExclTax: 1000,
        categoryIds: [],
        axes: ['色'],
      },
    ])
  })

  it('AC-15 サーバーの入力検証エラーは、その項目の入力欄の下に出て、画面は移らない', async () => {
    serveCreate(() =>
      Response.json(
        problem(400, 'INVALID_REQUEST', '入力に誤りがあります。', [
          { field: 'productCode', message: '商品品番の形式が正しくありません。' },
        ]),
        { status: 400 },
      ),
    )
    const { user, router } = renderApp('/products/new')

    await paste(user, await screen.findByLabelText('商品品番（登録後は変更できません）'), 'abc--001')
    await paste(user, screen.getByLabelText('商品名'), 'T シャツ')
    await user.click(screen.getByRole('combobox', { name: '税区分' }))
    await user.click(await screen.findByRole('option', { name: '標準（現時点の率：10%）' }))
    await paste(user, screen.getByLabelText('税抜価格（円）'), '1000')
    await user.click(screen.getByRole('button', { name: '登録' }))

    const codeField = fieldOf(screen.getByLabelText('商品品番（登録後は変更できません）'))
    expect(await within(codeField).findByText('商品品番の形式が正しくありません。')).toBeInTheDocument()
    // 入力欄の下に出したエラーは、フォームの下に繰り返さない
    expect(screen.getAllByText('商品品番の形式が正しくありません。')).toHaveLength(1)
    expect(router.state.location.pathname).toBe('/products/new')
  })
})
