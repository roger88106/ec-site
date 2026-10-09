import { BoxesIcon, FolderTreeIcon, type LucideIcon, PercentIcon } from 'lucide-react'

/** 左のメニュー。各機能の入口（一覧）だけを並べる（管理画面の構成 第 3 節） */
export const menu: { to: string; label: string; icon: LucideIcon }[] = [
  { to: '/products', label: '商品', icon: BoxesIcon },
  { to: '/categories', label: 'カテゴリ', icon: FolderTreeIcon },
  { to: '/tax-categories', label: '税区分', icon: PercentIcon },
]
