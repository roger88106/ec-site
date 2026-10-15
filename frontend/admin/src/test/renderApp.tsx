import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { App } from '@/app/App'
import { createQueryClient } from '@/app/queryClient'
import { routes } from '@/app/router'
import { type Operator, OperatorProvider } from '@/auth/operator'
import { I18nProvider } from '@/i18n/I18nProvider'
import { http, server } from '@/test/server'

/** 運営者の realm の職務「管理者」と同じ、すべての権限 */
export const ALL_PERMISSIONS = [
  'tax:read',
  'tax:write',
  'category:read',
  'category:write',
  'product:read',
  'product:write',
  'inventory:read',
  'inventory:write',
  'order:read',
  'order:write',
  'report-sales-trend:read',
  'report-variant-sales:read',
  'report-customer:read',
] as const

/** 結合テストでログインしている運営者（IdP を使わずに差し替える。管理画面の構成 第 10 節） */
export const TEST_OPERATOR_NAME = '管理 花子'

/**
 * 管理画面を、指定した URL を開いた状態で描画する（管理画面の構成 第 9 節）。<br />
 * ルーター・サーバー状態のキャッシュ・toast・日本語の文言は本物と同じものを使い、テストごとに新しく作る。
 * API の応答は、先に {@code server.use(...)} で与えておく。
 * ログインは置き換え、運営者（{@link TEST_OPERATOR_NAME}）がログインしている状態にする。token は付けない。
 * 運営者の権限（自分の権限の一覧の API の応答）は、既定ですべての権限にする。
 *
 * @param url 開く画面の URL（例：/tax-categories）
 * @param options.permissions 運営者の権限（既定は {@link ALL_PERMISSIONS}）
 * @returns 操作に使う user（user-event）、URL を確かめるための router、ログアウトの操作（呼ばれたかを確かめる）
 */
export function renderApp(url: string, { permissions = ALL_PERMISSIONS }: { permissions?: readonly string[] } = {}) {
  server.use(http.get('/me', ({ response }) => response(200).json({ permissions: [...permissions] })))
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  const user = userEvent.setup()
  const logout = vi.fn()
  const operator: Operator = { name: TEST_OPERATOR_NAME, logout }
  render(
    <I18nProvider>
      <OperatorProvider value={operator}>
        <App queryClient={createQueryClient()} router={router} />
      </OperatorProvider>
    </I18nProvider>,
  )
  return { user, router, logout }
}
