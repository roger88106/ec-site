import type { ReactNode } from 'react'

/** 画面の見出し。右側に操作（ボタンなど）を置ける */
export function PageHeader({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div className="mb-6 flex items-center justify-between gap-4">
      <h1 className="font-heading font-semibold text-2xl">{title}</h1>
      {children && <div className="flex items-center gap-2">{children}</div>}
    </div>
  )
}
