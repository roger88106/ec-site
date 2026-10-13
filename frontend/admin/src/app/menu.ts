import { BoxesIcon, FolderTreeIcon, type LucideIcon, PercentIcon, ReceiptTextIcon } from 'lucide-react'
import { paths } from '@/app/paths'

/** 左のメニュー。各機能の入口（一覧）だけを並べる（管理画面の構成 第 3 節） */
export const menu: { to: string; label: string; icon: LucideIcon }[] = [
  { to: paths.products(), label: '商品', icon: BoxesIcon },
  { to: paths.categories(), label: 'カテゴリ', icon: FolderTreeIcon },
  { to: paths.taxCategories(), label: '税区分', icon: PercentIcon },
  { to: paths.orders(), label: '注文', icon: ReceiptTextIcon },
]
