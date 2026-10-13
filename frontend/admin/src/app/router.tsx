import { Navigate, type RouteObject } from 'react-router'
import { Layout } from '@/app/Layout'
import { NotFoundPage } from '@/app/NotFoundPage'
import { paths, routePatterns } from '@/app/paths'
import { CategoryPage } from '@/features/category/pages/CategoryPage'
import { StockPage } from '@/features/inventory/pages/StockPage'
import { OrderDetailPage } from '@/features/order/pages/OrderDetailPage'
import { OrderListPage } from '@/features/order/pages/OrderListPage'
import { ProductCreatePage } from '@/features/product/pages/ProductCreatePage'
import { ProductEditPage } from '@/features/product/pages/ProductEditPage'
import { ProductListPage } from '@/features/product/pages/ProductListPage'
import { TaxCategoryListPage } from '@/features/tax/pages/TaxCategoryListPage'
import { TaxRatesPage } from '@/features/tax/pages/TaxRatesPage'

/** 画面の URL（管理画面の構成 第 3 節） */
export const routes: RouteObject[] = [
  {
    path: routePatterns.top,
    element: <Layout />,
    children: [
      { index: true, element: <Navigate replace to={paths.products()} /> },
      { path: routePatterns.products, element: <ProductListPage /> },
      { path: routePatterns.productNew, element: <ProductCreatePage /> },
      { path: routePatterns.product, element: <ProductEditPage /> },
      { path: routePatterns.stock, element: <StockPage /> },
      { path: routePatterns.categories, element: <CategoryPage /> },
      { path: routePatterns.taxCategories, element: <TaxCategoryListPage /> },
      { path: routePatterns.taxCategory, element: <TaxRatesPage /> },
      { path: routePatterns.orders, element: <OrderListPage /> },
      { path: routePatterns.order, element: <OrderDetailPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
