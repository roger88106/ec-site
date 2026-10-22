import Link from 'next/link'
import type { CategoryNode } from '@/api/types'
import { categoryHref } from '@/features/catalog/listQuery'

/** カテゴリの一覧。最上位と、その子を並べ、それぞれ絞り込んだ商品一覧へリンクする */
export function CategoryList({ categories }: { categories: CategoryNode[] }) {
  if (categories.length === 0) {
    return null
  }
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {categories.map((category) => (
        <li className="rounded-lg border p-4" key={category.id}>
          <Link className="font-medium hover:underline" href={categoryHref(category.id)}>
            {category.name}
          </Link>
          {category.children.length > 0 && (
            <ul className="mt-2 space-y-1 text-muted-foreground text-sm">
              {category.children.map((child) => (
                <li key={child.id}>
                  <Link className="hover:text-foreground hover:underline" href={categoryHref(child.id)}>
                    {child.name}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ul>
  )
}
