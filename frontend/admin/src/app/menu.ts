import {
  BoxesIcon,
  ChartBarIcon,
  ChartLineIcon,
  FolderTreeIcon,
  type LucideIcon,
  MapPinIcon,
  PercentIcon,
  ReceiptTextIcon,
} from 'lucide-react'
import { paths } from '@/app/paths'
import type { Permission } from '@/auth/permissions'

/** 左のメニュー。各機能の入口（一覧）だけを並べる（管理画面の構成 第 3 節）。表示名は文言の app.menu のキー */
export const menu = [
  { to: paths.products(), labelKey: 'products', icon: BoxesIcon }, // 商品
  { to: paths.categories(), labelKey: 'categories', icon: FolderTreeIcon }, // カテゴリ
  { to: paths.taxCategories(), labelKey: 'taxCategories', icon: PercentIcon }, // 税区分
  { to: paths.orders(), labelKey: 'orders', icon: ReceiptTextIcon }, // 注文
] as const satisfies { to: string; labelKey: string; icon: LucideIcon }[]

/** 左のメニューの「レポート」の下の画面。権限のない画面は出さない（権限がなければ「レポート」ごと出さない） */
export const reportMenu = [
  { to: paths.salesTrend(), labelKey: 'salesTrend', icon: ChartLineIcon, permission: 'report-sales-trend:read' }, // 売上の推移
  { to: paths.variantSales(), labelKey: 'variantSales', icon: ChartBarIcon, permission: 'report-variant-sales:read' }, // バリアントの売れ行き
  {
    to: paths.customerBreakdown(),
    labelKey: 'customerBreakdown',
    icon: MapPinIcon,
    permission: 'report-customer:read',
  }, // 地域・年齢層・性別
] as const satisfies { to: string; labelKey: string; icon: LucideIcon; permission: Permission }[]
