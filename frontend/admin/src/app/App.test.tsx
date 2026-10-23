import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { TAX_CATEGORY_ID } from '@/test/fixtures'
import { renderApp, TEST_OPERATOR_NAME } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

describe('結合テストの土台', () => {
  test('API の応答を画面に出し、リンクで移った画面に API のエラーのメッセージを出す', async () => {
    server.use(
      http.get('/tax-categories', ({ response }) =>
        response(200).json({
          items: [{ id: TAX_CATEGORY_ID, name: '標準税率', currentRatePercent: 10, rates: [], version: 0 }],
        }),
      ),
      http.get('/tax-categories/{taxCategoryId}', ({ response }) =>
        response(404).json(problem(404, 'NOT_FOUND', '税区分が見つかりません。')),
      ),
    )
    const { user, router } = renderApp('/tax-categories')

    await user.click(await screen.findByRole('button', { name: '税率を編集' }))

    expect(router.state.location.pathname).toBe(`/tax-categories/${TAX_CATEGORY_ID}`)
    expect(await screen.findByText('税区分が見つかりません。')).toBeInTheDocument()
  })
})

describe('ログインしている運営者', () => {
  test('AC-135 ヘッダーに運営者の氏名を出し、ログアウトを押すとログアウトする', async () => {
    server.use(http.get('/tax-categories', ({ response }) => response(200).json({ items: [] })))
    const { user, logout } = renderApp('/tax-categories')

    expect(await screen.findByText(TEST_OPERATOR_NAME)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(logout).toHaveBeenCalledOnce()
  })
})
