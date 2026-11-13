import type { ReactNode } from 'react'

/**
 * 画面の見出し
 *
 * 管理画面と店舗画面のページの h1 に使う。
 */
export function PageHeader({
  title,
  children,
}: {
  title: ReactNode
  /** 見出しの右側に置く操作（ボタンなど） */
  children?: ReactNode
}) {
  return (
    <div className="mb-6 flex items-center justify-between gap-4">
      <h1 className="font-heading text-2xl font-semibold">{title}</h1>
      {children && <div className="flex items-center gap-2">{children}</div>}
    </div>
  )
}
