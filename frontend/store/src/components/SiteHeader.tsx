import { SearchIcon, ShoppingCartIcon } from 'lucide-react'
import Link from 'next/link'
import { Input } from '@/components/ui/input'

/** 画面の上のヘッダー。サイト名、商品一覧へのリンク、キーワード検索（フォームの GET で /search へ）、カートへのリンク */
export function SiteHeader() {
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
      </div>
    </header>
  )
}
