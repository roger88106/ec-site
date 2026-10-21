import type { Metadata } from 'next'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'
import { currentCartChoice, memberCartId } from '@/api/cart'
import { readCartMergeNotice } from '@/api/cartCookie'
import { getApi, getMemberApi, UNCACHED } from '@/api/client'
import { unwrap, unwrapMember } from '@/api/result'
import type { Cart } from '@/api/types'
import { AmountsSummary } from '@/components/AmountsSummary'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { CartItemControls } from '@/features/cart/components/CartItemControls'
import { CartMergeNoticeView } from '@/features/cart/components/CartMergeNoticeView'
import { StartCheckoutForm } from '@/features/cart/components/StartCheckoutForm'

/** ブラウザのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('cart.page')
  return { title: t('title') } // カート
}

/**
 * 使うカート（店舗画面の構成 第 4.5 節）を取得する。数量がすぐ変わるので、キャッシュを使わない。
 * ログインしていれば会員のカート、していなければ Cookie のカート。
 *
 * @returns カート。Cookie のカートがない、またはもうなければ null（空のカートとして出す）
 */
async function fetchCart(): Promise<Cart | null> {
  const choice = await currentCartChoice()
  if (choice.kind === 'member') {
    const cartId = await memberCartId()
    const api = await getMemberApi()
    return unwrapMember(await api.GET('/carts/{cartId}', { params: { path: { cartId } } }))
  }
  if (choice.kind === 'none') {
    return null
  }
  const api = await getApi()
  const result = await api.GET('/carts/{cartId}', { params: { path: { cartId: choice.cartId } }, ...UNCACHED })
  return result.response.status === 404 ? null : unwrap(result)
}

/** カート */
export default async function CartPage() {
  const t = await getTranslations('cart.page')
  const [cart, mergeNotice] = await Promise.all([fetchCart(), readCartMergeNotice()])
  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">{t('title') /* カート */}</h1>
      <CartMergeNoticeView notice={mergeNotice} />
      {cart === null || cart.items.length === 0 ? (
        <div className="space-y-4">
          <p className="text-muted-foreground">{t('empty') /* 空 */}</p>
          <Link className="text-sm underline" href="/products">
            {t('findProducts') /* 商品を探す */}
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
