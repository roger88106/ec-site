'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { remainingTime } from '@/lib/orderDisplay'

/**
 * 引当の残り時間。1 秒ごとに数え直す。<br />
 * 期限が切れても注文はできるので、操作は止めずに案内だけを替える。
 * サーバーとブラウザの時刻の違いで表示が食い違わないよう、ブラウザで初めて数える。
 */
export function RemainingTime({ expiresAt }: { expiresAt: string }) {
  const t = useTranslations('checkout.remaining')
  const [now, setNow] = useState<Date | null>(null)
  // 今の時刻はブラウザで決める（サーバーの描画と食い違わないよう、最初は出さない）。1 秒ごとに残りを数え直す
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
      {
        remaining === null
          ? t('expired') /* 期限切れ */
          : t.rich('reserving', {
              remaining,
              time: (chunks) => <span className="font-semibold tabular-nums">{chunks}</span>,
            }) /* 確保している残りの時間 */
      }
    </p>
  )
}
