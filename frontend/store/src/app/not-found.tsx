import Link from 'next/link'

/** 見つからない（存在しない商品と、C 側に表示されない商品を区別しない。AC-65） */
export default function NotFound() {
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="font-heading font-semibold text-2xl">ページが見つかりません</h1>
      <p className="text-muted-foreground">お探しの商品は、販売が終わったか、URL が間違っている可能性があります。</p>
      <Link className="text-sm underline" href="/products">
        商品一覧へ
      </Link>
    </div>
  )
}
