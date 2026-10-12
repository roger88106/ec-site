import { Button } from '@ec-site/shared/ui/button'

/** ページング（constitution 9 のレスポンスの page、totalPages、totalItems を受ける） */
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
  return (
    <div className="flex items-center justify-between gap-4 text-sm">
      <span className="text-muted-foreground">
        全 {totalItems} 件　{totalPages === 0 ? 0 : page} / {totalPages} ページ
      </span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => onChange(page - 1)}>
          前へ
        </Button>
        <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
          次へ
        </Button>
      </div>
    </div>
  )
}
