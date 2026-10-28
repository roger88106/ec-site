import { type QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'

/** テストでルーターと QueryClient を差し替えられるよう、外から渡す */
type Props = {
  /** 画面のルーター。ブラウザーでは createBrowserRouter、結合テストでは createMemoryRouter で作る */
  router: ReturnType<typeof createBrowserRouter>
  queryClient: QueryClient
}

/** 画面全体の Provider */
export function App({ router, queryClient }: Props) {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <RouterProvider router={router} />
        <Toaster position="top-center" richColors />
      </TooltipProvider>
    </QueryClientProvider>
  )
}
