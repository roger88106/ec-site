'use client'

import { MenuIcon } from 'lucide-react'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type ReactNode, useEffect, useId, useRef, useState } from 'react'

/**
 * ヘッダーのメニュー。幅 1024px 未満では、ボタンで開閉する縦の並びにする。1024px 以上は、ボタンを出さず、項目をヘッダーの 1 行に並べる。<br />
 * 項目は 1 組だけ描画し、幅で並べ方と表示を切り替える（同じリンクが 2 つあると、支援技術で 2 回読み上げられるため）。
 * 1024px 以上では、入れ物と外側の div を `contents` にして、項目と開閉のボタンをヘッダーの並びの直接の要素にする。
 * 画面を移るとメニューを閉じ、Esc キーでも閉じてフォーカスをボタンに戻す。
 *
 * @param children メニューの中の項目。1024px 以上で並べる位置は、各項目の `lg:order-*` で決める
 */
export function HeaderMenu({ children }: { children: ReactNode }) {
  const t = useTranslations('components.headerMenu')
  const pathname = usePathname()
  const menuId = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [openedPathname, setOpenedPathname] = useState(pathname)

  // ヘッダーは画面を移っても再作成されないので、パスが変わったら描画の中で閉じる
  if (openedPathname !== pathname) {
    setOpenedPathname(pathname)
    setOpen(false)
  }

  // Esc キーは、メニューかボタンにフォーカスがあるときだけ受ける。静的な要素の onKeyDown は lint に当たるので、開いている間だけ document で受ける
  useEffect(() => {
    if (!open) return
    function closeOnEscape(event: KeyboardEvent) {
      const target = event.target
      const inside =
        target instanceof Node && (buttonRef.current?.contains(target) || menuRef.current?.contains(target))
      if (event.key === 'Escape' && inside) {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [open])

  return (
    <div className="contents">
      <button
        aria-controls={menuId}
        aria-expanded={open}
        className="order-3 ml-auto flex cursor-pointer items-center gap-1 py-2 text-sm whitespace-nowrap hover:underline lg:hidden"
        onClick={() => setOpen((current) => !current)}
        ref={buttonRef}
        type="button"
      >
        <MenuIcon aria-hidden className="size-4" />
        {t('open') /* メニュー */}
      </button>
      <div
        className={`order-5 w-full flex-col border-t pt-2 text-sm whitespace-nowrap lg:contents lg:border-0 lg:pt-0 [&_a]:py-2 lg:[&_a]:py-0 [&_button]:py-2 lg:[&_button]:py-0 ${open ? 'flex' : 'hidden'}`}
        id={menuId}
        ref={menuRef}
      >
        {children}
      </div>
    </div>
  )
}
