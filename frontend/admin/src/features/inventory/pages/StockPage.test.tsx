import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import { PRODUCT_ID, productDetail, variant } from '@/test/fixtures'
import { getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

/** 在庫の調整の要求の本文（送った内容を確認する） */
type AdjustStockRequest = components['schemas']['AdjustStockRequest']
/** 調整履歴の 1 件 */
type StockAdjustment = components['schemas']['StockAdjustment']

/** 在庫を調整するバリアント */
const VARIANT = variant()
/** 在庫の画面の URL */
const URL = `/products/${PRODUCT_ID}/variants/${VARIANT.id}/stock`

/**
 * 在庫の画面が使う API に応答する。在庫数は quantity から始める。<br />
 * 調整は、調整後に負になるなら 400（INSUFFICIENT_STOCK）、「その他」でメモがなければ 400（STOCK_MEMO_REQUIRED）、
 * それ以外は在庫数に反映する。調整の要求は requests に取っておく。履歴は history を返す。
 */
function serveStock(quantity: number, history: StockAdjustment[] = []) {
  const state = { quantity, requests: [] as AdjustStockRequest[] }
  server.use(
    http.get('/products/{productId}', ({ response }) => response(200).json(productDetail({ variants: [VARIANT] }))),
    http.get('/inventory/variants/{variantId}', ({ response }) =>
      response(200).json({ variantId: VARIANT.id, quantity: state.quantity }),
    ),
    http.get('/inventory/variants/{variantId}/adjustments', ({ response }) =>
      response(200).json({ items: history, page: 1, size: 20, totalItems: history.length, totalPages: 1 }),
    ),
    http.post('/inventory/variants/{variantId}/adjustments', async ({ request, response }) => {
      const body = await request.json()
      state.requests.push(body)
      if (state.quantity + body.delta < 0) {
        return response(400).json(problem(400, 'INSUFFICIENT_STOCK', `在庫が足りません（在庫数 ${state.quantity}）。`))
      }
      if (body.reason === 'OTHER' && !body.memo) {
        return response(400).json(
          problem(400, 'STOCK_MEMO_REQUIRED', '理由が「その他」のときは、メモを入力してください。'),
        )
      }
      state.quantity += body.delta
      return response(201).json({
        id: '00000000-0000-0000-0000-000000000301',
        delta: body.delta,
        reason: body.reason,
        memo: body.memo,
        quantityAfter: state.quantity,
        adjustedAt: '2026-10-10T10:00:00+09:00',
      })
    }),
  )
  return state
}

/** 在庫数（名前「在庫数」の status）が expected になるまで待つ */
async function shownQuantity(expected: number) {
  const quantity = await screen.findByRole('status', { name: '在庫数' })
  await waitFor(() => expect(quantity).toHaveTextContent(new RegExp(`^${expected}$`)))
  return quantity
}

describe('在庫の調整', () => {
  test('増減量と理由で調整すると、結果を知らせ、在庫数を取得し直す', async () => {
    const state = serveStock(5)
    const { user } = renderApp(URL)
    await shownQuantity(5)

    await user.type(screen.getByLabelText('増減量'), '10')
    await user.click(screen.getByRole('combobox', { name: '理由' }))
    await user.click(await screen.findByRole('option', { name: '入荷' }))
    await user.click(screen.getByRole('button', { name: '調整する' }))

    expect(await screen.findByText('+10 を反映し、在庫数は 15 になりました')).toBeInTheDocument()
    expect(await shownQuantity(15)).toBeInTheDocument()
    expect(screen.getByLabelText('増減量')).toHaveValue(null)
    expect(state.requests).toEqual([{ delta: 10, reason: 'ARRIVAL', memo: null }])
  })

  test('AC-43 調整後に負になる調整は拒否され、そのメッセージを出し、在庫数は変わらない', async () => {
    serveStock(2)
    const { user } = renderApp(URL)
    await shownQuantity(2)

    await user.type(screen.getByLabelText('増減量'), '-3')
    await user.click(screen.getByRole('combobox', { name: '理由' }))
    await user.click(await screen.findByRole('option', { name: '破損・紛失' }))
    await user.click(screen.getByRole('button', { name: '調整する' }))

    expect(await screen.findByText('在庫が足りません（在庫数 2）。')).toBeInTheDocument()
    expect(screen.getByLabelText('増減量')).toHaveValue(-3)
    expect(await shownQuantity(2)).toBeInTheDocument()
  })

  test('AC-46 理由が「その他」のときはメモが必須と示し、メモがなければ拒否のメッセージを出す', async () => {
    serveStock(5)
    const { user } = renderApp(URL)
    await shownQuantity(5)

    expect(screen.getByLabelText('メモ（任意）')).toBeInTheDocument()
    await user.type(screen.getByLabelText('増減量'), '1')
    await user.click(screen.getByRole('combobox', { name: '理由' }))
    await user.click(await screen.findByRole('option', { name: 'その他' }))
    expect(screen.getByLabelText('メモ（必須）')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '調整する' }))

    expect(await screen.findByText('理由が「その他」のときは、メモを入力してください。')).toBeInTheDocument()

    await user.type(screen.getByLabelText('メモ（必須）'), '展示品を引き当て')
    await user.click(screen.getByRole('button', { name: '調整する' }))
    expect(await screen.findByText('+1 を反映し、在庫数は 6 になりました')).toBeInTheDocument()
  })

  test('AC-107 注文に由来する調整は理由と注文番号を履歴に出し、調整の理由には選べない', async () => {
    serveStock(3, [
      {
        id: '00000000-0000-0000-0000-000000000302',
        delta: -2,
        reason: 'ORDER',
        memo: null,
        orderId: '00000000-0000-0000-0000-000000000401',
        orderNumber: '20261010-482915',
        quantityAfter: 3,
        adjustedAt: '2026-10-10T10:00:00+09:00',
      },
    ])
    const { user } = renderApp(URL)

    expect(await screen.findByText('注文番号 20261010-482915')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '注文' })).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: '理由' }))
    expect(await screen.findByRole('option', { name: '入荷' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '注文' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '注文のキャンセル' })).not.toBeInTheDocument()
  })
})

