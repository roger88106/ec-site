import { ImageIcon } from 'lucide-react'

/** 代わりの図の大きさ（品目の小さな枠、一覧のカード、詳細の大きな枠） */
const ICON_SIZES = { sm: 'size-8', md: 'size-10', lg: 'size-16' } as const

/**
 * 商品の画像。画像がなければ、代わりの図を出す。<br />
 * 枠（大きさ、背景、角の丸み）は使う側が持つ。
 *
 * @param src      画像の URL。なければ代わりの図
 * @param alt      画像の代わりの文（飾りなら空文字）
 * @param iconSize 代わりの図の大きさ
 */
export function ProductImage({
  src,
  alt,
  iconSize,
}: {
  src: string | null | undefined
  alt: string
  iconSize: keyof typeof ICON_SIZES
}) {
  if (!src) {
    return <ImageIcon aria-hidden className={`${ICON_SIZES[iconSize]} text-muted-foreground`} />
  }
  // biome-ignore lint/performance/noImgElement: 画像は配信の側（CDN）で最適化する
  return <img alt={alt} className="size-full object-cover" src={src} />
}
