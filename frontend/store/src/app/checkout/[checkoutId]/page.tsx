import type { Metadata } from 'next'
import { notFound, redirect } from 'next/navigation'
import { getTranslations } from 'next-intl/server'
import { getMemberApi } from '@/api/client'
import { unwrapMember } from '@/api/result'
import { currentMember, redirectToLogin } from '@/auth'
import { AmountsSummary } from '@/components/AmountsSummary'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { OrderForm } from '@/features/checkout/components/OrderForm'
import { RemainingTime } from '@/features/checkout/components/RemainingTime'
import { formatYen } from '@/lib/orderDisplay'
import { isUuid } from '@/lib/uuid'

/** ブラウザのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('checkout.page')
  return { title: t('title') } // ご注文手続き
}

/**
 * 注文手続き（T2505、T3407。AC-84〜89、125〜127）。注文が成立済みなら、注文の画面へ移る。<br />
 * ログインを求め、始めた会員のものでなければ「見つかりません」にする（AC-126）。
 */
export default async function CheckoutPage({ params }: PageProps<'/checkout/[checkoutId]'>) {
  const t = await getTranslations('checkout.page')
  const { checkoutId } = await params
  if (!isUuid(checkoutId)) {
    notFound()
  }
  const member = await currentMember()
  if (member === null) {
    return redirectToLogin()
  }
  const api = await getMemberApi()
  const checkout = await unwrapMember(await api.GET('/checkouts/{checkoutId}', { params: { path: { checkoutId } } }))
  if (checkout.orderId) {
    redirect(`/orders/${checkout.orderId}`)
  }

  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">{t('title') /* ご注文手続き */}</h1>
      <RemainingTime expiresAt={checkout.expiresAt} />
      <div className="grid gap-8 md:grid-cols-[1fr_22rem]">
        <section className="space-y-4">
          <h2 className="font-semibold">{t('items') /* ご注文の内容 */}</h2>
          <ul className="divide-y">
            {checkout.items.map((item) => (
              <li className="py-4 first:pt-0" key={item.variantId}>
                <QuoteItemSummary item={item}>
                  <div className="text-right text-sm">
                    <p>{t('quantity', { quantity: item.quantity }) /* 数量 */}</p>
                    <p className="font-semibold tabular-nums">{formatYen(item.subtotal)}</p>
                  </div>
                </QuoteItemSummary>
              </li>
            ))}
          </ul>
          <div className="rounded-lg border p-4">
            <AmountsSummary amounts={checkout.amounts} />
          </div>
        </section>
        <section className="space-y-4">
          <h2 className="font-semibold">{t('shippingAddress') /* お届け先 */}</h2>
          <OrderForm checkoutId={checkout.id} email={member.email} expectedTotal={checkout.amounts.total} />
        </section>
      </div>
    </div>
  )
}