describe('操作した運営者', () => {
  test('AC-142 運営者の調整は、調整した運営者の氏名を履歴に出す。注文に由来する調整は空', async () => {
    serveStock(4, [
      {
        id: '00000000-0000-0000-0000-000000000303',
        delta: 2,
        reason: 'ARRIVAL',
        memo: null,
        quantityAfter: 4,
        adjustedAt: '2026-10-10T11:00:00+09:00',
        operatorName: '在庫 一郎',
      },
      {
        id: '00000000-0000-0000-0000-000000000302',
        delta: -2,
        reason: 'ORDER',
        memo: null,
        orderId: '00000000-0000-0000-0000-000000000401',
        orderNumber: '20261010-482915',
        quantityAfter: 2,
        adjustedAt: '2026-10-10T10:00:00+09:00',
        operatorName: null,
      },
    ])
    renderApp(URL)

    const headers = await screen.findAllByRole('columnheader')
    const operatorColumn = headers.findIndex((header) => header.textContent === '操作した運営者')
    expect(operatorColumn).toBeGreaterThanOrEqual(0)
    const operatorOf = (row: HTMLElement) => within(row).getAllByRole('cell')[operatorColumn]
    expect(operatorOf(getRow('在庫 一郎'))).toHaveTextContent('在庫 一郎')
    expect(operatorOf(getRow('注文番号 20261010-482915'))).toBeEmptyDOMElement()
  })
})

describe('権限のない操作', () => {
  test('AC-141 権限のない運営者（閲覧者）が調整すると、権限がないことを調整のフォームに出す', async () => {
    serveStock(5)
    server.use(
      http.post('/inventory/variants/{variantId}/adjustments', ({ response }) =>
        response(403).json(problem(403, 'FORBIDDEN', 'この操作を行う権限がありません。')),
      ),
    )
    const { user } = renderApp(URL)
    await shownQuantity(5)

    await user.type(screen.getByLabelText('増減量'), '10')
    await user.click(screen.getByRole('combobox', { name: '理由' }))
    await user.click(await screen.findByRole('option', { name: '入荷' }))
    await user.click(screen.getByRole('button', { name: '調整する' }))

    expect(await screen.findByText('この操作を行う権限がありません。')).toBeInTheDocument()
    expect(await shownQuantity(5)).toBeInTheDocument()
  })
})
