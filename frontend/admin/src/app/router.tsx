import { createBrowserRouter, Navigate } from 'react-router'
import { Layout } from '@/app/Layout'
import { NotFoundPage } from '@/app/NotFoundPage'
import { CategoryPage } from '@/features/category/pages/CategoryPage'
import { TaxCategoryListPage } from '@/features/tax/pages/TaxCategoryListPage'
import { TaxRatesPage } from '@/features/tax/pages/TaxRatesPage'

/** 画面の URL（管理画面の構成 第 3 節） */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate replace to="/products" /> },
      { path: 'categories', element: <CategoryPage /> },
      { path: 'tax-categories', element: <TaxCategoryListPage /> },
      { path: 'tax-categories/:taxCategoryId', element: <TaxRatesPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
