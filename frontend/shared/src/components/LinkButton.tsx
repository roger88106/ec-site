import type { ComponentProps, ReactElement } from 'react'
import { Button } from '../ui/button'

/**
 * リンクの見た目のボタン。<br />
 * 描く要素（`a`）は呼ぶ側が `link` で渡す（管理画面は React Router の Link、店舗画面は next/link の Link）。
 * 共有はどちらのルーターにも依存しない。<br />
 * hook を使わないので `"use client"` は付けない（AmountsSummary と同じ）。店舗画面のサーバーコンポーネントからも使える。
 */
export function LinkButton({
  link,
  ...props
}: Omit<ComponentProps<typeof Button>, 'nativeButton' | 'render'> & { link: ReactElement }) {
  return <Button nativeButton={false} /* a を描くので、button の振る舞いを外す */ render={link} {...props} />
}
