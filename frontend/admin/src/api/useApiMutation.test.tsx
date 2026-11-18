import { QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http as mswHttp } from 'msw'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { describe, expect, test, vi } from 'vitest'
import { $api } from '@/api/client'
import type { ErrorCode } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { createQueryClient } from '@/app/queryClient'

import { http, problem, server } from '@/test/server'

/** テストごとに新しいキャッシュで hook を描画する。 */
function renderWithClient<T>(hook: () => T) {
  const queryClient = createQueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return renderHook(hook, { wrapper })
}

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

  describe('失敗の後の再取得', () => {
    /** 取得の回数を数えながら、カテゴリ名の変更を、指定の応答（通信の失敗を含む）で失敗させる。 */
    async function failWithResponse(respond: () => Response) {
      let gets = 0
      server.use(
        // 契約にない状態（500）や通信の失敗も試すので、型付きの http でなく、型のない msw のハンドラーで作る
        mswHttp.put(`${window.location.origin}/api/admin/categories/c-1`, respond),
        http.get('/categories', ({ response }) => {
          gets += 1
          return response(200).json({ items: [] })
        }),
      )
      const { result } = renderWithClient(() => ({
        list: $api.useQuery('get', '/categories'),
        rename: useApiMutation('put', '/categories/{categoryId}', { invalidate: ['/categories'] }),
      }))
      await waitFor(() => expect(result.current.list.isSuccess).toBe(true))
      const before = gets
      act(() =>
        result.current.rename.mutate({ params: { path: { categoryId: 'c-1' } }, body: { name: '服', version: 0 } }),
      )
      await waitFor(() => expect(result.current.rename.isError).toBe(true))
      return { result, extraGets: () => gets - before }
    }

    /** 指定の HTTP ステータスのエラーの応答で失敗させる。 */
    function failWith(status: number, errorCode: ErrorCode) {
      return failWithResponse(() => HttpResponse.json(problem(status, errorCode, '失敗しました。'), { status }))
    }

    test.each([
      [409, 'CONCURRENT_MODIFICATION'],
      [409, 'CATEGORY_NAME_ALREADY_EXISTS'],
      [404, 'NOT_FOUND'],
    ] as const)('AC-559-9 %i（%s）で失敗したら、指定した一覧を再取得し、エラーは残す', async (status, errorCode) => {
      const { result, extraGets } = await failWith(status, errorCode)

      await waitFor(() => expect(extraGets()).toBe(1))
      expect(result.current.rename.error).not.toBeNull()
    })

    test.each([
      [400, 'INVALID_REQUEST'],
      [500, 'SYSTEM_ERROR'],
    ] as const)('AC-559-9 %i（%s）で失敗しても、一覧は再取得しない', async (status, errorCode) => {
      const { result, extraGets } = await failWith(status, errorCode)

      // 失敗の処理で再取得が始まるなら、この時点で取得中になっている
      expect(result.current.list.isFetching).toBe(false)
      expect(extraGets()).toBe(0)
      expect(result.current.rename.error).not.toBeNull()
    })

    test('AC-559-9 通信の失敗になっても、一覧は再取得しない', async () => {
      const { result, extraGets } = await failWithResponse(() => HttpResponse.error())

      expect(result.current.list.isFetching).toBe(false)
      expect(extraGets()).toBe(0)
      expect(result.current.rename.error).not.toBeNull()
    })
  })
})
