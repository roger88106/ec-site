'use client'

import { useTranslations } from 'next-intl'
/** 取得の失敗など、予期しないエラーの画面（店舗画面の構成 第 6 節） */
export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  const t = useTranslations('app.error')
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="font-heading font-semibold text-2xl">{t('title') /* 表示できない */}</h1>
      <p className="text-muted-foreground">{t('message') /* もう一度の案内 */}</p>
      <button className="text-sm underline" onClick={reset} type="button">
        {t('retry') /* もう一度読み込む */}
      </button>
    </div>
  )
}
