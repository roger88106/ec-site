import { QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { $api } from '@/api/client'
import { useApiMutation } from '@/api/useApiMutation'
import { createQueryClient } from '@/app/queryClient'
import { http, problem, server } from '@/test/server'

/** テストごとに新しいキャッシュで hook を描画する */
function renderWithClient<T>(hook: () => T) {
  const queryClient = createQueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return renderHook(hook, { wrapper })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('更新の操作', () => {
  test('成功したら知らせを出し、続きの処理の後に、指定した一覧を取り直す', async () => {
    const success = vi.spyOn(toast, 'success')
    const order: string[] = []
    server.use(
      http.post('/tax-categories', ({ response }) => {
        order.push('post')
        return response(201).json({ id: 'tc-1' })
      }),
      http.get('/tax-categories', ({ response }) => {
        order.push('get')
        return response(200).json({ items: [] })
      }),
    )
    const { result } = renderWithClient(() => ({
      list: $api.useQuery('get', '/tax-categories'),
      create: useApiMutation('post', '/tax-categories', {
        success: '税区分を登録しました',
        invalidate: ['/tax-categories'],
        onSuccess: () => {
          order.push('onSuccess')
        },
      }),
    }))
    await waitFor(() => expect(result.current.list.isSuccess).toBe(true))
    order.length = 0

    await act(() => result.current.create.mutateAsync({ body: { name: '標準' } }))

    expect(success).toHaveBeenCalledWith('税区分を登録しました')
    expect(order).toEqual(['post', 'onSuccess', 'get'])
  })

  test('知らせの文は、応答と送った値から作れる', async () => {
    const success = vi.spyOn(toast, 'success')
    server.use(
      http.post('/inventory/variants/{variantId}/adjustments', ({ response }) =>
        response(201).json({
          id: 'a-1',
          delta: 3,
          reason: 'ARRIVAL',
          quantityAfter: 8,
          adjustedAt: '2026-10-13T00:00:00Z',
        }),
      ),
    )
    const { result } = renderWithClient(() =>
      useApiMutation('post', '/inventory/variants/{variantId}/adjustments', {
        success: (adjustment) => `在庫数は ${adjustment.quantityAfter} になりました`,
      }),
    )

    await act(() =>
      result.current.mutateAsync({
        params: { path: { variantId: 'v-1' } },
        body: { delta: 3, reason: 'ARRIVAL' },
      }),
    )

    expect(success).toHaveBeenCalledWith('在庫数は 8 になりました')
  })

  test('失敗したら知らせを出さず、エラーを渡す', async () => {
    const success = vi.spyOn(toast, 'success')
    const onError = vi.fn()
    server.use(
      http.post('/tax-categories', ({ response }) =>
        response(409).json(problem(409, 'TAX_CATEGORY_NAME_ALREADY_EXISTS', '同じ名称の税区分があります。')),
      ),
    )
    const { result } = renderWithClient(() =>
      useApiMutation('post', '/tax-categories', { success: '税区分を登録しました', onError }),
    )

    act(() => result.current.mutate({ body: { name: '標準' } }))

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(success).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(result.current.error)
  })
})
