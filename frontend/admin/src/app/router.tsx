import { Navigate, type RouteObject } from 'react-router'
import { Layout } from '@/app/Layout'
import { NotFoundPage } from '@/app/NotFoundPage'
import { CategoryPage } from '@/features/category/pages/CategoryPage'
import { StockPage } from '@/features/inventory/pages/StockPage'
import { OrderListPage } from '@/features/order/pages/OrderListPage'
import { ProductCreatePage } from '@/features/product/pages/ProductCreatePage'
import { ProductEditPage } from '@/features/product/pages/ProductEditPage'
import { ProductListPage } from '@/features/product/pages/ProductListPage'
import { TaxCategoryListPage } from '@/features/tax/pages/TaxCategoryListPage'
import { TaxRatesPage } from '@/features/tax/pages/TaxRatesPage'

/** 画面の URL（管理画面の構成 第 3 節） */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate replace to="/products" /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'products/new', element: <ProductCreatePage /> },
      { path: 'products/:productId', element: <ProductEditPage /> },
      { path: 'products/:productId/variants/:variantId/stock', element: <StockPage /> },
      { path: 'categories', element: <CategoryPage /> },
      { path: 'tax-categories', element: <TaxCategoryListPage /> },
      { path: 'tax-categories/:taxCategoryId', element: <TaxRatesPage /> },
      { path: 'orders', element: <OrderListPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
