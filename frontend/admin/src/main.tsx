import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { createBrowserRouter } from 'react-router'
import { App } from '@/app/App'
import { createQueryClient } from '@/app/queryClient'
import { routes } from '@/app/router'
import { AuthGate } from '@/auth/AuthGate'
import { oidcSettings } from '@/auth/oidc'
import { I18nProvider } from '@/i18n/I18nProvider'
import { returnPath } from '@/lib/login'
import './index.css'

/**
 * ログインした後に作る画面。ルーターは、IdP から戻った URL（code と state）を、移る前の画面の URL に
 * 書き換えてから作る（ルーターは作ったときの URL を読むため）。
 */
function AuthenticatedApp() {
  const [router] = useState(() => createBrowserRouter(routes))
  const [queryClient] = useState(createQueryClient)
  return <App queryClient={queryClient} router={router} />
}

// biome-ignore lint/style/noNonNullAssertion: index.html に必ずある要素
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <AuthProvider
        {...oidcSettings}
        onSigninCallback={(user) => {
          // IdP から戻った URL の code と state を消し、移る前の画面に戻す（管理画面の構成 第 10 節）
          window.history.replaceState({}, document.title, returnPath(user?.state))
        }}
      >
        <AuthGate>
          <AuthenticatedApp />
        </AuthGate>
      </AuthProvider>
    </I18nProvider>
  </StrictMode>,
)
