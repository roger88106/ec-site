import { createBrowserRouter, Navigate } from 'react-router'
import { Layout } from '@/app/Layout'
import { NotFoundPage } from '@/app/NotFoundPage'

/** 画面の URL（管理画面の構成 第 3 節） */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/products" replace /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
