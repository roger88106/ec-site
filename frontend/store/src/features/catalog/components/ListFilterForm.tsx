import type { FlatCategory } from '@ec-site/shared/lib/categoryTree'
import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Label } from '@ec-site/shared/ui/label'
import { useTranslations } from 'next-intl'
import { parameters } from '@/api/generated/constraints'
import { productSortValues } from '@/api/generated/store'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { type ListQuery, sortLabel } from '@/features/catalog/listQuery'

/**
 * 商品一覧の絞り込みと並び順。フォームの GET で URL のクエリに送る（1 ページ目に戻る）。<br />
 * 画面の操作だけで完結するので、クライアントコンポーネントにしない。
 */
export function ListFilterForm({ query, categories }: { query: ListQuery; categories: FlatCategory[] }) {
  const t = useTranslations('catalog.filter')
  const tCommon = useTranslations('common')
  return (
    <form action="/products" className="flex flex-wrap items-end gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor="filter-category">{t('category') /* カテゴリ */}</Label>
        <NativeSelect defaultValue={query.categoryId ?? ''} id="filter-category" name="categoryId">
          <NativeSelectOption value="">{tCommon('all') /* すべて */}</NativeSelectOption>
          {categories.map((category) => (
            <NativeSelectOption key={category.id} value={category.id}>
              {/* 階層を全角の空白で字下げする */}
              {tCommon('categoryIndent').repeat(category.depth - 1) /* 階層の深さだけ字下げ */}
              {category.name}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="filter-min-price">{t('price') /* 価格 */}</Label>
        <div className="flex items-center gap-2">
          <Input
            aria-label={t('min') /* 下限 */}
            className="w-28"
            defaultValue={query.minPrice ?? ''}
            id="filter-min-price"
            min={parameters.listProducts.minPrice.minimum}
            name="minPrice"
            placeholder={t('min') /* 下限 */}
            type="number"
          />
          <span className="text-muted-foreground">{t('rangeSeparator') /* 範囲 */}</span>
          <Input
            aria-label={t('max') /* 上限 */}
            className="w-28"
            defaultValue={query.maxPrice ?? ''}
            min={parameters.listProducts.maxPrice.minimum}
            name="maxPrice"
            placeholder={t('max') /* 上限 */}
            type="number"
          />
          <span className="text-muted-foreground text-sm">{t('yen') /* 円 */}</span>
        </div>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="filter-sort">{t('sort') /* 並び順 */}</Label>
        <NativeSelect defaultValue={query.sort} id="filter-sort" name="sort">
          {productSortValues.map((sort) => (
            <NativeSelectOption key={sort} value={sort}>
              {sortLabel(sort)}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <Button type="submit">{t('submit') /* 絞り込む */}</Button>
    </form>
  )
}
