import type { ReactNode } from 'react'
import type { ActionResult } from '@/lib/actionResult'

/**
 * サーバーアクションの失敗の表示（店舗画面の構成 第 6 節）。<br />
 * 状態の知らせ（`status` の役割）の領域として、メッセージがなくても常に描く。後から描かれた領域は読み上げられない支援技術があるため、領域を先に画面に置き、失敗したときに文だけを入れる。<br />
 * メッセージのない領域は、中身のない要素で、レイアウトから外す（親の `space-y-*` や `gap-*` の余白を増やさない）。支援技術には残る。<br />
 * メッセージのあとに、操作へのリンクなどを添えられる。<br />
 * エラーメッセージは、サーバーアクションの結果（`result`）か、画面に出すメッセージ（`message`）で渡す。両方あれば `message` を出す。<br />
 * 親が常にある読み上げの領域を持ち、その中で出すときは `inLiveRegion` を付ける。領域を入れ子にしないために、自分では領域を描かず、文があるときだけ文を出す。
 *
 * @param result       サーバーアクションの結果（失敗のときだけ、その message を出す。成功、まだ結果がない場合は文を出さない）
 * @param message      画面に出すエラーメッセージ（なければ文を出さない）
 * @param inLiveRegion 親の領域の中で出すか（出すなら自分では領域を描かない）
 * @param children     メッセージのあとに添える要素（メッセージがなければ出さない）
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
  const body = text && (
    <>
      {text}
      {children}
    </>
  )
  if (inLiveRegion) {
    return text ? <p className="text-destructive text-sm">{body}</p> : null
  }
  return (
    <p className="text-destructive text-sm empty:absolute" role="status">
      {body}
    </p>
  )
}
