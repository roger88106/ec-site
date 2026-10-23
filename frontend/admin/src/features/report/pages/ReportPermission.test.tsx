import { screen, waitFor } from '@testing-library/react'
import { delay } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderApp } from '@/test/renderApp'
import { serveReportBasics } from '@/test/reportServer'
import { http, server } from '@/test/server'

/** 閲覧者の権限（参照だけ） */
const VIEWER = ['tax:read', 'category:read', 'product:read', 'inventory:read', 'order:read']

/** 営業の権限（参照と、売上の推移を除くレポート） */
const SALES = [...VIEWER, 'report-variant-sales:read', 'report-customer:read']

/** 自分の権限の一覧の取得の失敗（500 の応答と、通信の失敗）と、画面に出るエラーの文 */
const ME_FAILURES = [
  ['500 の応答', 'serverError', 'システムエラーが発生しました。'],
  ['通信の失敗', 'networkError', 'サーバーに接続できませんでした。'],
] as const

describe('レポートの権限による出し分け', () => {
  it('AC-214 営業は、メニューの「レポート」に、権限のある画面だけを見る', async () => {
    renderApp('/reports/sales-trend', { permissions: SALES })

    const reports = await screen.findByText('レポート')
    expect(reports).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'バリアントの売れ行き' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '地域・年齢層・性別' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '売上の推移' })).not.toBeInTheDocument()
  })

  it('AC-214 権限のない画面を URL で開くと「権限がない」を示す', async () => {
    renderApp('/reports/sales-trend', { permissions: SALES })

    expect(await screen.findByText('この画面を見る権限がありません。')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '売上の推移' })).not.toBeInTheDocument()
  })

  it('AC-214 レポートの権限がなければ、メニューに「レポート」を出さない', async () => {
    renderApp('/reports/customer-breakdown', { permissions: VIEWER })

    expect(await screen.findByText('この画面を見る権限がありません。')).toBeInTheDocument()
    expect(screen.queryByText('レポート')).not.toBeInTheDocument()
  })

  it('AC-214 権限があれば、メニューから画面を開ける', async () => {
    serveReportBasics()
    server.use(
      http.get('/reports/customer-breakdown', ({ response }) =>
        response(200).json({ prefectures: [], ageGroups: [], genders: [], shippingExcluded: false }),
      ),
    )
    const { user, router } = renderApp('/reports/sales-trend', { permissions: SALES })

    await user.click(await screen.findByRole('link', { name: '地域・年齢層・性別' }))

    expect(router.state.location.pathname).toBe('/reports/customer-breakdown')
    expect(await screen.findByRole('heading', { name: '地域・年齢層・性別' })).toBeInTheDocument()
  })

  it.each(ME_FAILURES)(
    '自分の権限の一覧の取得に失敗すると、レポートの画面は取得の失敗を示し、再試行して開ける（%s）',
    async (_name, me, message) => {
      serveReportBasics()
      server.use(
        http.get('/reports/customer-breakdown', ({ response }) =>
          response(200).json({ prefectures: [], ageGroups: [], genders: [], shippingExcluded: false }),
        ),
      )
      const { user } = renderApp('/reports/customer-breakdown', { me })

      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(screen.queryByText('この画面を見る権限がありません。')).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: '地域・年齢層・性別' })).not.toBeInTheDocument()

      server.use(http.get('/me', ({ response }) => response(200).json({ permissions: SALES })))
      await user.click(screen.getByRole('button', { name: '再試行' }))

      expect(await screen.findByRole('heading', { name: '地域・年齢層・性別' })).toBeInTheDocument()
      expect(screen.queryByText(message)).not.toBeInTheDocument()
    },
  )

  it('自分の権限の一覧の取得中は、レポートの画面は何も表示せず、メニューにレポートを表示しない', async () => {
    renderApp('/reports/customer-breakdown', { me: 'pending' })

    expect(await screen.findByRole('navigation')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '地域・年齢層・性別' })).not.toBeInTheDocument()
    expect(screen.queryByText('この画面を見る権限がありません。')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument()
    expect(screen.queryByText('レポート')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '地域・年齢層・性別' })).not.toBeInTheDocument()
  })

  it('再試行の間（取得し直している間）は、「再試行」のボタンを押せない', async () => {
    const { user } = renderApp('/reports/customer-breakdown', { me: 'serverError' })
    const retry = await screen.findByRole('button', { name: '再試行' })
    expect(retry).toBeEnabled()

    server.use(
      http.get('/me', async ({ response }) => {
        await delay('infinite')
        return response(200).json({ permissions: SALES })
      }),
    )
    await user.click(retry)

    await waitFor(() => expect(screen.getByRole('button', { name: '再試行' })).toBeDisabled())
  })

  it.each(ME_FAILURES)(
    '再試行の間に別のレポートの画面へ移っても、取得の失敗を示し、「再試行」のボタンを押せない（%s）',
    async (_name, me, message) => {
      const { user, router } = renderApp('/reports/customer-breakdown', { me })
      expect(await screen.findByText(message)).toBeInTheDocument()

      server.use(
        http.get('/me', async ({ response }) => {
          await delay('infinite')
          return response(200).json({ permissions: SALES })
        }),
      )
      await user.click(screen.getByRole('button', { name: '再試行' }))
      await waitFor(() => expect(screen.getByRole('button', { name: '再試行' })).toBeDisabled())

      // 権限を確かめない画面（税区分）を経て別のレポートを開く。画面の部品が新しく作られる
      server.use(http.get('/tax-categories', ({ response }) => response(200).json({ items: [] })))
      await user.click(screen.getByRole('link', { name: '税区分' }))
      await waitFor(() => expect(router.state.location.pathname).toBe('/tax-categories'))
      expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('link', { name: 'バリアントの売れ行き' }))

      expect(router.state.location.pathname).toBe('/reports/variant-sales')
      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '再試行' })).toBeDisabled()
    },
  )

  it.each(ME_FAILURES)(
    '自分の権限の一覧の取得に失敗すると、メニューにレポートの入口を全て表示する（%s）',
    async (_name, me, message) => {
      renderApp('/reports/customer-breakdown', { me })

      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(screen.getByText('レポート')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '売上の推移' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'バリアントの売れ行き' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '地域・年齢層・性別' })).toBeInTheDocument()
    },
  )
})
