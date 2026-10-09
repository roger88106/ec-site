import type { Metadata } from 'next'
import { Geist } from 'next/font/google'
import { SiteHeader } from '@/components/SiteHeader'
import { cn } from '@/lib/utils'
import './globals.css'

const geist = Geist({ subsets: ['latin'], variable: '--font-sans' })

export const metadata: Metadata = {
  title: { default: 'EC Site', template: '%s | EC Site' },
  description: 'EC Site の店舗',
}

/** すべての画面の共通の枠（ヘッダーと本文） */
export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html className={cn('font-sans', geist.variable)} lang="ja">
      <body className="min-h-svh bg-background text-foreground">
        <SiteHeader />
        <main className="mx-auto w-full max-w-6xl px-4 py-8">{children}</main>
      </body>
    </html>
  )
}
