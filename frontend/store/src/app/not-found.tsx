import Link from 'next/link'

/** 見つからない（存在しない商品と、C 側に表示されない商品を区別しない。AC-65）。注文手続き・注文の URL の誤りも同じ */
export default function NotFound() {
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="font-heading font-semibold text-2xl">ページが見つかりません</h1>
      <p className="text-muted-foreground">
        お探しのページ（商品、ご注文など）は、販売が終わったか、URL が間違っている可能性があります。
      </p>
      <Link className="text-sm underline" href="/products">
        商品一覧へ
      </Link>
    </div>
  )
}
