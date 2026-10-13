'use client'

import { usePathname, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { loginPath } from '@/lib/login'

/**
 * ログインしていないときのヘッダーの「ログイン」「会員登録」。ログインの後は、いまの画面に戻る。<br />
 * ヘッダーは共通の枠（layout）にあり、画面を移っても作り直されないので、いまの画面はブラウザーで読む。
 * /login は IdP へ移る口（ルートハンドラー）なので、Link ではなく a で移る。
 */
export function LoginLinks() {
  const t = useTranslations('components.loginLinks')
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const query = searchParams.toString()
  const returnTo = query === '' ? pathname : `${pathname}?${query}`
  return (
    <>
      <a className="hover:underline" href={loginPath(returnTo)}>
        {t('login') /* ログイン */}
      </a>
      <a className="hover:underline" href={loginPath(returnTo, { register: true })}>
        {t('register') /* 会員登録 */}
      </a>
    </>
  )
}
