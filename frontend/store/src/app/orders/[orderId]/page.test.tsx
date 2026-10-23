import { describe, expect, test } from 'vitest'
import type { Order } from '@/api/types'
import OrderPage from '@/app/orders/[orderId]/page'
import { signInAs } from '@/test/auth'
import { ORDER_ID } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'
import { http, server } from '@/test/server'

/** 受付済みの注文（キャンセルを申請できる。支払の欄は出さない） */
const ACCEPTED: Order = {
  id: ORDER_ID,
  orderNumber: '20261010-482915',
  orderedAt: '2026-10-10T03:00:00Z',
  status: 'ACCEPTED',
  lines: [],
  amounts: { itemsTotal: 0, shippingFee: 0, total: 0, taxes: [] },
  email: 'member@example.com',
  shippingAddress: {
    name: '山田 太郎',
    postalCode: '1000001',
    prefectureCode: '13',
    prefectureName: '東京都',
    municipalityCode: '13101',
    municipalityName: '千代田区',
    address: '千代田 1-1',
    phone: '0312345678',
  },
  actions: { requestCancellation: true, pay: false },
}

/** 支払待ちの注文（支払える。キャンセルの欄は出さない） */
const PENDING_PAYMENT: Order = {
  ...ACCEPTED,
  status: 'PENDING_PAYMENT',
  paymentDeadline: '2026-10-10T15:35:00Z',
  actions: { requestCancellation: false, pay: true },
}

/** 出荷済みの注文（キャンセルの欄を出さない） */
const SHIPPED: Order = { ...ACCEPTED, status: 'SHIPPED', actions: { requestCancellation: false, pay: false } }

/**
 * 注文の画面を HTML にする（会員がログインしている）。
 *
 * @param order 注文の API が返す注文
 * @returns HTML の文字
 */
async function renderOrderPage(order: Order): Promise<string> {
  signInAs()
  server.use(
    http.get('/orders/{orderId}', ({ response }) => response(200).json(order)),
    http.get('/shop', ({ response }) => response(200).json({ contactEmail: 'support@example.com' })),
  )
  const page = await OrderPage({
    params: Promise.resolve({ orderId: ORDER_ID }),
    searchParams: Promise.resolve({}),
  } as Parameters<typeof OrderPage>[0])
  return renderMarkup(page)
}

/** 見出し（欄の題名）が HTML にあるか */
function hasHeading(html: string, name: string): boolean {
  return new RegExp(`<h[1-6][^>]*>${name}</h[1-6]>`).test(html)
}

/** 「支払う」のボタンが HTML にあるか */
function hasPayButton(html: string): boolean {
  return /<button[^>]*>支払う<\/button>/.test(html)
}

describe('注文の画面の欄の出し分け', () => {
  test('AC-151 支払待ちの注文は、支払の欄を出し、「支払う」を示す', async () => {
    const html = await renderOrderPage(PENDING_PAYMENT)

    expect(hasHeading(html, 'お支払い')).toBe(true)
    expect(hasPayButton(html)).toBe(true)
  })

  test('支払待ちでない注文は、支払の欄を出さない', async () => {
    const html = await renderOrderPage(ACCEPTED)

    expect(hasHeading(html, 'お支払い')).toBe(false)
    expect(hasPayButton(html)).toBe(false)
  })

  test('AC-158 支払待ちの注文は、キャンセルの欄を出さない', async () => {
    const html = await renderOrderPage(PENDING_PAYMENT)

    expect(hasHeading(html, 'キャンセル')).toBe(false)
  })

  test('AC-171 出荷済みの注文は、キャンセルの欄を出さない', async () => {
    const html = await renderOrderPage(SHIPPED)

    expect(hasHeading(html, 'キャンセル')).toBe(false)
  })

  test('キャンセルを申請できる注文は、キャンセルの欄を出す', async () => {
    const html = await renderOrderPage(ACCEPTED)

    expect(hasHeading(html, 'キャンセル')).toBe(true)
  })
})
