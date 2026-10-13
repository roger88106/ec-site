import Link from 'next/link'
import { useTranslations } from 'next-intl'

/** 見つからない（存在しない商品と、C 側に表示されない商品を区別しない。AC-65）。注文手続き・注文の URL の誤りも同じ */
export default function NotFound() {
  const t = useTranslations('app.notFound')
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="font-heading font-semibold text-2xl">{t('title') /* 見つからない */}</h1>
      <p className="text-muted-foreground">{t('message') /* 見つからない理由 */}</p>
      <Link className="text-sm underline" href="/products">
        {t('toProducts') /* 商品一覧へ */}
      </Link>
    </div>
  )
}
