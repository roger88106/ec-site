import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'use-intl'

/** ページング（API の規約のレスポンスの page、totalPages、totalItems を受ける） */
export function Pagination({
  page,
  totalPages,
  totalItems,
  onChange,
}: {
  page: number
  totalPages: number
  totalItems: number
  onChange: (page: number) => void
}) {
  const t = useTranslations('common.pagination')
  return (
    <div className="flex items-center justify-between gap-4 text-sm">
      <span className="text-muted-foreground">
        {t('summary', { totalItems, page: totalPages === 0 ? 0 : page, totalPages }) /* 件数とページ */}
      </span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => onChange(page - 1)}>
          {t('previous') /* 前へ */}
        </Button>
        <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
          {t('next') /* 次へ */}
        </Button>
      </div>
    </div>
  )
}
