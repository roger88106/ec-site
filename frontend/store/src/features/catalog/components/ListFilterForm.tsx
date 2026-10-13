import type { FlatCategory } from '@ec-site/shared/lib/categoryTree'
import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Label } from '@ec-site/shared/ui/label'
import { parameters } from '@/api/generated/constraints'
import { productSortValues } from '@/api/generated/store'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { type ListQuery, sortLabels } from '@/features/catalog/listQuery'

/**
 * 商品一覧の絞り込みと並び順（AC-52〜54）。フォームの GET で URL のクエリに送る（1 ページ目に戻る）。<br />
 * 画面の操作だけで完結するので、クライアントコンポーネントにしない。
 */
export function ListFilterForm({ query, categories }: { query: ListQuery; categories: FlatCategory[] }) {
  return (
    <form action="/products" className="flex flex-wrap items-end gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor="filter-category">カテゴリ</Label>
        <NativeSelect defaultValue={query.categoryId ?? ''} id="filter-category" name="categoryId">
          <NativeSelectOption value="">すべて</NativeSelectOption>
          {categories.map((category) => (
            <NativeSelectOption key={category.id} value={category.id}>
              {/* 階層を全角の空白で字下げする */}
              {'　'.repeat(category.depth - 1)}
              {category.name}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="filter-min-price">価格（税込）</Label>
        <div className="flex items-center gap-2">
          <Input
            aria-label="下限"
            className="w-28"
            defaultValue={query.minPrice ?? ''}
            id="filter-min-price"
            min={parameters.listProducts.minPrice.minimum}
            name="minPrice"
            placeholder="下限"
            type="number"
          />
          <span className="text-muted-foreground">〜</span>
          <Input
            aria-label="上限"
            className="w-28"
            defaultValue={query.maxPrice ?? ''}
            min={parameters.listProducts.maxPrice.minimum}
            name="maxPrice"
            placeholder="上限"
            type="number"
          />
          <span className="text-muted-foreground text-sm">円</span>
        </div>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="filter-sort">並び順</Label>
        <NativeSelect defaultValue={query.sort} id="filter-sort" name="sort">
          {productSortValues.map((sort) => (
            <NativeSelectOption key={sort} value={sort}>
              {sortLabels[sort]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <Button type="submit">絞り込む</Button>
    </form>
  )
}
