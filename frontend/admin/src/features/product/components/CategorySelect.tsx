import { categoryOptions, flattenTree } from '@ec-site/shared/lib/categoryTree'
import { $api } from '@/api/client'
import { OptionSelect } from '@/components/OptionSelect'

/**
 * カテゴリの選択（絞り込み用）
 *
 * 階層は字下げで示す。
 */
export function CategorySelect({
  id,
  value,
  onChange,
}: {
  id?: string
  /** 選んだカテゴリの ID。null は「すべて」 */
  value: string | null
  onChange: (value: string | null) => void
}) {
  const query = $api.useQuery('get', '/categories')
  const options = categoryOptions(flattenTree(query.data?.items ?? []))
  return <OptionSelect className="w-48" id={id} onChange={onChange} options={options} value={value} />
}
