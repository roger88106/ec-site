import { formatDateTime, formatYen } from '@ec-site/shared/lib/format'
import { describe, expect, test } from 'vitest'
import PendingOrdersPage from '@/app/checkout/pending-orders/page'
import { signInAs } from '@/test/auth'
import { OTHER_ORDER_ID, order } from '@/test/fixtures'
import { redirectedTo } from '@/test/next'
import { renderStatic } from '@/test/render'
import { http, server } from '@/test/server'

/** 支払える支払待ちの注文（明細 1 行、総額 2,200 円） */
const PAYABLE = order({
  status: 'PENDING_PAYMENT',
  orderNumber: '20261010-111111',
  orderedAt: '2026-10-10T03:00:00Z',
  paymentDeadline: '2026-10-10T15:35:00Z',
  lines: [
    {
      productCode: 'P-1',
      variantCode: 'P-1-RED',
      productName: 'T シャツ',
      axisValues: [{ name: '色', value: '赤' }],
      unitPrice: { inclTax: 1100, exclTax: 1000 },
      ratePercent: 10,
      quantity: 2,
      subtotal: 2200,
    },
  ],
  amounts: { itemsTotal: 2200, shippingFee: 0, total: 2200, taxes: [] },
  actions: { requestCancellation: false, pay: true },
})

/** 支払の期限を過ぎた支払待ちの注文（支払えない） */
const EXPIRED = order({
  id: OTHER_ORDER_ID,
  status: 'PENDING_PAYMENT',
  orderNumber: '20261009-222222',
  orderedAt: '2026-10-09T03:00:00Z',
  paymentDeadline: '2026-10-09T15:35:00Z',
  amounts: { itemsTotal: 3000, shippingFee: 0, total: 3000, taxes: [] },
  actions: { requestCancellation: false, pay: false },
})

/**
 * 確認の画面を DOM に読み込む（会員がログインしている）。
 *
 * @param items 支払待ちの注文の API の応答
 */
async function renderPage(items: (typeof PAYABLE)[]) {
  signInAs()
  server.use(http.get('/me/orders/pending', ({ response }) => response(200).json({ items })))
  return renderStatic(await PendingOrdersPage())
}

describe('支払待ちの注文の確認の画面', () => {
  test('AC-406-4 注文ごとに、注文番号、注文日時、明細、総額、支払の期限を示し、複数あれば全てを示す', async () => {
    const view = await renderPage([PAYABLE, EXPIRED])

    expect(view.getByText('20261010-111111')).toBeInTheDocument()
    expect(view.getByText('20261009-222222')).toBeInTheDocument()
    expect(view.getByText(formatDateTime(PAYABLE.orderedAt))).toBeInTheDocument()
    expect(view.getByText(formatDateTime(PAYABLE.paymentDeadline as string))).toBeInTheDocument()
    expect(view.getByText('T シャツ')).toBeInTheDocument()
    expect(view.getByText(`総額 ${formatYen(2200)}`)).toBeInTheDocument()
    expect(view.getByText(`総額 ${formatYen(3000)}`)).toBeInTheDocument()
  })

  test('AC-406-4 案内として、支払待ちの注文があること、新しく注文すると別の注文になること、期限で自動でキャンセルされることを示す', async () => {
    const view = await renderPage([PAYABLE])

    expect(
      view.getByText(/お支払い待ちのご注文があります。このまま新しく注文すると、別のご注文になります/),
    ).toBeInTheDocument()
    expect(view.getByText(/お支払いのないご注文は、お支払い期限になると自動でキャンセルされます/)).toBeInTheDocument()
    expect(view.queryByRole('button', { name: /キャンセル/ })).toBeNull()
  })

  test('AC-406-6 支払える注文にだけ「支払へ進む」を示す（期限を過ぎた注文には示さない）', async () => {
    const view = await renderPage([PAYABLE, EXPIRED])

    expect(view.getAllByRole('button', { name: '支払へ進む' })).toHaveLength(1)
  })

  test('AC-406-8 「戻る」はカートへのリンクである', async () => {
    const view = await renderPage([PAYABLE])

    expect(view.getByRole('link', { name: '戻る' })).toHaveAttribute('href', '/cart')
  })

  test('支払待ちの注文がなければ、カートへ移る', async () => {
    signInAs()
    server.use(http.get('/me/orders/pending', ({ response }) => response(200).json({ items: [] })))

    expect(await redirectedTo(() => PendingOrdersPage())).toBe('/cart')
  })
})
