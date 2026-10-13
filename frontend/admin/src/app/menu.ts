import { BoxesIcon, FolderTreeIcon, type LucideIcon, PercentIcon, ReceiptTextIcon } from 'lucide-react'
import { paths } from '@/app/paths'

/** 左のメニュー。各機能の入口（一覧）だけを並べる（管理画面の構成 第 3 節）。表示名は文言の app.menu のキー */
export const menu = [
  { to: paths.products(), labelKey: 'products', icon: BoxesIcon }, // 商品
  { to: paths.categories(), labelKey: 'categories', icon: FolderTreeIcon }, // カテゴリ
  { to: paths.taxCategories(), labelKey: 'taxCategories', icon: PercentIcon }, // 税区分
  { to: paths.orders(), labelKey: 'orders', icon: ReceiptTextIcon }, // 注文
] as const satisfies { to: string; labelKey: string; icon: LucideIcon }[]
