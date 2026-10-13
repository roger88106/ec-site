import { LOCALE } from '@ec-site/shared/lib/locale'
import type { Metadata } from 'next'
import { Geist } from 'next/font/google'
import { NextIntlClientProvider } from 'next-intl'
import { getTranslations } from 'next-intl/server'
import { SiteHeader } from '@/components/SiteHeader'
import { cn } from '@/lib/utils'
import './globals.css'

/** 本文の書体（CSS の変数 --font-sans で使う） */
const geist = Geist({ subsets: ['latin'], variable: '--font-sans' })

/** ブラウザのタブの題名（画面ごとの題名の後ろに店名を付ける）と、店舗の説明 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('app.layout')
  return {
    title: { default: 'EC Site', template: '%s | EC Site' },
    description: t('description'), // 店舗の説明
  }
}

/** すべての画面の共通の枠（ヘッダーと本文） */
export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html className={cn('font-sans', geist.variable)} lang={LOCALE}>
      <body className="min-h-svh bg-background text-foreground">
        {/* クライアントコンポーネントに文言を渡す（ロケールと文言は request.ts の設定を引き継ぐ） */}
        <NextIntlClientProvider>
          <SiteHeader />
          <main className="mx-auto w-full max-w-6xl px-4 py-8">{children}</main>
        </NextIntlClientProvider>
      </body>
    </html>
  )
}
