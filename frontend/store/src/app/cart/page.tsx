import type { Metadata } from 'next'
import Link from 'next/link'
import { readCartId } from '@/api/cartCookie'
import { getApi, UNCACHED } from '@/api/client'
import { unwrap } from '@/api/result'
import type { Cart } from '@/api/types'
import { AmountsSummary } from '@/components/AmountsSummary'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { CartItemControls } from '@/features/cart/components/CartItemControls'
import { StartCheckoutForm } from '@/features/cart/components/StartCheckoutForm'

/** ブラウザのタブの題名 */
export const metadata: Metadata = { title: 'カート' }

/**
 * Cookie のカートを取得する（店舗画面の構成 第 4.2 節）。数量がすぐ変わるので、キャッシュを使わない。
 *
 * @returns カート。Cookie がない、またはカートがもうなければ null（空のカートとして出す）
 */
async function fetchCart(): Promise<Cart | null> {
  const cartId = await readCartId()
  if (cartId === null) {
    return null
  }
  const api = await getApi()
  const result = await api.GET('/carts/{cartId}', { params: { path: { cartId } }, ...UNCACHED })
  return result.response.status === 404 ? null : unwrap(result)
}

/** カート（T2504。AC-77〜80、82、88） */
export default async function CartPage() {
  const cart = await fetchCart()
  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">カート</h1>
      {cart === null || cart.items.length === 0 ? (
        <div className="space-y-4">
          <p className="text-muted-foreground">カートに商品がありません。</p>
          <Link className="text-sm underline" href="/products">
            商品を探す
          </Link>
        </div>
      ) : (
        <div className="grid gap-8 md:grid-cols-[1fr_20rem]">
          <ul className="divide-y">
            {cart.items.map((item) => (
              <li className="py-4 first:pt-0" key={item.variantId}>
                <QuoteItemSummary item={item}>
                  <CartItemControls item={item} />
                </QuoteItemSummary>
              </li>
            ))}
          </ul>
          <aside className="space-y-4 self-start rounded-lg border p-4">
            <AmountsSummary amounts={cart.amounts} />
            <StartCheckoutForm orderable={cart.orderable} />
          </aside>
        </div>
      )}
    </div>
  )
}
