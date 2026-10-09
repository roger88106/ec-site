import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { ApiError } from '@/api/errors'
import { router } from '@/app/router'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'

/** 失敗した取得を繰り返す回数（TanStack Query の既定と同じ） */
const MAX_RETRIES = 3

/**
 * サーバー状態のキャッシュ（管理画面の構成 第 4 節）。設定は TanStack Query の既定のまま。<br />
 * ただし 4xx は繰り返しても結果が変わらず、表示が遅れるだけなので繰り返さない。
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => !(error instanceof ApiError && error.isClientError) && failureCount < MAX_RETRIES,
    },
  },
})

/** 画面全体の Provider */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <RouterProvider router={router} />
        <Toaster position="top-center" richColors />
      </TooltipProvider>
    </QueryClientProvider>
  )
}
