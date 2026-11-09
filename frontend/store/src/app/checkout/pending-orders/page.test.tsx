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

/** 上限に達したことを示す文（一部） */
const LIMIT_TEXT = /お支払い待ちのご注文が上限に達しているため、新しく注文できません/

/**
 * 画面の引数（クエリ付き）を作る。
 *
 * @param query URL のクエリ
 */
function pageProps(query: Record<string, string> = {}) {
  return { params: Promise.resolve({}), searchParams: Promise.resolve(query) }
}

/**
 * 確認の画面を DOM に読み込む（会員がログインしている）。
 *
 * @param items 支払待ちの注文の API の応答
 * @param query URL のクエリ
 */
async function renderPage(items: (typeof PAYABLE)[], query: Record<string, string> = {}) {
  signInAs()
  server.use(http.get('/me/orders/pending', ({ response }) => response(200).json({ items })))
  return renderStatic(await PendingOrdersPage(pageProps(query)))
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

    expect(view.getByRole('heading', { name: 'お支払い待ちのご注文があります' })).toBeInTheDocument()
    expect(view.getByText(/このまま新しく注文すると、別のご注文になります/)).toBeInTheDocument()
    expect(view.getByText(/お支払いのないご注文は、お支払い期限を過ぎると自動でキャンセルされます/)).toBeInTheDocument()
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

  test('AC-411-9、AC-411-10 クエリ limit=reached があると、上限に達したため新しく注文できないことと、支払うか期限切れを待つ案内を示す', async () => {
    const view = await renderPage([PAYABLE], { limit: 'reached' })

    expect(view.getByText(LIMIT_TEXT)).toBeInTheDocument()
    expect(view.getByText(/お支払いいただくか、お支払い期限が過ぎるまでお待ちください/)).toBeInTheDocument()
  })

  test('AC-411-9 クエリがない、または limit が reached 以外なら、上限の文を示さない', async () => {
    const without = await renderPage([PAYABLE])
    expect(without.queryByText(LIMIT_TEXT)).toBeNull()

    const other = await renderPage([PAYABLE], { limit: 'other' })
    expect(other.queryByText(LIMIT_TEXT)).toBeNull()
  })

  test('AC-411-11 上限の文を示す画面でも、支払える注文には「支払へ進む」を示し、「新しく注文する」と「戻る」も示す', async () => {
    const view = await renderPage([PAYABLE, EXPIRED], { limit: 'reached' })

    expect(view.getAllByRole('button', { name: '支払へ進む' })).toHaveLength(1)
    expect(view.getByRole('button', { name: '新しく注文する' })).toBeInTheDocument()
    expect(view.getByRole('link', { name: '戻る' })).toHaveAttribute('href', '/cart')
  })

  test('支払待ちの注文がなければ、カートへ移る', async () => {
    signInAs()
    server.use(http.get('/me/orders/pending', ({ response }) => response(200).json({ items: [] })))

    expect(await redirectedTo(() => PendingOrdersPage(pageProps()))).toBe('/cart')
  })
})
