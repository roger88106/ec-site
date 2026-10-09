import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import { App } from '@/app/App'
import { createQueryClient } from '@/app/queryClient'
import { routes } from '@/app/router'
import './index.css'

// biome-ignore lint/style/noNonNullAssertion: index.html に必ずある要素
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App queryClient={createQueryClient()} router={createBrowserRouter(routes)} />
  </StrictMode>,
)
