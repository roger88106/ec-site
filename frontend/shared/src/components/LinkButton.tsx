import type { ComponentProps, ReactElement } from 'react'
import { Button } from '../ui/button'

/**
 * リンクの見た目のボタン
 *
 * 共有はどちらのルーターにも依存しない。
 * hook を使わないので `"use client"` は付けない（AmountsSummary と同じ）。店舗画面のサーバーコンポーネントからも使える。
 */
export function LinkButton({
  link,
  ...props
}: Omit<ComponentProps<typeof Button>, 'nativeButton' | 'render'> & {
  /** 描く要素（`a`）。管理画面は React Router の Link、店舗画面は next/link の Link */
  link: ReactElement
}) {
  return <Button nativeButton={false} /* a を描くので、button の振る舞いを外す */ render={link} {...props} />
}
