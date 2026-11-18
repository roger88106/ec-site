import type { Metadata } from 'next'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'

/** ブラウザーのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('app.loginFailed')
  return { title: t('title') } // ログインできませんでした
}

/**
 * ログインできなかったことを示す画面（店舗画面の構成の「ログイン」）。
 *
 * ログインの手続きが再試行でも失敗したときの移り先。ログインを求めない。
 */
export default async function LoginFailedPage() {
  const t = await getTranslations('app.loginFailed')
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="font-heading text-2xl font-semibold">{t('title') /* ログインできなかった */}</h1>
      <p className="text-muted-foreground">{t('message') /* 時間をおいて再度ログインする案内 */}</p>
      <div className="flex justify-center gap-6 text-sm">
        {/* /login は route handler で、先読みを避けるため <a> を使う */}
        <a className="underline" href="/login">
          {t('login') /* もう一度ログインする */}
        </a>
        <Link className="underline" href="/">
          {t('top') /* トップへ戻る */}
        </Link>
      </div>
    </div>
  )
}
