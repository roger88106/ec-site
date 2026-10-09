import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter } from 'react-router'
import { App } from '@/app/App'
import { createQueryClient } from '@/app/queryClient'
import { routes } from '@/app/router'

/**
 * 管理画面を、指定した URL を開いた状態で描画する（管理画面の構成 第 10 節）。<br />
 * ルーター・サーバー状態のキャッシュ・toast は本物と同じものを使い、テストごとに新しく作る。
 * API の応答は、先に {@code server.use(...)} で与えておく。
 *
 * @param url 開く画面の URL（例：/tax-categories）
 * @returns 操作に使う user（user-event）と、URL を確かめるための router
 */
export function renderApp(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  const user = userEvent.setup()
  render(<App queryClient={createQueryClient()} router={router} />)
  return { user, router }
}
