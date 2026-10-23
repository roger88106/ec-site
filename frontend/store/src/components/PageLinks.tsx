import { LinkButton } from '@ec-site/shared/components/LinkButton'
import { Button } from '@ec-site/shared/ui/button'
import type { Route } from 'next'
import Link from 'next/link'
import { useTranslations } from 'next-intl'

/** ページ送り（API の規約のレスポンスの page、totalPages、totalItems を受ける）。リンクで移る */
export function PageLinks({
  page,
  totalPages,
  totalItems,
  hrefFor,
}: {
  page: number
  totalPages: number
  totalItems: number
  hrefFor: (page: number) => Route
}) {
  const t = useTranslations('common.pagination')
  const tPage = useTranslations('components.pageLinks')
  if (totalPages <= 1) {
    return <p className="text-muted-foreground text-sm">{t('total', { totalItems }) /* 件数 */}</p>
  }
  return (
    <nav aria-label={tPage('label') /* ページ */} className="flex items-center justify-between gap-4 text-sm">
      <span className="text-muted-foreground">{t('summary', { totalItems, page, totalPages }) /* 件数とページ */}</span>
      <div className="flex gap-2">
        {page > 1 ? (
          <LinkButton link={<Link href={hrefFor(page - 1)} />} size="sm" variant="outline">
            {t('previous') /* 前へ */}
          </LinkButton>
        ) : (
          <Button disabled size="sm" variant="outline">
            {t('previous') /* 前へ */}
          </Button>
        )}
        {page < totalPages ? (
          <LinkButton link={<Link href={hrefFor(page + 1)} />} size="sm" variant="outline">
            {t('next') /* 次へ */}
          </LinkButton>
        ) : (
          <Button disabled size="sm" variant="outline">
            {t('next') /* 次へ */}
          </Button>
        )}
      </div>
    </nav>
  )
}
