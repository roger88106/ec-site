import type { ReactNode } from 'react'

/**
 * サーバーアクションの失敗の表示（店舗画面の構成 第 6 節）。<br />
 * 失敗の文があるときだけ出し、読み上げの対象にする。文のあとに、操作へのリンクなどを添えられる。
 *
 * @param message  画面に出す失敗の文（なければ何も出さない）
 * @param children 文のあとに添える要素
 */
export function ActionError({ message, children }: { message: string | null | undefined; children?: ReactNode }) {
  if (!message) {
    return null
  }
  return (
    <p aria-live="polite" className="text-destructive text-sm">
      {message}
      {children}
    </p>
  )
}
