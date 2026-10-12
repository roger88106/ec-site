import { Input } from '@ec-site/shared/ui/input'
import { SearchIcon, ShoppingCartIcon } from 'lucide-react'
import Link from 'next/link'
import { Suspense } from 'react'
import { logout } from '@/actions/auth'
import { ACCOUNT_URL, currentMember } from '@/auth'
import { LoginLinks } from '@/components/LoginLinks'

/**
 * 画面の上のヘッダー。サイト名、商品一覧へのリンク、キーワード検索（フォームの GET で /search へ）、カートへのリンク、
 * ログインの状態（AC-108、113、114、119）。<br />
 * ログインしていなければ「ログイン」「会員登録」、していれば氏名、「ご注文履歴」（AC-129）、「アカウント」（IdP のアカウントの画面）、「ログアウト」。
 */
export async function SiteHeader() {
  const member = await currentMember()
  return (
    <header className="border-b">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-6 px-4">
        <Link className="font-heading font-semibold text-lg" href="/">
          EC Site
        </Link>
        <nav className="text-sm">
          <Link className="hover:underline" href="/products">
            商品一覧
          </Link>
        </nav>
        <search className="ml-auto">
          <form action="/search" className="flex items-center gap-2">
            <SearchIcon aria-hidden className="size-4 text-muted-foreground" />
            <Input
              aria-label="キーワード"
              className="w-56"
              maxLength={100}
              name="q"
              placeholder="キーワードで探す"
              required
            />
          </form>
        </search>
        <Link className="flex items-center gap-1 text-sm hover:underline" href="/cart">
          <ShoppingCartIcon aria-hidden className="size-4" />
          カート
        </Link>
        <div className="flex items-center gap-4 text-sm">
          {member === null ? (
            <Suspense>
              <LoginLinks />
            </Suspense>
          ) : (
            <>
              <span>{member.name} 様</span>
              <Link className="hover:underline" href="/orders">
                ご注文履歴
              </Link>
              <a className="hover:underline" href={ACCOUNT_URL}>
                アカウント
              </a>
              <form action={logout}>
                <button className="cursor-pointer hover:underline" type="submit">
                  ログアウト
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
