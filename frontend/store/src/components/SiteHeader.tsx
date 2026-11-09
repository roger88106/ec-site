import { Input } from '@ec-site/shared/ui/input'
import { SearchIcon, ShoppingCartIcon } from 'lucide-react'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'
import { Suspense } from 'react'
import { logout } from '@/actions/auth'
import { parameters } from '@/api/generated/constraints'
import { ACCOUNT_URL, currentMember } from '@/auth'
import { HeaderMenu } from '@/components/HeaderMenu'
import { LoginLinks } from '@/components/LoginLinks'

/**
 * 画面の上のヘッダー。サイト名、商品一覧へのリンク、キーワード検索（フォームの GET で /search へ）、カートへのリンク、
 * ログインの状態。<br />
 * ログインしていなければ「ログイン」「会員登録」、していれば氏名、「ご注文履歴」、「アカウント」（IdP のアカウントの画面）、「ログアウト」。<br />
 * 幅 1024px 以上は、全てを 1 行に並べる。1024px 未満は、1 行目に店名、カート、メニューのボタン、2 行目に検索欄を置き、
 * 商品一覧とログインの状態の項目は、ボタンで開くメニューに入れる。項目は 1 組だけ描画し、並び順は CSS の order で切り替える。
 */
export async function SiteHeader() {
  const tCommon = await getTranslations('common')
  const t = await getTranslations('components.siteHeader')
  const member = await currentMember()
  return (
    <header className="border-b">
      <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-x-6 gap-y-1 px-4 py-2 lg:h-14 lg:flex-nowrap lg:py-0">
        <Link className="font-heading order-1 text-lg font-semibold whitespace-nowrap" href="/">
          {tCommon('siteName') /* 店名 */}
        </Link>
        <search className="order-4 w-full lg:order-3 lg:ml-auto lg:w-auto">
          <form action="/search" className="flex items-center gap-2">
            <SearchIcon aria-hidden className="text-muted-foreground size-4" />
            <Input
              aria-label={t('keyword') /* キーワード */}
              className="lg:w-56"
              maxLength={parameters.searchProducts.q.maxLength}
              name="q"
              placeholder={t('keywordPlaceholder') /* 探すことを促す */}
              required
            />
          </form>
        </search>
        <Link
          className="order-2 flex items-center gap-1 text-sm whitespace-nowrap hover:underline lg:order-4"
          href="/cart"
        >
          <ShoppingCartIcon aria-hidden className="size-4" />
          {t('cart') /* カート */}
        </Link>
        <HeaderMenu>
          <nav className="text-sm lg:order-2">
            <Link className="hover:underline" href="/products">
              {t('products') /* 商品一覧 */}
            </Link>
          </nav>
          <div className="flex flex-col items-start lg:order-5 lg:flex-row lg:items-center lg:gap-4">
            {member === null ? (
              <Suspense>
                <LoginLinks />
              </Suspense>
            ) : (
              <>
                <span className="py-2 lg:py-0">{t('memberName', { name: member.name }) /* 会員の名前 */}</span>
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
        </HeaderMenu>
      </div>
    </header>
  )
}
