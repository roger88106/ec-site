import Link from 'next/link'
import { Button } from '@/components/ui/button'

/** ページ送り（constitution 9 のレスポンスの page、totalPages、totalItems を受ける）。リンクで移る */
export function PageLinks({
  page,
  totalPages,
  totalItems,
  hrefFor,
}: {
  page: number
  totalPages: number
  totalItems: number
  hrefFor: (page: number) => string
}) {
  if (totalPages <= 1) {
    return <p className="text-muted-foreground text-sm">全 {totalItems} 件</p>
  }
  return (
    <nav aria-label="ページ" className="flex items-center justify-between gap-4 text-sm">
      <span className="text-muted-foreground">
        全 {totalItems} 件　{page} / {totalPages} ページ
      </span>
      <div className="flex gap-2">
        {page > 1 ? (
          <Button nativeButton={false} render={<Link href={hrefFor(page - 1)} />} size="sm" variant="outline">
            前へ
          </Button>
        ) : (
          <Button disabled size="sm" variant="outline">
            前へ
          </Button>
        )}
        {page < totalPages ? (
          <Button nativeButton={false} render={<Link href={hrefFor(page + 1)} />} size="sm" variant="outline">
            次へ
          </Button>
        ) : (
          <Button disabled size="sm" variant="outline">
            次へ
          </Button>
        )}
      </div>
    </nav>
  )
}
