import { Input } from '@ec-site/shared/ui/input'
import { SearchIcon, ShoppingCartIcon } from 'lucide-react'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'
import { Suspense } from 'react'
import { logout } from '@/actions/auth'
import { parameters } from '@/api/generated/constraints'
import { ACCOUNT_URL, currentMember } from '@/auth'
import { LoginLinks } from '@/components/LoginLinks'

/**
 * 画面の上のヘッダー。サイト名、商品一覧へのリンク、キーワード検索（フォームの GET で /search へ）、カートへのリンク、
 * ログインの状態。<br />
 * ログインしていなければ「ログイン」「会員登録」、していれば氏名、「ご注文履歴」、「アカウント」（IdP のアカウントの画面）、「ログアウト」。
 */
export async function SiteHeader() {
  const tCommon = await getTranslations('common')
  const t = await getTranslations('components.siteHeader')
  const member = await currentMember()
  return (
    <header className="border-b">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-6 px-4">
        <Link className="font-heading font-semibold text-lg" href="/">
          {tCommon('siteName') /* 店名 */}
        </Link>
        <nav className="text-sm">
          <Link className="hover:underline" href="/products">
            {t('products') /* 商品一覧 */}
          </Link>
        </nav>
        <search className="ml-auto">
          <form action="/search" className="flex items-center gap-2">
            <SearchIcon aria-hidden className="size-4 text-muted-foreground" />
            <Input
              aria-label={t('keyword') /* キーワード */}
              className="w-56"
              maxLength={parameters.searchProducts.q.maxLength}
              name="q"
              placeholder={t('keywordPlaceholder') /* 探すことを促す */}
              required
            />
          </form>
        </search>
        <Link className="flex items-center gap-1 text-sm hover:underline" href="/cart">
          <ShoppingCartIcon aria-hidden className="size-4" />
          {t('cart') /* カート */}
        </Link>
        <div className="flex items-center gap-4 text-sm">
          {member === null ? (
            <Suspense>
              <LoginLinks />
            </Suspense>
          ) : (
            <>
              <span>{t('memberName', { name: member.name }) /* 会員の名前 */}</span>
              <Link className="hover:underline" href="/orders">
                {t('orders') /* ご注文履歴 */}
              </Link>
              <a className="hover:underline" href={ACCOUNT_URL}>
                {t('account') /* アカウント */}
              </a>
              <form action={logout}>
                <button className="cursor-pointer hover:underline" type="submit">
                  {tCommon('logout') /* ログアウト */}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
