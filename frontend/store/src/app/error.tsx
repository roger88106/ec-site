'use client'

/** 取得の失敗など、予期しないエラーの画面（店舗画面の構成 第 6 節） */
export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="font-heading font-semibold text-2xl">表示できませんでした</h1>
      <p className="text-muted-foreground">時間をおいて、もう一度お試しください。</p>
      <button className="text-sm underline" onClick={reset} type="button">
        もう一度読み込む
      </button>
    </div>
  )
}
