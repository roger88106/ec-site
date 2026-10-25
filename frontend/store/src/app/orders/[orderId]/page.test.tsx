import { describe, expect, test } from 'vitest'
import type { Order } from '@/api/types'
import OrderPage from '@/app/orders/[orderId]/page'
import { signInAs } from '@/test/auth'
import { ORDER_ID, order } from '@/test/fixtures'
import { renderStatic } from '@/test/render'
import { http, server } from '@/test/server'

/** 受付済みの注文（キャンセルを申請できる。支払の欄は出さない） */
const ACCEPTED = order()

/** 支払待ちの注文（支払える。キャンセルの欄は出さない） */
const PENDING_PAYMENT = order({
  status: 'PENDING_PAYMENT',
  paymentDeadline: '2026-10-10T15:35:00Z',
  actions: { requestCancellation: false, pay: true },
})

/** 出荷済みの注文（キャンセルの欄を出さない） */
const SHIPPED = order({ status: 'SHIPPED', actions: { requestCancellation: false, pay: false } })

/**
 * 注文の画面を DOM に読み込む（会員がログインしている）。
 *
 * @param apiOrder 注文の API が返す注文
 * @returns 画面を対象にした問い合わせの関数
 */
async function renderOrderPage(apiOrder: Order) {
  signInAs()
  server.use(
    http.get('/orders/{orderId}', ({ response }) => response(200).json(apiOrder)),
    http.get('/shop', ({ response }) => response(200).json({ contactEmail: 'support@example.com' })),
  )
  const page = await OrderPage({
    params: Promise.resolve({ orderId: ORDER_ID }),
    searchParams: Promise.resolve({}),
  })
  return renderStatic(page)
}

describe('注文の画面の欄の出し分け', () => {
  test('AC-151 支払待ちの注文は、支払の欄を出し、「支払う」を示す', async () => {
    const view = await renderOrderPage(PENDING_PAYMENT)

    expect(view.getByRole('heading', { name: 'お支払い' })).toBeInTheDocument()
    expect(view.getByRole('button', { name: '支払う' })).toBeInTheDocument()
  })

  test('支払待ちでない注文は、支払の欄を出さない', async () => {
    const view = await renderOrderPage(ACCEPTED)

    expect(view.queryByRole('heading', { name: 'お支払い' })).toBeNull()
    expect(view.queryByRole('button', { name: '支払う' })).toBeNull()
  })

  test('AC-158 支払待ちの注文は、キャンセルの欄を出さない', async () => {
    const view = await renderOrderPage(PENDING_PAYMENT)

    expect(view.queryByRole('heading', { name: 'キャンセル' })).toBeNull()
  })

  test('AC-171 出荷済みの注文は、キャンセルの欄を出さない', async () => {
    const view = await renderOrderPage(SHIPPED)

    expect(view.queryByRole('heading', { name: 'キャンセル' })).toBeNull()
  })

  test('キャンセルを申請できる注文は、キャンセルの欄を出す', async () => {
    const view = await renderOrderPage(ACCEPTED)

    expect(view.getByRole('heading', { name: 'キャンセル' })).toBeInTheDocument()
  })
})
