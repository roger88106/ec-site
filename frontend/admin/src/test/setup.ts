import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { toast } from 'sonner'
import { afterAll, afterEach } from 'vitest'
import { server } from '@/test/server'

// テストのファイルを読み込む前に始める。API のクライアント（openapi-fetch）は、作ったときの fetch を持ち続けるので、
// MSW が fetch を差し替えた後に作らせる必要がある
server.listen({ onUnhandledRequest: 'error' })

afterEach(() => {
  // toast は画面の外（sonner）に残るので、次のテストに出ないよう消す
  toast.dismiss()
  cleanup()
  server.resetHandlers()
})

afterAll(() => {
  server.close()
})

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
