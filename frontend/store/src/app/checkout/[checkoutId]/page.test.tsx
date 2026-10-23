import { describe, expect, test } from 'vitest'
import CheckoutPage from '@/app/checkout/[checkoutId]/page'
import { signInAs } from '@/test/auth'
import { expectNotFound, redirectedTo, resetRequest } from '@/test/next'

/** 形の違う ID（UUID でない） */
const MALFORMED_ID = 'not-a-uuid'

/**
 * 注文手続きの画面を、指定の ID で開く。
 *
 * @param checkoutId URL の注文手続きの ID
 */
function openCheckoutPage(checkoutId: string) {
  return CheckoutPage({
    params: Promise.resolve({ checkoutId }),
    searchParams: Promise.resolve({}),
  })
}

describe('注文手続きの画面の確認の順', () => {
  test('DC-135-14 ログインしていない人が形の違う ID で開くと、ログインの画面に移る', async () => {
    resetRequest({ headers: { 'x-pathname': `/checkout/${MALFORMED_ID}` } })

    expect(await redirectedTo(() => openCheckoutPage(MALFORMED_ID))).toBe(
      `/login?returnTo=${encodeURIComponent(`/checkout/${MALFORMED_ID}`)}`,
    )
  })

  test('ログインした人が形の違う ID で開くと、「見つかりません」になる', async () => {
    signInAs()

    await expectNotFound(() => openCheckoutPage(MALFORMED_ID))
  })
})
