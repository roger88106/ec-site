import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { toast } from 'sonner'
import { afterAll, afterEach, vi } from 'vitest'
import { server } from '@/test/server'

// テストのファイルを読み込む前に始める。API のクライアント（openapi-fetch）は、作ったときの fetch を持ち続けるので、
// MSW が fetch を差し替えた後に作らせる必要がある。
// ハンドラーのない API が呼ばれたら、その要求を失敗させ、テストの終わりにテストを失敗させる（要求の失敗だけでは、
// 画面がエラーを出すだけで、テストが通ってしまうことがあるため）
const unhandledRequests: string[] = []
server.listen({
  onUnhandledRequest: (request, print) => {
    unhandledRequests.push(`${request.method} ${request.url}`)
    print.error()
  },
})

afterEach(() => {
  // toast は画面の外（sonner）に残るので、次のテストに出ないよう消す
  toast.dismiss()
  cleanup()
  // vi.spyOn で差し替えたものを戻す（各テストのファイルで繰り返さない）
  vi.restoreAllMocks()
  server.resetHandlers()
  const requests = unhandledRequests.splice(0)
  if (requests.length > 0) {
    throw new Error(`ハンドラーのない API が呼ばれた：${requests.join('、')}`)
  }
})

afterAll(() => {
  server.close()
})

// jsdom にない Blob の URL の関数を補う（ダウンロードの記録 download.ts が vi.spyOn で差し替えるため、関数が必要）
URL.createObjectURL ??= () => 'blob:test'
URL.revokeObjectURL ??= () => {}

// jsdom にないブラウザの機能を、画面の部品（Sidebar、base-ui）が使う分だけ補う
window.matchMedia ??= (query: string) =>
  ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }) as MediaQueryList
