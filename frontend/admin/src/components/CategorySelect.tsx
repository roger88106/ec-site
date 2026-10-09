import { $api } from '@/api/client'
import { OptionSelect } from '@/components/OptionSelect'
import { flattenTree } from '@/lib/categoryTree'

/** カテゴリを 1 つ選ぶ（絞り込み用）。null は「すべて」。階層は字下げで示す */
export function CategorySelect({
  id,
  value,
  onChange,
}: {
  id?: string
  value: string | null
  onChange: (value: string | null) => void
}) {
  const query = $api.useQuery('get', '/categories')
  const options = [
    { value: null, label: 'すべて' },
    ...flattenTree(query.data?.items ?? []).map((category) => ({
      value: category.id,
      label: `${'　'.repeat(category.depth - 1)}${category.name}`,
    })),
  ]
  return <OptionSelect className="w-48" id={id} onChange={onChange} options={options} value={value} />
}
