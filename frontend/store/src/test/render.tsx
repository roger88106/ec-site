import { LOCALE, TIME_ZONE } from '@ec-site/shared/lib/locale'
import { within } from '@testing-library/dom'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { JSDOM } from 'jsdom'
import { NextIntlClientProvider } from 'next-intl'
import type { ReactElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { messages } from '@/i18n/messages'

/**
 * 部品を HTML の文字にする。画面と同じく、日本語の文言の提供者（next-intl）で包む。
 *
 * @param element 描画する部品
 * @returns HTML の文字
 */
export function renderMarkup(element: ReactElement): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale={LOCALE} messages={messages} timeZone={TIME_ZONE}>
      {element}
    </NextIntlClientProvider>,
  )
}

/**
 * 部品を jsdom の上に描画する（ブラウザーの中だけで終わる操作を確認する。店舗画面の構成 第 7 節）。<br />
 * テストのファイルの先頭で jsdom を選ぶ（`// @vitest-environment jsdom`）。
 *
 * @param element 描画する部品
 * @returns 操作に使う user（user-event）
 */
export function renderInBrowser(element: ReactElement) {
  render(
    <NextIntlClientProvider locale={LOCALE} messages={messages} timeZone={TIME_ZONE}>
      {element}
    </NextIntlClientProvider>,
  )
  return { user: userEvent.setup() }
}

/**
 * 部品を HTML にし、jsdom の文書に読み込んで、役割と名前などで探す関数を返す。
 * 効果（`useEffect`）は実行されない（サーバーが返す HTML と同じ）。<br />
 * テストの環境は Node のまま使う（ページとサーバーコンポーネントのテストが、Node の上の MSW と Next.js の置き換えを使うため）。
 * そのため、jsdom の文書は、ここで `JSDOM` で作る。
 *
 * @param element 描画する部品
 * @returns 文書の body を対象にした問い合わせの関数（Testing Library の `within` の戻り値）
 */
export function renderStatic(element: ReactElement) {
  const { document } = new JSDOM('<!doctype html><html><body></body></html>').window
  document.body.innerHTML = renderMarkup(element)
  return within(document.body)
}
