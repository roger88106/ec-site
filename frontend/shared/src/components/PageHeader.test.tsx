// 照合の関数（toBeInTheDocument など）の型を得る。製品のコードの型の範囲を変えないよう、tsconfig でなくここで読み込む
import '@testing-library/jest-dom/vitest'
import { within } from '@testing-library/dom'
import { JSDOM } from 'jsdom'
import type { ReactElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, test } from 'vitest'
import { PageHeader } from './PageHeader'

/** 部品を HTML にし、jsdom の文書の body に読み込んで、探す関数を返す */
function render(element: ReactElement) {
  const { document: jsdomDocument } = new JSDOM('<!doctype html><html><body></body></html>').window
  jsdomDocument.body.innerHTML = renderToStaticMarkup(element)
  return within(jsdomDocument.body)
}

describe('画面の見出し', () => {
  test('見出しを h1 で出す', () => {
    const header = render(<PageHeader title="カート" />)

    expect(header.getByRole('heading', { level: 1, name: 'カート' })).toBeInTheDocument()
  })

  test('操作を渡すと、見出しと一緒に出す', () => {
    const header = render(
      <PageHeader title="商品">
        <button type="button">登録</button>
      </PageHeader>,
    )

    expect(header.getByRole('heading', { level: 1, name: '商品' })).toBeInTheDocument()
    expect(header.getByRole('button', { name: '登録' })).toBeInTheDocument()
  })
})
