import type { Route } from 'next'
import { describe, expect, test } from 'vitest'
import { PageLinks } from '@/components/PageLinks'
import { renderStatic } from '@/test/render'

/** ページの URL（typedRoutes の Route は、実行時に作った文字を型で受け付けないため、型を合わせる） */
const hrefFor = (page: number) => `/orders?page=${page}` as Route

describe('ページ送り', () => {
  test('中のページでは、件数とページと、前後のページへのリンクを出す', () => {
    const view = renderStatic(<PageLinks hrefFor={hrefFor} page={2} totalItems={45} totalPages={3} />)

    const nav = view.getByRole('navigation', { name: 'ページ' })
    expect(nav).toHaveTextContent('全 45 件 2 / 3 ページ')
    // 次のページへ移るリンクの見た目のボタンは、読み上げの役割が button になる
    expect(view.getByRole('button', { name: '前へ' })).toHaveAttribute('href', '/orders?page=1')
    expect(view.getByRole('button', { name: '次へ' })).toHaveAttribute('href', '/orders?page=3')
  })

  test('最初のページの前へ、最後のページの次へは、押せないボタンにする', () => {
    const first = renderStatic(<PageLinks hrefFor={hrefFor} page={1} totalItems={45} totalPages={3} />)

    expect(first.getByRole('button', { name: '前へ' })).toBeDisabled()
    expect(first.getByRole('button', { name: '前へ' })).not.toHaveAttribute('href')
    expect(first.getByRole('button', { name: '次へ' })).toHaveAttribute('href', '/orders?page=2')

    // 同じ文書を使い回すので、前の描画の確認を終えてから、次を描画する
    const last = renderStatic(<PageLinks hrefFor={hrefFor} page={3} totalItems={45} totalPages={3} />)
    expect(last.getByRole('button', { name: '次へ' })).toBeDisabled()
    expect(last.getByRole('button', { name: '次へ' })).not.toHaveAttribute('href')
    expect(last.getByRole('button', { name: '前へ' })).toHaveAttribute('href', '/orders?page=2')
  })

  test('1 ページに収まれば、件数だけを出す', () => {
    const view = renderStatic(<PageLinks hrefFor={hrefFor} page={1} totalItems={5} totalPages={1} />)

    expect(view.getByText('全 5 件')).toBeInTheDocument()
    expect(view.queryByRole('navigation')).toBeNull()
  })
})
