import type { ReactNode } from 'react'
import type { ActionResult } from '@/lib/actionResult'

/**
 * サーバーアクションの失敗の表示（店舗画面の構成 第 6 節）。<br />
 * 失敗の文があるときだけ出し、読み上げの対象にする。文のあとに、操作へのリンクなどを添えられる。<br />
 * 失敗の文は、サーバーアクションの結果（`result`）か、画面に出す文（`message`）で渡す。両方あれば `message` を出す。<br />
 * 親が常にある `aria-live` の領域を持ち、その中で出すときは `inLiveRegion` を付ける。領域を入れ子にしないために、自分では `aria-live` を付けない。<br />
 * 後から描かれた領域は読み上げられない支援技術があるため、失敗も常にある領域の中で出す。
 *
 * @param result       サーバーアクションの結果（失敗のときだけ、その message を出す。成功、まだ結果がない場合は何も出さない）
 * @param message      画面に出す失敗の文（なければ何も出さない）
 * @param inLiveRegion 親の領域の中で出すか（出すなら自分では `aria-live` を付けない）
 * @param children     文のあとに添える要素
 */
export function ActionError({
  result,
  message,
  inLiveRegion = false,
  children,
}: {
  result?: ActionResult | null
  message?: string | null
  inLiveRegion?: boolean
  children?: ReactNode
}) {
  const text = message || (result?.ok === false ? result.message : null)
  if (!text) {
    return null
  }
  return (
    <p aria-live={inLiveRegion ? undefined : 'polite'} className="text-destructive text-sm">
      {text}
      {children}
    </p>
  )
}
