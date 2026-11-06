import type { Metadata } from 'next'
import { redirect } from 'next/navigation'
import { getTranslations } from 'next-intl/server'
import { getMemberApi } from '@/api/client'
import { unwrapMember } from '@/api/result'
import { requireMember } from '@/auth'
import { PendingOrdersConfirmation } from '@/features/checkout/components/PendingOrdersConfirmation'

/** ブラウザーのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('checkout.pendingOrders')
  return { title: t('title') } // お支払い待ちのご注文があります
}

/**
 * 支払待ちの注文の確認。注文手続きに進む前に、支払うか、新しく注文するか、戻るかを選ばせる。<br />
 * ログインを求め、支払待ちの注文がなければカートへ移る。
 */
export default async function PendingOrdersPage() {
  await requireMember()
  const api = await getMemberApi()
  const { items } = await unwrapMember(await api.GET('/me/orders/pending'))
  if (items.length === 0) {
    redirect('/cart')
  }
  return <PendingOrdersConfirmation orders={items} />
}
