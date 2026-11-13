'use client'

import { cn } from 'cn'
import { MenuIcon } from 'lucide-react'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type ReactNode, useEffect, useId, useRef, useState } from 'react'

/**
 * ヘッダーのメニュー
 *
 * 幅 1024px 未満では、ボタンで開閉する縦の並びにする。1024px 以上は、ボタンを出さず、項目をヘッダーの 1 行に並べる。
 *
 * 項目は 1 組だけ描画し、幅で並べ方と表示を切り替える（同じリンクが 2 つあると、支援技術で 2 回読み上げられるため）。
 * 1024px 以上では、メニューの div と外側の div を `contents` にして、項目と開閉のボタンをヘッダーの並びの直接の要素にする。
 * 画面を遷移するとメニューを閉じ（メニューの中のリンクのクリックと、パスの変化の両方で）、Esc キーでも閉じてフォーカスをボタンに戻す。
 */
export function HeaderMenu({
  children,
}: {
  /** メニューの中の項目。1024px 以上で並べる位置は、各項目の `lg:order-*` で決める */
  children: ReactNode
}) {
  const t = useTranslations('components.headerMenu')
  const pathname = usePathname()
  const menuId = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [renderedPathname, setRenderedPathname] = useState(pathname)

  // ヘッダーは画面を遷移しても再作成されないので、パスが変わったら描画の中で閉じる
  if (renderedPathname !== pathname) {
    setRenderedPathname(pathname)
    setOpen(false)
  }

  // 静的な要素の onKeyDown・onClick は lint の警告になるので、開いている間だけ document で受ける。
  // Esc キーは、フォーカスの位置によらず閉じる。フォーカスをボタンに戻すのは、フォーカスがメニューの中・ボタン・body にあったときだけ（検索欄などから奪わない）。
  // 日本語の変換の取り消しの Esc と、他の処理が扱った Esc では閉じない。
  // メニューの中のリンクのクリックは、パスが同じ遷移でも閉じる（パスの変化で閉じる処理は戻る・進むのため）。
  // 修飾キーと中ボタンのクリックは別のタブで開き、今の画面は遷移しないので閉じない
  useEffect(() => {
    if (!open) {
      return
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape' || event.isComposing || event.defaultPrevented) {
        return
      }
      const active = document.activeElement
      const focusIsHere =
        active === null ||
        active === document.body ||
        buttonRef.current?.contains(active) === true ||
        menuRef.current?.contains(active) === true
      setOpen(false)
      if (focusIsHere) {
        buttonRef.current?.focus()
      }
    }
    function closeOnLinkClick(event: MouseEvent) {
      const target = event.target
      const opensElsewhere = event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey
      if (
        !opensElsewhere &&
        target instanceof Element &&
        menuRef.current?.contains(target) &&
        target.closest('a') !== null
      ) {
        setOpen(false)
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    document.addEventListener('click', closeOnLinkClick)
    return () => {
      document.removeEventListener('keydown', closeOnEscape)
      document.removeEventListener('click', closeOnLinkClick)
    }
  }, [open])

  return (
    <div className="contents">
      <button
        aria-controls={menuId}
        aria-expanded={open}
        className="ml-auto flex cursor-pointer items-center gap-1 py-2 text-sm whitespace-nowrap hover:underline lg:hidden"
        onClick={() => setOpen((current) => !current)}
        ref={buttonRef}
        type="button"
      >
        <MenuIcon aria-hidden className="size-4" />
        {t('open') /* メニュー */}
      </button>
      <div
        className={cn(
          'w-full flex-col border-t pt-2 text-sm whitespace-nowrap lg:contents lg:border-0 lg:pt-0 [&_a]:py-2 lg:[&_a]:py-0 [&_button]:py-2 lg:[&_button]:py-0',
          open ? 'flex' : 'hidden',
        )}
        id={menuId}
        ref={menuRef}
      >
        {children}
      </div>
    </div>
  )
}
