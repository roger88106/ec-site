import { cn } from 'cn'
import { ImageIcon } from 'lucide-react'

/** 代わりの図の大きさ（品目の小さな枠、一覧のカード、詳細の大きな枠） */
const ICON_SIZES = { sm: 'size-8', md: 'size-10', lg: 'size-16' } as const

/**
 * 商品の画像
 *
 * 画像がなければ、代わりの図を出す。枠（大きさ、背景、角の丸み）は描かない。
 */
export function ProductImage({
  src,
  alt,
  iconSize,
}: {
  /** 画像の URL。なければ代わりの図 */
  src: string | null | undefined
  /** 画像の代わりの文（飾りなら空文字） */
  alt: string
  /** 代わりの図の大きさ */
  iconSize: keyof typeof ICON_SIZES
}) {
  if (!src) {
    return <ImageIcon aria-hidden className={cn(ICON_SIZES[iconSize], 'text-muted-foreground')} />
  }
  // biome-ignore lint/performance/noImgElement: 画像は配信の側（CDN）で最適化する
  return <img alt={alt} className="size-full object-cover" src={src} />
}
