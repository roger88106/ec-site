'use client'

import { useEffect, useState } from 'react'
import { remainingTime } from '@/lib/orderDisplay'

/**
 * 引当の残り時間（AC-84）。1 秒ごとに数え直す。<br />
 * 期限が切れても注文はできる（AC-87）ので、操作は止めずに案内だけを替える。
 * サーバーとブラウザの時刻の違いで表示が食い違わないよう、ブラウザで初めて数える。
 */
export function RemainingTime({ expiresAt }: { expiresAt: string }) {
  const [now, setNow] = useState<Date | null>(null)
  useEffect(() => {
    setNow(new Date())
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  if (now === null) {
    return null
  }
  const remaining = remainingTime(expiresAt, now)
  return (
    <p aria-live="off" className="rounded-lg bg-muted px-4 py-3 text-sm">
      {remaining === null ? (
        '在庫の確保の期限が切れました。このまま注文できますが、その時点で在庫が足りなければ注文できません。'
      ) : (
        <>
          ご注文の商品の在庫を確保しています。残り <span className="font-semibold tabular-nums">{remaining}</span>
        </>
      )}
    </p>
  )
}
