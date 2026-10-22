import type { ReactNode } from 'react'

/**
 * サーバーアクションの失敗の表示（店舗画面の構成 第 6 節）。<br />
 * 失敗の文があるときだけ出し、読み上げの対象にする。文のあとに、操作へのリンクなどを添えられる。<br />
 * 親が常にある `aria-live` の領域を持ち、その中で出すときは `inLiveRegion` を付ける。領域を入れ子にしないためと、
 * 後から描かれた領域は読み上げられない支援技術があるため、失敗も常にある領域の中で出す。
 *
 * @param message      画面に出す失敗の文（なければ何も出さない）
 * @param inLiveRegion 親の領域の中で出すか（出すなら自分では `aria-live` を付けない）
 * @param children     文のあとに添える要素
 */
export function ActionError({
  message,
  inLiveRegion = false,
  children,
}: {
  message: string | null | undefined
  inLiveRegion?: boolean
  children?: ReactNode
}) {
  if (!message) {
    return null
  }
  return (
    <p aria-live={inLiveRegion ? undefined : 'polite'} className="text-destructive text-sm">
      {message}
      {children}
    </p>
  )
}
